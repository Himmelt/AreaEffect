package org.soraworld.areaeffect.client;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
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
import org.soraworld.areaeffect.common.network.MessageDurationUpdate;
import org.soraworld.areaeffect.common.network.MessageLightnessUpdate;
import org.soraworld.areaeffect.common.network.MessageListReply;
import org.soraworld.areaeffect.common.network.MessageListRequest;
import org.soraworld.areaeffect.common.network.MessageSelection;
import org.soraworld.areaeffect.common.network.MessageSetProps;
import org.soraworld.areaeffect.common.network.PacketChannel;
import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientProxy extends CommonProxy {

    public static final KeyBinding KEY_LIST = new KeyBinding("key.areaeffect.list", Keyboard.KEY_J, "key.categories.areaeffect");

    private boolean inArea = false;
    private int lastAreaId = -1;
    private float lastDuration = 1.0F;

    private Vec3i selPos1 = null;
    private Vec3i selPos2 = null;

    private EffectRenderers renderers = new EffectRenderers();

    private final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public void onPreInit(FMLPreInitializationEvent event) {
        super.onPreInit(event);
        AreaClientHandler handler = new AreaClientHandler(this);
        FMLCommonHandler.instance().bus().register(handler);
        MinecraftForge.EVENT_BUS.register(new SelectionRenderHandler(this));
        ClientRegistry.registerKeyBinding(KEY_LIST);
    }

    @Override
    public void onInit(FMLInitializationEvent event) {
        super.onInit(event);
        // 客户端方向消息的处理逻辑绑定
        PacketChannel.bindClient(MessageAreaUpdate.class, this::handleUpdate);
        PacketChannel.bindClient(MessageAreaDelete.class, this::handleDelete);
        PacketChannel.bindClient(MessageLightnessUpdate.class, this::handleLightness);
        PacketChannel.bindClient(MessageDurationUpdate.class, this::handleDuration);
        PacketChannel.bindClient(MessageSelection.class, this::handleSelection);
        PacketChannel.bindClient(MessageListReply.class, this::handleListReply);
    }

    public void sendListRequest() {
        PacketChannel.sendToServer(new MessageListRequest());
    }

    public void sendSetProps(int dim, int id, List<AreaEffect> effects) {
        PacketChannel.sendToServer(new MessageSetProps(dim, id, effects));
    }

    public void handleUpdate(MessageAreaUpdate packet) {
        lightAreas.computeIfAbsent(packet.dim, dim -> new HashMap<>()).put(packet.id, packet.data);
    }

    public void handleDelete(MessageAreaDelete packet) {
        Map<Integer, Area> areas = lightAreas.get(packet.dim);
        if (areas != null && !areas.isEmpty()) {
            areas.remove(packet.id);
        }
    }

    public void handleLightness(MessageLightnessUpdate packet) {
        Map<Integer, Area> areas = lightAreas.get(packet.dim);
        if (areas != null && !areas.isEmpty()) {
            Area area = areas.get(packet.id);
            if (area != null) {
                area.setLightness(packet.lightness);
            }
        }
    }

    public void handleDuration(MessageDurationUpdate packet) {
        Map<Integer, Area> areas = lightAreas.get(packet.dim);
        if (areas != null && !areas.isEmpty()) {
            Area area = areas.get(packet.id);
            if (area != null) {
                area.setDuration(packet.duration);
            }
        }
    }

    public void handleSelection(MessageSelection packet) {
        selPos1 = packet.pos1;
        selPos2 = packet.pos2;
    }

    public void handleListReply(MessageListReply packet) {
        mc.displayGuiScreen(new GuiAreaList(this, packet.dim, packet.areas));
    }

    public Vec3i getSelPos1() {
        return selPos1;
    }

    public Vec3i getSelPos2() {
        return selPos2;
    }

    /**
     * 每 tick 客户端更新：判定当前所在区域，再把该区域挂载的各效果分发给对应的
     * 客户端运行时驱动过渡。区域外则让所有运行时回退到 0。
     */
    public void updateClientLight(EntityPlayer player) {
        LightmapHook.tryInstall(mc);
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
        renderers = new EffectRenderers();
        LightmapHook.setOffset(0.0D);
    }
}