package org.soraworld.areaeffect.client;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;
import org.soraworld.areaeffect.client.effect.EffectRenderers;
import org.soraworld.areaeffect.client.effect.EffectRenderer;
import org.soraworld.areaeffect.client.gui.GuiAreaList;
import org.soraworld.areaeffect.client.handler.AreaClientHandler;
import org.soraworld.areaeffect.client.handler.LightmapHook;
import org.soraworld.areaeffect.client.handler.SelectionRenderHandler;
import org.soraworld.areaeffect.common.CommonProxy;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.network.Area;
import org.soraworld.areaeffect.common.network.MessageAreaDelete;
import org.soraworld.areaeffect.common.network.MessageAreaUpdate;
import org.soraworld.areaeffect.common.network.MessageDeleteRequest;
import org.soraworld.areaeffect.common.network.MessageListReply;
import org.soraworld.areaeffect.common.network.MessageListRequest;
import org.soraworld.areaeffect.common.network.MessageSelection;
import org.soraworld.areaeffect.common.network.MessageSetProps;
import org.soraworld.areaeffect.common.network.MessageTpRequest;
import org.soraworld.areaeffect.common.network.PacketChannel;
import org.soraworld.areaeffect.common.util.Vec3i;

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

    private Vec3i selPos1 = null;
    private Vec3i selPos2 = null;
    private boolean showSelection = true;
    /** 各维度中已开启线框显示的区域 id 集合（客户端本地设置）。 */
    private final Map<Integer, Set<Integer>> visibleAreas = new ConcurrentHashMap<>();

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
    }

    public void sendListRequest() {
        PacketChannel.sendToServer(new MessageListRequest());
    }

    public void sendSetProps(int dim, int id, List<AreaEffect> effects) {
        PacketChannel.sendToServer(new MessageSetProps(dim, id, effects));
    }

    public void sendDeleteRequest(int dim, int id) {
        PacketChannel.sendToServer(new MessageDeleteRequest(dim, id));
    }

    public void sendTpRequest(int id) {
        PacketChannel.sendToServer(new MessageTpRequest(id));
    }

    public void handleUpdate(MessageAreaUpdate packet) {
        lightAreas.computeIfAbsent(packet.dim, dim -> new ConcurrentHashMap<Integer, Area>()).put(packet.id, packet.data);
    }

    public void handleDelete(MessageAreaDelete packet) {
        Map<Integer, Area> areas = lightAreas.get(packet.dim);
        if (areas != null && !areas.isEmpty()) {
            areas.remove(packet.id);
        }
    }

    public void handleSelection(MessageSelection packet) {
        selPos1 = packet.pos1;
        selPos2 = packet.pos2;
    }

    public void handleListReply(MessageListReply packet) {
        // netty 线程回调：GUI 操作必须回客户端主线程
        runOnClientThread(() -> mc.displayGuiScreen(new GuiAreaList(this, packet.dim, packet.areas)));
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
        return selPos1;
    }

    public Vec3i getSelPos2() {
        return selPos2;
    }

    public boolean isShowSelection() {
        return showSelection;
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
     * 编辑界面实时预览：只修改客户端本地区域副本的亮度/时长，渲染器下一帧即生效。
     * 未保存退出时可用原值再次调用以还原。
     */
    public void previewAreaProps(int dim, int id, float lightness, float duration) {
        Map<Integer, Area> areas = lightAreas.get(dim);
        if (areas != null) {
            Area target = areas.get(id);
            if (target != null) {
                target.setLightness(lightness);
                target.setDuration(duration);
            }
        }
    }

    /** 客户端本地移除区域（用于删除后立即刷新界面；服务端广播到达后为幂等操作）。 */
    public void clientRemoveArea(int dim, int id) {
        Map<Integer, Area> areas = lightAreas.get(dim);
        if (areas != null) {
            areas.remove(id);
        }
    }

    /** 客户端本地区域列表快照（用于返回列表界面，无需再走服务端请求）。 */
    public List<Area> getAreasLocal(int dim) {
        Map<Integer, Area> areas = lightAreas.get(dim);
        return areas == null ? new ArrayList<>() : new ArrayList<>(areas.values());
    }

    /**
     * 每帧客户端更新：判定当前所在区域，再把该区域挂载的各效果分发给对应的
     * 客户端运行时驱动过渡（渲染器内部按真实时间插值）。区域外则让所有运行时回退到 0。
     */
    public void updateClientLight(EntityPlayer player) {
        LightmapHook.tryInstall(mc);
        drainClientTasks();
        Area area = findAreaAt(player);
        if (area != null) {
            boolean areaChanged = !inArea || area.id != lastAreaId;
            inArea = true;
            lastAreaId = area.id;
            lastDuration = area.getDuration();
            for (AreaEffect effect : area.getEffects()) {
                EffectRenderer renderer = renderers.get(effect.typeId());
                if (renderer != null) {
                    renderer.onFrame(areaChanged, effect, duration);
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
        duration = 1.0F;
        inArea = false;
        lastAreaId = -1;
        lastDuration = 1.0F;
        AREA_ID = 0;
        lightAreas.clear();
        pos1s.clear();
        pos2s.clear();
        selPos1 = null;
        selPos2 = null;
        visibleAreas.clear();
        renderers = new EffectRenderers();
        clientTasks.clear();
        LightmapHook.setOffset(0.0D, 0.0D);
    }
}