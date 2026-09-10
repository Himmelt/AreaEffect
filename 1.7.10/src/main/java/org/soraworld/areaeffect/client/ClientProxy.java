package org.soraworld.areaeffect.client;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;
import org.soraworld.areaeffect.client.effect.EffectRenderers;
import org.soraworld.areaeffect.client.effect.EffectRenderer;
import org.soraworld.areaeffect.client.gui.GuiAreas;
import org.soraworld.areaeffect.client.handler.AreaClientHandler;
import org.soraworld.areaeffect.client.handler.ClientSelectionHandler;
import org.soraworld.areaeffect.client.handler.LightmapHook;
import org.soraworld.areaeffect.client.handler.SelectionRenderHandler;
import org.soraworld.areaeffect.common.CommonProxy;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.effect.LightnessEffect;
import org.soraworld.areaeffect.common.network.Area;
import org.soraworld.areaeffect.common.network.MessageAreaDelete;
import org.soraworld.areaeffect.common.network.MessageAreaUpdate;
import org.soraworld.areaeffect.common.network.MessageClickAir;
import org.soraworld.areaeffect.common.network.MessageConflictAreas;
import org.soraworld.areaeffect.common.network.MessageDeleteRequest;
import org.soraworld.areaeffect.common.network.MessageListReply;
import org.soraworld.areaeffect.common.network.MessageListRequest;
import org.soraworld.areaeffect.common.network.MessageSelection;
import org.soraworld.areaeffect.common.network.MessageSetProps;
import org.soraworld.areaeffect.common.network.MessageTpRequest;
import org.soraworld.areaeffect.common.network.MessageSelectShape;
import org.soraworld.areaeffect.common.network.MessageToolSync;
import org.soraworld.areaeffect.common.network.PacketChannel;
import org.soraworld.areaeffect.common.shape.Selection;
import org.soraworld.areaeffect.common.util.Vec3i;
import org.soraworld.areaeffect.common.util.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClientProxy extends CommonProxy {

    public static final KeyBinding KEY_LIST = new KeyBinding("key.areaeffect.list", Keyboard.KEY_J, "key.categories.areaeffect");
    public static final KeyBinding KEY_SEL_RENDER = new KeyBinding("key.areaeffect.selrender", Keyboard.KEY_K, "key.categories.areaeffect");

    private boolean inArea = false;
    private int lastAreaId = -1;
    private float lastDuration = 1.0F;

    private Selection selSelection = null;
    private boolean showSelection = true;
    /** 各维度中已开启线框显示的区域 id 集合（客户端本地设置）。 */
    private final Map<Integer, Set<Integer>> visibleAreas = new ConcurrentHashMap<>();

    /** 编辑预览亮度/时长覆盖：dim → id → {lightness, duration}。
     *  独立于共享 Area，预览值不进入 areas，避免被落盘。 */
    private final Map<Integer, Map<Integer, float[]>> previewLd = new ConcurrentHashMap<>();
    /** 编辑预览备注覆盖：dim → id → String（同样不入共享对象）。 */
    private final Map<Integer, Map<Integer, String>> previewRemarks = new ConcurrentHashMap<>();

    /** 区域查找缓存：玩家连续帧停在同区域时 O(1) 命中；跨维度/离开导致 contains 失败即重定位。
     *  区域被更新/删除时由 {@link #invalidateAreaCache} 显式失效（缓存对象 shape 不可变，
     *  contains 恒真不会自动失效）。失效由 netty 线程触发、渲染线程读取，故须 volatile。 */
    private volatile int cachedDim = Integer.MIN_VALUE;
    private volatile Area cachedArea = null;

    /** 选区形状轮切 overlay 状态：当前提示的形状与最近一次轮切时间（毫秒）。 */
    private String overlayShape = null;
    private long overlayLastAction = 0L;

    private EffectRenderers renderers = new EffectRenderers();

    /** netty 线程投递、需在客户端主线程执行的逻辑（GUI 必须回主线程操作）。 */
    private final ConcurrentLinkedQueue<Runnable> clientTasks = new ConcurrentLinkedQueue<>();

    private final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public void onPreInit(FMLPreInitializationEvent event) {
        super.onPreInit(event);
        AreaClientHandler handler = new AreaClientHandler(this);
        FMLCommonHandler.instance().bus().register(handler);
        MinecraftForge.EVENT_BUS.register(new SelectionRenderHandler(this));
        MinecraftForge.EVENT_BUS.register(new ClientSelectionHandler(this));
        ClientRegistry.registerKeyBinding(KEY_LIST);
        ClientRegistry.registerKeyBinding(KEY_SEL_RENDER);
    }

    @Override
    public void onInit(FMLInitializationEvent event) {
        super.onInit(event);
        // 客户端方向消息的处理逻辑绑定
        PacketChannel.bindClient(MessageAreaUpdate.class, this::handleUpdate);
        PacketChannel.bindClient(MessageAreaDelete.class, this::handleDelete);
        PacketChannel.bindClient(MessageSelection.class, this::handleSelection);
        PacketChannel.bindClient(MessageListReply.class, this::handleListReply);
        PacketChannel.bindClient(MessageToolSync.class, this::handleToolSync);
        PacketChannel.bindClient(MessageConflictAreas.class, this::handleConflictAreas);
    }

    /** 创建冲突：自动开启冲突区域的线框显示。 */
    public void handleConflictAreas(MessageConflictAreas packet) {
        runOnClientThread(() -> {
            Set<Integer> ids = visibleAreas.computeIfAbsent(packet.dim, d -> ConcurrentHashMap.newKeySet());
            ids.addAll(packet.ids);
        });
    }

    /** 同步服务端设置的选区工具（MP 客户端不读 config）。netty 线程回调，写 tool 须回主线程。 */
    public void handleToolSync(MessageToolSync packet) {
        runOnClientThread(() -> {
            try {
                Object object = Item.itemRegistry.getObject(packet.toolName);
                if (object instanceof Item) {
                    tool = (Item) object;
                } else {
                    tool = Items.wooden_axe;
                }
            } catch (Throwable ignored) {
                tool = Items.wooden_axe;
            }
        });
    }

    public void sendListRequest() {
        PacketChannel.sendToServer(new MessageListRequest());
    }

    public void sendSetProps(int dim, int id, String remark, List<AreaEffect> effects) {
        PacketChannel.sendToServer(new MessageSetProps(dim, id, remark, effects));
    }

    public void sendDeleteRequest(int dim, int id) {
        PacketChannel.sendToServer(new MessageDeleteRequest(dim, id));
    }

    public void sendTpRequest(int id) {
        PacketChannel.sendToServer(new MessageTpRequest(id));
    }

    /**
     * 连接建立时的会话初始化：连外部服务器时，本地区域表只是镜像，先清掉上一个会话的残留 ——
     * 否则等登录全量同步按 id 覆盖之后，只存在于旧会话的 id 会以幽灵区域的形式留下来。
     *
     * <p>{@code local} 为真表示连的是本机集成服（单机）：此时 {@link #areas} 与集成服务端是
     * <b>同一个实例</b>（见 {@code CommonProxy#areas}），归服务端所有，绝不能在这里清。
     * 单机退出世界后该表保持不动，下次加载世界由 {@code AreaStore#load} 负责 clear + 重读。
     */
    public void onServerConnected(boolean local) {
        if (!local) {
            areas.clear();
            cachedArea = null;
            cachedDim = Integer.MIN_VALUE;
        }
    }

    public void handleUpdate(MessageAreaUpdate packet) {
        if (packet.data == null) {
            return; // 反序列化失败（如未知形状），丢弃避免注入 null
        }
        // 与 handleSelection 一致：改镜像与失效查找缓存都回客户端主线程执行，
        // 免得在 netty 线程与渲染线程争抢 cachedDim / cachedArea 这对非原子字段
        runOnClientThread(() -> {
            areas.put(packet.dim, packet.id, packet.data);
            // 更新会替换 Area 对象（shape/effects 均可能变化），缓存持有旧引用须失效
            invalidateAreaCache(packet.dim, packet.id);
        });
    }

    public void handleDelete(MessageAreaDelete packet) {
        // 同上：镜像写入、缓存失效、界面刷新一并回客户端主线程执行
        runOnClientThread(() -> {
            areas.remove(packet.dim, packet.id);
            // 缓存持有已删对象的强引用且 shape 不可变，contains 恒真不会自动失效，须显式清除
            invalidateAreaCache(packet.dim, packet.id);
            // 已开启线框的区域没了就不再画，顺势摘掉本地显示集合，免得每帧空转
            Set<Integer> ids = visibleAreas.get(packet.dim);
            if (ids != null) {
                ids.remove(packet.id);
            }
            // 广播统一刷新：更新已打开的管理界面
            if (mc.currentScreen instanceof GuiAreas) {
                ((GuiAreas) mc.currentScreen).refreshFromProxy();
            }
        });
    }

    /** 区域被更新/删除时失效查找缓存（下一帧全遍历重建，单帧开销可忽略）。 */
    private void invalidateAreaCache(int dim, int id) {
        if (cachedArea != null && cachedArea.id == id && cachedDim == dim) {
            cachedArea = null;
            cachedDim = Integer.MIN_VALUE;
        }
    }

    public void handleSelection(MessageSelection packet) {
        // 选区镜像 netty 线程写、渲染线程读，需回客户端主线程统一更新，避免数据竞争
        runOnClientThread(() -> {
            selSelection = new Selection();
            selSelection.shapeType = packet.shapeType;
            for (Vec3i anchor : packet.anchors) {
                selSelection.anchors.add(anchor);
            }
        });
    }

    /**
     * 收到面板授权：打开或刷新区域管理界面。
     *
     * <p>消息本身<b>不带数据</b>（见 {@link MessageListReply}）：界面直接读本地镜像，
     * 而且是跨维度的全量（左栏列所有存在区域的维度），所以这里不需要、也不该使用任何 payload。
     */
    public void handleListReply(MessageListReply packet) {
        // netty 线程回调：GUI 操作必须回客户端主线程
        runOnClientThread(() -> {
            // 已打开界面则刷新，否则打开主界面（读取全量本地数据，跨维度）
            if (mc.currentScreen instanceof GuiAreas) {
                ((GuiAreas) mc.currentScreen).refreshFromProxy();
            } else {
                mc.displayGuiScreen(new GuiAreas(this));
            }
        });
    }

    /** netty 线程投递需在客户端主线程执行的逻辑（参考 CNpcUI：回调只做线程安全操作，其余进队）。 */
    private void runOnClientThread(Runnable task) {
        clientTasks.add(task);
    }

    /** 在客户端主线程（PlayerTick 驱动）排空待执行任务。 */
    private void drainClientTasks() {
        Runnable task;
        while ((task = clientTasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable ignored) {
            }
        }
    }

    /** 当前本地选区镜像（netty 线程写，渲染线程读，与旧 selPos1/2 模式一致）。 */
    public Selection getLocalSelection() {
        return selSelection;
    }

    /**
     * 客户端发送切换选区形状请求，并<b>本地乐观更新</b>选区镜像。
     * <p>轮切需要读"当前形状"（见 {@code ClientSelectionHandler#currentShape}），若只等服务端回发的
     * {@link MessageSelection}，高延迟下连按两次会读到同一旧值、算出同一个"下一个形状"，
     * 表现为按键无反应。这里按 {@code Selection.reset} 的语义先行更新（换形状即清空锚点），
     * 服务端回声到达后会以权威值整体覆盖，故不会积累偏差。
     */
    public void sendSelectShape(String type) {
        PacketChannel.sendToServer(new MessageSelectShape(type));
        Selection sel = selSelection;
        if (sel == null) {
            selSelection = sel = new Selection();
        }
        sel.reset(type);
    }

    /** 客户端发送撤回/右键空气事件（服务端与右键方块同入口处理，作为多边形撤回）。 */
    public void sendClickAir() {
        PacketChannel.sendToServer(new MessageClickAir());
    }

    public boolean isShowSelection() {
        return showSelection;
    }

    /** 触发选区形状轮切 overlay 显示（重置 1.5 秒停留计时）。 */
    public void showShapeOverlay(String type) {
        overlayShape = type;
        overlayLastAction = Minecraft.getSystemTime();
    }

    /** overlay 当前展示的形状类型；无则返回 null。 */
    public String getOverlayShape() {
        return overlayShape;
    }

    /** 清空 overlay 状态（淡出结束调用，防止残留状态被再次绘制）。 */
    public void clearShapeOverlay() {
        overlayShape = null;
    }

    /** overlay 最近一次轮切触发时间（Minecraft 毫秒时间）。 */
    public long getOverlayLastAction() {
        return overlayLastAction;
    }

    /** 切换选区线框显示（客户端本地设置），并本地提示。 */
    public void toggleSelection() {
        showSelection = !showSelection;
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentTranslation(showSelection ? "chat.selection.on" : "chat.selection.off"));
        }
    }

    /** 指定区域的线框显示是否开启（客户端本地设置）。 */
    public boolean isAreaVisible(int dim, int id) {
        Set<Integer> ids = visibleAreas.get(dim);
        return ids != null && ids.contains(id);
    }

    /** 切换指定区域的线框显示（客户端本地设置），并本地提示。 */
    public void toggleAreaVisible(int dim, int id) {
        Set<Integer> ids = visibleAreas.computeIfAbsent(dim, d -> ConcurrentHashMap.newKeySet());
        if (!ids.remove(id)) {
            ids.add(id);
        }
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentTranslation(
                    isAreaVisible(dim, id) ? "chat.area.show" : "chat.area.hide", id));
        }
    }

    /** 当前维度中已开启线框显示的区域列表。 */
    public List<Area> getVisibleAreas(int dim) {
        List<Area> result = new ArrayList<>();
        Set<Integer> ids = visibleAreas.get(dim);
        if (ids != null) {
            Map<Integer, Area> dimAreas = areas.inDim(dim);
            for (Integer id : ids) {
                Area area = dimAreas.get(id);
                if (area != null) {
                    result.add(area);
                }
            }
        }
        return result;
    }

    /**
     * 编辑界面实时预览：把亮度/时长写入独立覆盖表（不修改共享 Area）。
     * 渲染器通过 {@link #effectiveArea} 读取覆盖，下一帧即生效；
     * 未保存退出/切换时用 {@link #clearPreview} 移除覆盖即可还原原值。
     */
    public void previewAreaProps(int dim, int id, float lightness, float duration) {
        previewLd.computeIfAbsent(dim, d -> new ConcurrentHashMap<>()).put(id, new float[]{lightness, duration});
    }

    /** 本地立即写回备注到覆盖表（列表即时显示；服务端广播到达后共享数据保持一致）。 */
    public void previewAreaRemark(int dim, int id, String remark) {
        previewRemarks.computeIfAbsent(dim, d -> new ConcurrentHashMap<>()).put(id, remark);
    }

    /** 返回该区域当前应呈现的实例：有预览覆盖则新建临时对象（复用原 shape），否则原对象。
     *  共享 Area 始终保持存档原值，预览仅存在于渲染视图中。 */
    private Area effectiveArea(int dim, Area area) {
        if (area == null) {
            return null;
        }
        Map<Integer, float[]> m = previewLd.get(dim);
        if (m == null) {
            return area;
        }
        float[] ld = m.get(area.id);
        if (ld == null) {
            return area;
        }
        // 覆盖值与共享一致时直接复用共享对象，避免每帧构造临时对象
        if (ld[0] == area.getLightness() && ld[1] == area.getDuration()) {
            return area;
        }
        Area tmp = new Area(area.shape(), ld[0], ld[1]);
        tmp.id = area.id;
        // 预览只覆盖亮度/时长：其它类型的效果原样带过来，别被 new Area(...) 里那条单例
        // 亮度效果挤掉（效果列表的设计是"一个区域可挂多种效果"）
        List<AreaEffect> keep = new ArrayList<>();
        for (AreaEffect effect : area.getEffects()) {
            if (!(effect instanceof LightnessEffect)) {
                keep.add(effect);
            }
        }
        if (!keep.isEmpty()) {
            keep.add(new LightnessEffect(ld[0], ld[1]));
            tmp.setEffects(keep);
        }
        return tmp;
    }

    /** 返回该区域当前应呈现的备注：预览覆盖优先，否则区域原值。 */
    public String getEffectiveRemark(int dim, Area area) {
        if (area == null) {
            return "";
        }
        Map<Integer, String> r = previewRemarks.get(dim);
        if (r != null) {
            String override = r.get(area.id);
            if (override != null) {
                return override;
            }
        }
        return area.getRemark();
    }

    /** 清除指定区域的预览覆盖（未保存退出/切换/保存成功后调用），恢复以共享原值为准。 */
    public void clearPreview(int dim, int id) {
        Map<Integer, float[]> m = previewLd.get(dim);
        if (m != null) {
            m.remove(id);
        }
        Map<Integer, String> r = previewRemarks.get(dim);
        if (r != null) {
            r.remove(id);
        }
    }

    /** 客户端本地区域列表快照（按 id 升序，用于返回列表界面，无需再走服务端请求）。 */
    public List<Area> getAreasLocal(int dim) {
        return areas.sortedIn(dim);
    }

    /** 所有存在区域的维度列表（升序）。 */
    public List<Integer> getDimsLocal() {
        return areas.dims();
    }

    /** 指示玩家是否正站在某区域内并返回该区域（带缓存）。
     *  命中连续帧所在区域时单次 contains，否则回退父类 findAreaAt 全遍历并重建缓存。 */
    private Area findAreaCached(EntityPlayer player) {
        if (cachedArea != null && cachedDim == player.dimension && cachedArea.contains(new Vec3d(player))) {
            return cachedArea;
        }
        cachedDim = player.dimension;
        cachedArea = findAreaAt(player);
        return cachedArea;
    }

    /** 区域内指定类型的效果实例；该类型效果不在该区域时返回 null。 */
    private static AreaEffect effectOf(Area area, String typeId) {
        for (AreaEffect effect : area.getEffects()) {
            if (typeId.equals(effect.typeId())) {
                return effect;
            }
        }
        return null;
    }

    /**
     * 每帧客户端更新：判定当前所在区域，再把该区域挂载的各效果分发给对应的
     * 客户端运行时驱动过渡（渲染器内部按真实时间插值）。区域外则让所有运行时回退到 0。
     */
    public void updateClientLight(EntityPlayer player) {
        LightmapHook.tryInstall(mc);
        drainClientTasks();
        Area area = findAreaCached(player);
        if (area != null) {
            boolean areaChanged = !inArea || area.id != lastAreaId;
            inArea = true;
            lastAreaId = area.id;
            lastDuration = area.getDuration();
            // 读取预览覆盖（若有），共享 Area 不被预览污染
            Area renderArea = effectiveArea(player.dimension, area);
            // 每帧驱动全部已注册渲染器，区域内没有该类效果时传 null（等同"该效果不在生效中"）。
            // 【不要】改成只遍历"区域内存在的效果"：那样一个没有亮度效果的区域（异常存档或被
            // 写坏的数据）会让所有渲染器都不被调用，LightmapHook 保持上一次的偏移，
            // 玩家站进去后画面会停在上一区域的亮度上不动。
            for (String typeId : renderers.typeIds()) {
                EffectRenderer renderer = renderers.get(typeId);
                if (renderer != null) {
                    renderer.onFrame(areaChanged, effectOf(renderArea, typeId), lastDuration);
                }
            }
        } else {
            boolean areaChanged = inArea;
            inArea = false;
            lastAreaId = -1;
            for (EffectRenderer renderer : renderers.all()) {
                renderer.onFrame(areaChanged, null, lastDuration);
            }
        }
    }

    public void clientReset() {
        tool = Items.wooden_axe;
        inArea = false;
        lastAreaId = -1;
        lastDuration = 1.0F;
        // 【不要在这里清 areas】：单机下它与集成服务端是同一实例（见 CommonProxy#areas 的说明），
        // 而 Minecraft.loadWorld 会先断连接（触发本方法）、后停集成服并走 onServerStopping 的兜底
        // flushStore —— 若此刻存档恰好是脏的，就会把空表写回存档、清空全部区域。
        // 镜像的清理改在「连上外部服务器」时做（onServerConnected），单机则交给下一次 AreaStore#load。
        selections.clearAll();
        selSelection = null;
        overlayShape = null;
        overlayLastAction = 0L;
        visibleAreas.clear();
        previewLd.clear();
        previewRemarks.clear();
        cachedDim = Integer.MIN_VALUE;
        cachedArea = null;
        renderers = new EffectRenderers();
        clientTasks.clear();
        LightmapHook.setOffset(0.0D, 0.0D);
    }
}