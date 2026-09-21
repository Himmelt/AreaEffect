package org.soraworld.areaeffect.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import org.soraworld.areaeffect.client.effect.EffectRenderer;
import org.soraworld.areaeffect.client.effect.EffectRenderers;
import org.soraworld.areaeffect.client.gui.GuiAreas;
import org.soraworld.areaeffect.client.handler.AreaClientHandler;
import org.soraworld.areaeffect.client.handler.ClientSelectionHandler;
import org.soraworld.areaeffect.client.handler.FogRenderHandler;
import org.soraworld.areaeffect.client.handler.LightmapHook;
import org.soraworld.areaeffect.client.handler.SelectionRenderHandler;
import org.soraworld.areaeffect.client.handler.SkyRenderHandler;
import org.soraworld.areaeffect.common.CommonProxy;
import org.soraworld.areaeffect.common.area.AreaTable;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.network.Area;
import org.soraworld.areaeffect.common.network.MessageAreaDelete;
import org.soraworld.areaeffect.common.network.MessageAreaUpdate;
import org.soraworld.areaeffect.common.network.MessageClickAir;
import org.soraworld.areaeffect.common.network.MessageDeleteRequest;
import org.soraworld.areaeffect.common.network.MessageListReply;
import org.soraworld.areaeffect.common.network.MessageListRequest;
import org.soraworld.areaeffect.common.network.MessageSelectShape;
import org.soraworld.areaeffect.common.network.MessageSelection;
import org.soraworld.areaeffect.common.network.MessageSetProps;
import org.soraworld.areaeffect.common.network.MessageToolSync;
import org.soraworld.areaeffect.common.network.MessageTpRequest;
import org.soraworld.areaeffect.common.network.PacketChannel;
import org.soraworld.areaeffect.common.shape.Selection;
import org.soraworld.areaeffect.common.util.Vec3d;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientProxy extends CommonProxy {

    /**
     * 按键码：1.13 的 {@code KeyBinding} 不再吃 LWJGL2 的 {@code Keyboard.KEY_*}，
     * 统一用 GLFW 的键码常量。
     */
    public static final KeyBinding KEY_LIST = new KeyBinding("key.areaeffect.manager",
            GLFW.GLFW_KEY_J, "key.categories.areaeffect");
    public static final KeyBinding KEY_SEL_RENDER = new KeyBinding("key.areaeffect.selrender",
            GLFW.GLFW_KEY_K, "key.categories.areaeffect");

    /** 该类型尚无记录时的淡出时长（秒）。 */
    private static final float DEFAULT_DURATION = 1.0F;

    /**
     * 「离开所有区域」时用于淡出的过渡时长（秒），<b>按效果类型各记一份</b>最近生效值。
     * 分开记的原因：玩家离开区域时区域已不可见，运行时只能靠这份记录决定淡出速度；
     * 若全局共用一份，雾/天空的淡出速度就会被亮度效果（或任意最后写入者）的时长顶替。
     */
    private final Map<String, Float> lastDurationByType = new HashMap<>();

    private Selection selSelection = null;
    private boolean showSelection = true;
    /** 各维度中已开启线框显示的区域 id 集合（客户端本地设置）。 */
    private final Map<Integer, Set<Integer>> visibleAreas = new ConcurrentHashMap<>();

    /**
     * 编辑预览的效果列表覆盖：dim → id → 工作副本（每种效果各拷一份）。
     * 独立于客户端镜像 {@link #clientAreas}、从不写回区域对象，故镜像始终等于服务端同步值、未保存的编辑不外泄。
     * 按效果泛化：亮度/雾/天空的滑条编辑都能经 {@link #effectiveArea} 实时预览（原有单亮度覆盖并入此模型）。
     */
    private final Map<Integer, Map<Integer, List<AreaEffect>>> previewEffects = new ConcurrentHashMap<>();
    /** 编辑预览备注覆盖：dim → id → String（同样不入共享对象）。 */
    private final Map<Integer, Map<Integer, String>> previewRemarks = new ConcurrentHashMap<>();

    /** 上一帧各效果类型的获胜区域 id（-1 表示该类型当帧无获胜区域）。
     *  类型级"进入/切换/离开"的过渡触发由它与当前帧对比判定（替代旧的单区域查找缓存）。 */
    private final Map<String, Integer> lastWinAreaByType = new HashMap<>();

    /** 选区形状轮切 overlay 状态：当前提示的形状与最近一次轮切时间（毫秒）。 */
    private String overlayShape = null;
    private long overlayLastAction = 0L;

    private EffectRenderers renderers = new EffectRenderers();

    /** netty 线程投递、需在客户端主线程执行的逻辑（GUI 必须回主线程操作）。 */
    // 1.13 的 SimpleChannel 已在 enqueueWork 里把回调投到客户端主线程，入站消息本身即在主线程执行；
    // 这里保留投递入口仅为"从任意线程安全进入主线程"的通用语义（1.14 的入口是 Minecraft#execute(Runnable)，与 1.12 的 addScheduledTask 等价）。

    private final Minecraft mc = Minecraft.getInstance();

    /**
     * 客户端只读镜像：与继承自 {@code CommonProxy} 的服务端权威 {@code areas} 相互独立、永不共享实例。
     * 只由网络入站处理器（{@link #handleUpdate}/{@link #handleDelete}）与断线复位
     * （{@link #clientReset()}）写入，渲染与 GUI 一律读它；单机（集成服）同样经完整网络回环填充，
     * 不走共享捷径。权威 {@code areas} 不参与客户端渲染。
     */
    private final AreaTable clientAreas = new AreaTable();

    /**
     * 客户端选区工具镜像：与服务端权威 {@code CommonProxy#tool} 独立、不共享。只由 {@link #handleToolSync}
     * 经 {@code MessageToolSync} 写入、{@link #clientReset} 复位，客户端交互判定 {@link #isSelectToolLocal} 只读它。
     */
    private Item clientTool = Items.WOODEN_AXE;

    /**
     * 客户端装配：绑定客户端方向消息、注册按键与渲染挂钩。
     *
     * <p>1.13 起没有 {@code FMLPreInitializationEvent}/{@code FMLInitializationEvent}，两者合并到
     * {@link FMLClientSetupEvent}（它在 {@code FMLCommonSetupEvent} 之后触发，届时
     * {@code CommonProxy#onCommonSetup} 已把 opcode 表与服务端方向处理绑定完成）。
     */
    @Override
    public void onClientSetup(FMLClientSetupEvent event) {
        super.onClientSetup(event);
        // 客户端方向消息的处理逻辑绑定
        PacketChannel.bindClient(MessageAreaUpdate.class, this::handleUpdate);
        PacketChannel.bindClient(MessageAreaDelete.class, this::handleDelete);
        PacketChannel.bindClient(MessageSelection.class, this::handleSelection);
        PacketChannel.bindClient(MessageListReply.class, this::handleListReply);
        PacketChannel.bindClient(MessageToolSync.class, this::handleToolSync);
        AreaClientHandler handler = new AreaClientHandler(this);
        // 1.13 的 TickEvent 系列与所有 Forge 事件都只在 MinecraftForge.EVENT_BUS 上派发，
        // 客户端 handler 统一注册于此。
        MinecraftForge.EVENT_BUS.register(handler);
        MinecraftForge.EVENT_BUS.register(new SelectionRenderHandler(this));
        MinecraftForge.EVENT_BUS.register(new ClientSelectionHandler(this));
        // FogColors / FogDensity、ClientTick 及天空装卸都在 EVENT_BUS 上，同一实例一次注册即可
        MinecraftForge.EVENT_BUS.register(new FogRenderHandler());
        MinecraftForge.EVENT_BUS.register(new SkyRenderHandler());
        ClientRegistry.registerKeyBinding(KEY_LIST);
        ClientRegistry.registerKeyBinding(KEY_SEL_RENDER);
    }

    /** 同步服务端下发的选区工具到客户端镜像 {@link #clientTool}（客户端不读 config）。 */
    public void handleToolSync(MessageToolSync packet) {
        runOnClientThread(() -> {
            // 1.13 起物品数字 id 废弃，按注册名（mod:item）查物品注册表；查不到回落木斧
            try {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(packet.toolName));
                clientTool = item != null ? item : Items.WOODEN_AXE;
            } catch (Throwable ignored) {
                clientTool = Items.WOODEN_AXE;
            }
        });
    }

    /** 客户端本地判定：手持物是否为选区工具（读客户端镜像 {@link #clientTool}，非服务端权威 tool）。 */
    public boolean isSelectToolLocal(ItemStack stack) {
        // 空手为 ItemStack.EMPTY，用 isEmpty() 明确排除
        return stack != null && !stack.isEmpty() && Objects.equals(stack.getItem(), clientTool);
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
        runOnClientThread(() -> {
            // Area.fromByteBuf 有意不携带 id（id 只走 packet.id），反序列化出的 Area.id 恒为默认 0；
            // 服务端在入表前会赋 id（AreaTable#add / AreaStore），客户端镜像此处补齐，
            // 否则 GUI 的 "#"+area.id 及一切按 id 的定位都会把同步来的区域读成 #0。
            packet.data.id = packet.id;
            clientAreas.put(packet.dim, packet.id, packet.data);
            // 更新已打开的管理界面：保存/他端改动经此回显后，界面必须换绑到新实例，
            // 否则 areas 快照仍指向旧实例，"切到别的区域再切回来"会重读到保存前的旧值。
            // refreshFromProxy 内部按 dirty 保护未保存编辑，故不会覆盖正在进行的改动。
            if (mc.currentScreen instanceof GuiAreas) {
                ((GuiAreas) mc.currentScreen).refreshFromProxy();
            }
        });
    }

    public void handleDelete(MessageAreaDelete packet) {
        // 同上：镜像写入与界面刷新一并回客户端主线程执行
        runOnClientThread(() -> {
            clientAreas.remove(packet.dim, packet.id);
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

    public void handleSelection(MessageSelection packet) {
        // 选区镜像 netty 线程写、渲染线程读，需回客户端主线程统一更新，避免数据竞争
        runOnClientThread(() -> {
            selSelection = new Selection();
            selSelection.shapeType = packet.shapeType;
            Collections.addAll(selSelection.anchors, packet.anchors);
        });
    }

    /**
     * 收到面板授权：打开或刷新区域管理界面。
     *
     * <p>消息本身<b>不带数据</b>（见 {@link MessageListReply}）：界面直接读本地镜像，
     * 而且是跨维度的全量（左栏列所有存在区域的维度），所以这里不需要、也不该使用任何 payload。
     */
    public void handleListReply(MessageListReply packet) {
        // 回调可能来自网络线程：GUI 操作必须回客户端主线程
        runOnClientThread(() -> {
            // 已打开界面则刷新，否则打开主界面（读取全量本地数据，跨维度）
            if (mc.currentScreen instanceof GuiAreas) {
                ((GuiAreas) mc.currentScreen).refreshFromProxy();
            } else {
                mc.displayGuiScreen(new GuiAreas(this));
            }
        });
    }

    /** 投递需在客户端主线程执行的逻辑（GUI 等必须回主线程操作）。 */
    private void runOnClientThread(Runnable task) {
        mc.execute(task);
    }

    /** 当前本地选区镜像（入站线程写，渲染线程读）。 */
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
        // 1.12 的 Minecraft#getSystemTime 已被移除，改用 1.13 的 Util#milliTime（同为毫秒单调时钟）
        overlayLastAction = Util.milliTime();
    }

    /** overlay 当前展示的形状类型；无则返回 null。 */
    public String getOverlayShape() {
        return overlayShape;
    }

    /** 清空 overlay 状态（淡出结束调用，防止残留状态被再次绘制）。 */
    public void clearShapeOverlay() {
        overlayShape = null;
    }

    /** overlay 最近一次轮切触发时间（{@link Util#milliTime()} 基准）。 */
    public long getOverlayLastAction() {
        return overlayLastAction;
    }

    /** 切换选区线框显示（客户端本地设置），并本地提示。 */
    public void toggleSelection() {
        showSelection = !showSelection;
        if (mc.player != null) {
            mc.player.sendMessage(new TranslationTextComponent(showSelection ? "chat.selection.on" : "chat.selection.off"));
        }
    }

    /** 指定区域的线框显示是否开启（客户端本地设置）。 */
    public boolean isAreaVisible(int dim, int id) {
        Set<Integer> ids = visibleAreas.get(dim);
        return ids != null && ids.contains(id);
    }

    /** 切换指定区域的线框显示（客户端本地设置）。 */
    public void toggleAreaVisible(int dim, int id) {
        Set<Integer> ids = visibleAreas.computeIfAbsent(dim, d -> ConcurrentHashMap.newKeySet());
        if (!ids.remove(id)) {
            ids.add(id);
        }
    }

    /** 当前维度中已开启线框显示的区域列表。 */
    public List<Area> getVisibleAreas(int dim) {
        List<Area> result = new ArrayList<>();
        Set<Integer> ids = visibleAreas.get(dim);
        if (ids != null) {
            Map<Integer, Area> dimAreas = clientAreas.inDim(dim);
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
     * 编辑界面实时预览：把工作副本 {@code effects}（各效果已拷过）写入覆盖表（不修改共享 Area）。
     * 渲染器通过 {@link #effectiveArea} 读取覆盖，下一帧即生效；
     * 未保存退出/切换时用 {@link #clearPreview} 移除覆盖即可还原原值。
     * 写入前对每个效果再拷一份，保证覆盖表与调用方可变列表相互独立。
     */
    public void previewAreaEffects(int dim, int id, List<AreaEffect> effects) {
        List<AreaEffect> copy = new ArrayList<>(effects.size());
        for (AreaEffect effect : effects) {
            if (effect != null) {
                copy.add(effect.copy());
            }
        }
        previewEffects.computeIfAbsent(dim, d -> new ConcurrentHashMap<>()).put(id, copy);
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
        Map<Integer, List<AreaEffect>> m = previewEffects.get(dim);
        if (m == null) {
            return area;
        }
        List<AreaEffect> override = m.get(area.id);
        if (override == null) {
            return area;
        }
        Area tmp = new Area(area.shape());
        tmp.id = area.id;
        tmp.setEffects(override);
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
        Map<Integer, List<AreaEffect>> m = previewEffects.get(dim);
        if (m != null) {
            m.remove(id);
        }
        Map<Integer, String> r = previewRemarks.get(dim);
        if (r != null) {
            r.remove(id);
        }
    }

    /** 客户端本地区域列表快照（读客户端镜像，按 id 升序，用于返回列表界面，无需再走服务端请求）。 */
    public List<Area> getAreasLocal(int dim) {
        return clientAreas.sortedIn(dim);
    }

    /** 所有存在区域的维度列表（读客户端镜像，升序）。 */
    public List<Integer> getDimsLocal() {
        return clientAreas.dims();
    }

    /** 客户端玩家当前所在区域：读客户端镜像 {@link #clientAreas}（服务端权威查询见 {@code AreaTable#findAt}）。 */
    public Area findAreaAt(PlayerEntity player) {
        return clientAreas.findAt(player);
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
     * 每帧客户端更新：一次性取回所有包含玩家的区域（区域允许任意重叠），再<b>按效果类型</b>
     * 各自解析获胜者（权重最高，同权取较大 id），分发给对应运行时驱动过渡。区域外则让所有运行时回退到 0。
     *
     * <p>每种效果类型的"进入/切换/离开"由该类型获胜区域 id 与上一帧对比判定，因此不同类型可独立过渡；
     * 重叠集合内同种效果获胜者由"权重优先、同权取较大 id"确定，结果恒定、同帧不抖动。
     */
    public void updateClientLight(PlayerEntity player) {
        LightmapHook.tryInstall(mc);
        // 1.13 的维度是 DimensionType；区域镜像仍按维度 id 分表
        int dim = player.dimension.getId();
        Vec3d pos = new Vec3d(player);
        List<Area> containing = clientAreas.findAt(dim, pos);

        // 当前游戏/现实时间（小时，0..24），供时间段过滤
        float gameHour = gameHourOf(player.world.getDayTime());
        LocalTime now = LocalTime.now();
        float realHour = now.getHour() + now.getMinute() / 60.0F;

        for (String typeId : renderers.typeIds()) {
            EffectRenderer renderer = renderers.get(typeId);
            if (renderer == null) {
                continue;
            }
            // 解析该类型获胜者：重叠区域内权重最高者；权重相同时取较大 id（即后创建者），
            // 保证重叠集合内结果确定、同帧不抖动（不存在的类型结果为 null）。
            // 不在启用时间段内的效果按"不存在"处理，不参与权重决胜。
            AreaEffect winner = null;
            Area winnerArea = null;
            for (Area area : containing) {
                AreaEffect eff = effectOf(area, typeId);
                if (eff != null && eff.inTimeWindow(gameHour, realHour)
                        && (winnerArea == null || eff.getWeight() > winner.getWeight()
                        || (eff.getWeight() == winner.getWeight() && area.id > winnerArea.id))) {
                    winner = eff;
                    winnerArea = area;
                }
            }
            int curId = winnerArea == null ? -1 : winnerArea.id;
            int prevId = lastWinAreaByType.getOrDefault(typeId, -1);
            boolean areaChanged = prevId != curId;
            lastWinAreaByType.put(typeId, curId);
            // 预览覆盖若存在则用覆盖值驱动（共享 Area 不被预览污染），渲染只用参数、不用权重本身
            AreaEffect renderEffect = null;
            if (winnerArea != null) {
                renderEffect = effectOf(effectiveArea(dim, winnerArea), typeId);
                // 记下该类型最近一次的过渡时长：离开所有区域时该运行时用它做淡出速度（按类型各记一份）
                lastDurationByType.put(typeId,
                        (renderEffect != null ? renderEffect : winner).getDuration());
            }
            renderer.onFrame(areaChanged, renderEffect,
                    lastDurationByType.getOrDefault(typeId, DEFAULT_DURATION));
        }
    }

    /**
     * 世界时间 → 游戏时钟小时（0..24），供「生效时段 = 游戏」过滤用。
     *
     * <p>原版的一天从 <b>06:00</b> 起算：{@code dayTime % 24000 == 0} 是日出（06:00）、
     * {@code 6000} 正午、{@code 12000} 日落（18:00）、{@code 18000} 午夜。因此钟点要在
     * {@code dayTime / 1000} 的基础上偏移 6 小时；漏掉这个偏移会让 GUI 上写的
     * 「游戏 06:00→18:00」实际生效于游戏内 12:00→24:00（一半落在夜里）。
     */
    private static float gameHourOf(long dayTime) {
        long time = dayTime % 24000L;
        if (time < 0L) {
            // 正常游戏时钟不为负，但存档可被手工改成负值；先归一到 [0, 24000) 再换算
            time += 24000L;
        }
        return (time / 1000.0F + 6.0F) % 24.0F;
    }

    /**
     * 连接建立：清空客户端区域镜像 {@link #clientAreas}，随后由登录全量同步重建。
     *
     * <p>镜像与集成服务端的权威 {@code areas} 是<b>不同实例</b>，因此无论单机还是连外部服务器都可安全清空
     * （清了不会动到世界数据）；不清则上个会话残留的 id 会以幽灵区域留存。单机同样走此路径，
     * 与专用服共用一条"清空 → 全量同步"的时序。
     *
     * <p>触发点见 {@code AreaClientHandler#onLoggedIn}：1.14 的 {@code ClientPlayerNetworkEvent.LoggedInEvent}
     * 早于登录同步包的处理，故不会把刚到的新数据抹掉。
     */
    public void onServerConnected() {
        clientAreas.clear();
    }

    /**
     * 断线 / 退出世界：复位全部客户端本地状态。
     *
     * <p>触发点见 {@code AreaClientHandler#onLoggedOut}（1.14 的
     * {@code ClientPlayerNetworkEvent.LoggedOutEvent}）。复位包含清空客户端镜像 {@link #clientAreas}
     * —— 它与集成服务端的权威 {@code areas} 已是不同实例，清它不会动到世界数据；权威表的落盘由
     * AreaStore 负责（服务端侧）。
     */
    public void clientReset() {
        clientTool = Items.WOODEN_AXE;
        lastDurationByType.clear();
        clientAreas.clear();
        selections.clearAll();
        selSelection = null;
        overlayShape = null;
        overlayLastAction = 0L;
        visibleAreas.clear();
        previewEffects.clear();
        previewRemarks.clear();
        lastWinAreaByType.clear();
        renderers = new EffectRenderers();
        LightmapHook.setOffset(0.0D, 0.0D);
        // 断线重建渲染器时显式复位雾/天空的静态状态：不依赖新世界首帧驱动，避免旧的 active/颜色在首帧前短暂残留
        FogRenderHandler.setState(false, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0);
        SkyRenderHandler.setState(false, 0.0D, 0.0D, 0.0D);
    }
}
