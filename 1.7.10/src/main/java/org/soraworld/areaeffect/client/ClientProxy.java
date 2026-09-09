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
import java.util.Collections;
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
     *  独立于共享 Area，预览值不进入 lightAreas，避免被 save() 落盘。 */
    private final Map<Integer, Map<Integer, float[]>> previewLd = new ConcurrentHashMap<>();
    /** 编辑预览备注覆盖：dim → id → String（同样不入共享对象）。 */
    private final Map<Integer, Map<Integer, String>> previewRemarks = new ConcurrentHashMap<>();

    /** 区域查找缓存：玩家连续帧停在同区域时 O(1) 命中；跨维度/离开导致 contains 失败即重定位。
     *  区域增删无需显式失效——缓存下次 contains 判 false 会自动回退到全遍历。 */
    private int cachedDim = Integer.MIN_VALUE;
    private Area cachedArea = null;

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

    /** 同步服务端设置的选区工具（MP 客户端不读 config）。 */
    public void handleToolSync(MessageToolSync packet) {
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

    public void handleUpdate(MessageAreaUpdate packet) {
        if (packet.data == null) {
            return; // 反序列化失败（如未知形状），丢弃避免注入 null
        }
        lightAreas.computeIfAbsent(packet.dim, dim -> new ConcurrentHashMap<Integer, Area>()).put(packet.id, packet.data);
    }

    public void handleDelete(MessageAreaDelete packet) {
        Map<Integer, Area> areas = lightAreas.get(packet.dim);
        if (areas != null && !areas.isEmpty()) {
            areas.remove(packet.id);
        }
        // 广播统一刷新：回主线程更新已打开的管理界面
        runOnClientThread(() -> {
            if (mc.currentScreen instanceof GuiAreas) {
                ((GuiAreas) mc.currentScreen).refreshFromProxy();
            }
        });
    }

    public void handleSelection(MessageSelection packet) {
        // 选区镜像 netty 线程写、渲染线程读，需回客户端主线程统一更新，避免数据竞争
        runOnClientThread(() -> {
            selSelection = new Selection();
            selSelection.shapeType = packet.shapeType;
            for (Vec3i anchor : packet.anchors) {
                selSelection.anchors.add(anchor);
            }
            selSelection.closed = packet.closed;
            selSelection.heightPhase = packet.heightPhase;
        });
    }

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

    public Vec3i getSelPos1() {
        return selSelection != null && selSelection.anchors.size() > 0 ? selSelection.anchors.get(0) : null;
    }

    public Vec3i getSelPos2() {
        return selSelection != null && selSelection.anchors.size() > 1 ? selSelection.anchors.get(1) : null;
    }

    /** 当前本地选区镜像（netty 线程写，渲染线程读，与旧 selPos1/2 模式一致）。 */
    public Selection getLocalSelection() {
        return selSelection;
    }

    /** 客户端发送切换选区形状请求。 */
    public void sendSelectShape(String type) {
        PacketChannel.sendToServer(new MessageSelectShape(type, false));
    }

    /** 客户端发送闭合多边形请求。 */
    public void sendClosePolygon() {
        PacketChannel.sendToServer(new MessageSelectShape("", true));
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
            Map<Integer, Area> areas = lightAreas.get(dim);
            if (areas != null) {
                for (Integer id : ids) {
                    Area area = areas.get(id);
                    if (area != null) {
                        result.add(area);
                    }
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
        Map<Integer, Area> areas = lightAreas.get(dim);
        if (areas == null || areas.isEmpty()) {
            return new ArrayList<>();
        }
        List<Area> list = new ArrayList<>(areas.values());
        Collections.sort(list, (a, b) -> Integer.compare(a.id, b.id));
        return list;
    }

    /** 所有存在区域的维度列表（升序）。 */
    public List<Integer> getDimsLocal() {
        List<Integer> dims = new ArrayList<>(lightAreas.keySet());
        Collections.sort(dims);
        return dims;
    }

    /** 按 id 跨维度查找区域副本。 */
    public Area findAreaLocal(int dim, int id) {
        Map<Integer, Area> areas = lightAreas.get(dim);
        return areas == null ? null : areas.get(id);
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
            for (AreaEffect effect : renderArea.getEffects()) {
                EffectRenderer renderer = renderers.get(effect.typeId());
                if (renderer != null) {
                    renderer.onFrame(areaChanged, effect, lastDuration);
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
        AREA_ID = 0;
        lightAreas.clear();
        selections.clear();
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