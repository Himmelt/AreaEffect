package org.soraworld.areaeffect.proxy;

import com.electronwill.nightconfig.core.file.FileConfig;
import io.netty.buffer.Unpooled;
import net.minecraft.client.GameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.VideoSettingsScreen;
import net.minecraft.command.ICommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.network.play.server.*;
import net.minecraft.potion.EffectInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.thread.EffectiveSide;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.hooks.BasicEventHooks;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import org.soraworld.areaeffect.AreaEffect;
import org.soraworld.areaeffect.command.AreaCommand;
import org.soraworld.areaeffect.handler.ClientEventHandler;
import org.soraworld.areaeffect.handler.CommonEventHandler;
import org.soraworld.areaeffect.network.Area;
import org.soraworld.areaeffect.network.AreaPacket;
import org.soraworld.areaeffect.util.Vec3d;
import org.soraworld.areaeffect.util.Vec3i;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

import static org.soraworld.areaeffect.AreaEffect.MOD_ID;
import org.soraworld.areaeffect.util.GammaCurve;

/**
 * @author Himmelt
 */
public class CommonProxy {

    private Item tool = Items.WOODEN_AXE;
    private int AREA_ID = 0;
    private float duration = 1.0F;
    private double originGamma = 0.0F;
    private double originLightness = 0.0D;
    private int elapsed = Integer.MAX_VALUE;
    private boolean inArea = false;
    private int lastAreaId = -1;
    private float lastLightness = -1.0F;

    private Minecraft mc;
    private FileConfig config;
    private GameSettings gameSettings;
    private final HashMap<UUID, Vec3i> pos1s = new HashMap<>();
    private final HashMap<UUID, Vec3i> pos2s = new HashMap<>();
    private final HashMap<Integer, HashMap<Integer, Area>> lightAreas = new HashMap<>();
    private final ResourceLocation WECUI_CHANNEL = new ResourceLocation("worldedit", "wecui");
    private final ResourceLocation CHANNEL_NAME = new ResourceLocation(MOD_ID, "light");
    private final SimpleChannel channel = NetworkRegistry.newSimpleChannel(CHANNEL_NAME, () -> "1.0", (v) -> true, (v) -> true);

    private static final byte UPDATE = 1;
    private static final byte DELETE = 2;
    private static final byte LIGHTNESS = 3;
    private static final byte DURATION = 4;
    private static final byte[] CUBOID = "s|cuboid".getBytes(StandardCharsets.UTF_8);

    public void onCommonSetup(FMLCommonSetupEvent event) {
        channel.registerMessage(UPDATE, AreaPacket.Update.class, AreaPacket.Update::encode, AreaPacket.Update::decode, this::processUpdate);
        channel.registerMessage(DELETE, AreaPacket.Delete.class, AreaPacket.Delete::encode, AreaPacket.Delete::decode, this::processDelete);
        channel.registerMessage(LIGHTNESS, AreaPacket.Lightness.class, AreaPacket.Lightness::encode, AreaPacket.Lightness::decode, this::processLightness);
        channel.registerMessage(DURATION, AreaPacket.Duration.class, AreaPacket.Duration::encode, AreaPacket.Duration::decode, this::processDuration);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new CommonEventHandler(this));
    }

    public void onClientSetup(FMLClientSetupEvent event) {
        mc = event.getMinecraftSupplier().get();
        gameSettings = mc.gameSettings;
        originGamma = gameSettings.gamma;
        MinecraftForge.EVENT_BUS.register(new ClientEventHandler(this));
    }

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        AreaCommand.register(event.getCommandDispatcher(), this);
        File save = event.getServer().getWorld(DimensionType.OVERWORLD).getSaveHandler().getWorldDirectory();
        File conf = new File(save, AreaEffect.MOD_ID + ".toml");
        config = FileConfig.of(conf);
        if (!conf.exists()) {
            save();
        } else {
            load();
        }
    }

    public void processUpdate(AreaPacket.Update packet, Supplier<NetworkEvent.Context> context) {
        if (EffectiveSide.get() == LogicalSide.CLIENT) {
            context.get().enqueueWork(() -> lightAreas.computeIfAbsent(packet.dim, dim -> new HashMap<>()).put(packet.id, packet.data));
        }
    }

    public void processDelete(AreaPacket.Delete packet, Supplier<NetworkEvent.Context> context) {
        if (EffectiveSide.get() == LogicalSide.CLIENT) {
            context.get().enqueueWork(() -> {
                Map<Integer, Area> areas = lightAreas.get(packet.dim);
                if (areas != null && !areas.isEmpty()) {
                    areas.remove(packet.id);
                }
            });
        }
    }

    public void processLightness(AreaPacket.Lightness packet, Supplier<NetworkEvent.Context> context) {
        if (EffectiveSide.get() == LogicalSide.CLIENT) {
            context.get().enqueueWork(() -> {
                Map<Integer, Area> areas = lightAreas.get(packet.dim);
                if (areas != null && !areas.isEmpty()) {
                    Area area = areas.get(packet.id);
                    if (area != null) {
                        area.lightness = packet.lightness;
                    }
                }
            });
        }
    }

    public void processDuration(AreaPacket.Duration packet, Supplier<NetworkEvent.Context> context) {
        if (EffectiveSide.get() == LogicalSide.CLIENT) {
            context.get().enqueueWork(() -> {
                Map<Integer, Area> areas = lightAreas.get(packet.dim);
                if (areas != null && !areas.isEmpty()) {
                    Area area = areas.get(packet.id);
                    if (area != null) {
                        area.duration = packet.duration;
                    }
                }
            });
        }
    }

    public void load() {
        config.load();
        setSelectTool(config.get("tool"));
        List<String> list = config.get("areas");
        lightAreas.clear();
        AREA_ID = 0;
        if (list != null && !list.isEmpty()) {
            list.forEach(text -> {
                String[] ss = text.split(",");
                try {
                    int off = "2".equals(ss[0]) ? 1 : 0;
                    int dim = Integer.parseInt(ss[off]);
                    Vec3i pos1 = new Vec3i(Integer.parseInt(ss[off + 1]), Integer.parseInt(ss[off + 2]), Integer.parseInt(ss[off + 3]));
                    Vec3i pos2 = new Vec3i(Integer.parseInt(ss[off + 4]), Integer.parseInt(ss[off + 5]), Integer.parseInt(ss[off + 6]));
                    float lightness;
                    float duration;
                    if (off == 0) {
                        // legacy row: field 8 is an old gamma value, convert it with the old reference scene
                        float gamma = ss.length >= 8 ? Float.parseFloat(ss[7]) : 1.0F;
                        lightness = (float) GammaCurve.perceive(gamma, 0.05);
                        duration = 1.0F;
                    } else {
                        lightness = ss.length >= 9 ? Float.parseFloat(ss[8]) : 90.0F;
                        duration = ss.length >= 10 ? Float.parseFloat(ss[9]) : 1.0F;
                    }
                    addArea(dim, pos1, pos2, lightness, duration);
                } catch (Throwable ignored) {
                }
            });
        }
    }

    public void save() {
        config.set("tool", getToolName());
        List<String> list = new ArrayList<>();
        lightAreas.forEach((dim, areas) -> areas.values().forEach(area -> list.add("2," + dim + "," + area)));
        config.set("areas", list);
        config.save();
    }

    public void updateClientGamma(PlayerEntity player) {
        if (mc.currentScreen instanceof VideoSettingsScreen) {
            return;
        }
        double raw = rawLightAt(player);
        double targetGamma;
        double targetLightness;
        float seconds;
        boolean restart = false;
        Area area = findAreaAt(player);
        if (area != null) {
            seconds = area.duration;
            targetLightness = area.lightness;
            targetGamma = GammaCurve.gammaFromLightness(area.lightness, raw);
            if (!inArea || area.id != lastAreaId || area.lightness != lastLightness) {
                restart = true;
            }
            inArea = true;
            lastAreaId = area.id;
            lastLightness = area.lightness;
            duration = area.duration;
        } else {
            seconds = duration;
            targetGamma = originGamma;
            targetLightness = GammaCurve.perceive(originGamma, raw);
            if (inArea) {
                restart = true;
            }
            inArea = false;
            lastAreaId = -1;
        }
        if (restart) {
            originLightness = GammaCurve.perceive(gameSettings.gamma, raw);
            elapsed = 0;
        }
        int ticks = Math.max(1, Math.round(seconds * 20.0F));
        double gamma;
        if (elapsed >= ticks) {
            gamma = targetGamma;
        } else {
            double lightness = GammaCurve.glide(originLightness, targetLightness, elapsed, ticks);
            gamma = GammaCurve.gammaFromLightness(lightness, raw);
            elapsed++;
        }
        gameSettings.gamma = gamma;
    }


    /** Scene base light level (the lightmap's raw value) at the player's position. */
    private double rawLightAt(PlayerEntity player) {
        return player.getBrightness();
    }

    public void saveLight() {
        originGamma = gameSettings.gamma;
    }

    public void clientReset() {
        tool = Items.WOODEN_AXE;
        duration = 1.0F;
        elapsed = Integer.MAX_VALUE;
        inArea = false;
        lastAreaId = -1;
        AREA_ID = 0;
        if (config != null) {
            config.clear();
        }
        lightAreas.clear();
        pos1s.clear();
        pos2s.clear();
        gameSettings.gamma = originGamma;
    }

    private void setSelectTool(String toolName) {
        if (toolName != null) {
            Registry.ITEM.getValue(new ResourceLocation(toolName)).ifPresent(item -> {
                tool = item;
            });
        }
    }

    private String getToolName() {
        return Objects.requireNonNull(tool.getRegistryName()).toString();
    }

    public void setPos1(PlayerEntity player, BlockPos pos, boolean msg) {
        setPos1(player, new Vec3i(pos.getX(), pos.getY(), pos.getZ()), msg);
    }

    public void setPos1(PlayerEntity player, Vec3i pos, boolean msg) {
        pos1s.put(player.getUniqueID(), pos);
        if (msg) {
            sendChatTranslation(player, "set.pos1", pos);
        }
        updateCUI(player);
    }

    public void setPos2(PlayerEntity player, BlockPos pos, boolean msg) {
        setPos2(player, new Vec3i(pos.getX(), pos.getY(), pos.getZ()), msg);
    }

    public void setPos2(PlayerEntity player, Vec3i pos, boolean msg) {
        pos2s.put(player.getUniqueID(), pos);
        if (msg) {
            sendChatTranslation(player, "set.pos2", pos);
        }
        updateCUI(player);
    }

    public void updateCUI(PlayerEntity player) {
        Vec3i pos1 = pos1s.get(player.getUniqueID());
        Vec3i pos2 = pos2s.get(player.getUniqueID());
        if (pos1 == null) {
            if (pos2 == null) {
                return;
            }
            pos1 = pos2;
        } else if (pos2 == null) {
            pos2 = pos1;
        }
        int size = (pos2.x - pos1.x + 1) * (pos2.y - pos1.y + 1) * (pos2.z - pos1.z + 1);
        // CUI Packet
        if (player instanceof ServerPlayerEntity) {
            ServerPlayNetHandler connection = ((ServerPlayerEntity) player).connection;
            connection.sendPacket(new SCustomPayloadPlayPacket(WECUI_CHANNEL, new PacketBuffer(Unpooled.copiedBuffer(CUBOID))));
            connection.sendPacket(new SCustomPayloadPlayPacket(WECUI_CHANNEL, new PacketBuffer(Unpooled.copiedBuffer(pos1.cui(1, size)))));
            connection.sendPacket(new SCustomPayloadPlayPacket(WECUI_CHANNEL, new PacketBuffer(Unpooled.copiedBuffer(pos2.cui(2, size)))));
        }
    }

    public void sendAllAreasTo(ServerPlayerEntity player) {
        if (isDedicated(player)) {
            lightAreas.forEach((dim, areas) -> areas.forEach((id, area) -> sendUpdateTo(player, dim, id, area)));
        }
    }

    public void sendUpdateTo(ServerPlayerEntity player, int dim, int id, Area area) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), new AreaPacket.Update(dim, id, area));
    }

    public void sendUpdateToAll(int dim, int id, Area area) {
        channel.send(PacketDistributor.ALL.noArg(), new AreaPacket.Update(dim, id, area));
    }

    public void sendDeleteToAll(int dim, int id) {
        channel.send(PacketDistributor.ALL.noArg(), new AreaPacket.Delete(dim, id));
    }

    public void sendLightnessToAll(int dim, int id, float lightness) {
        channel.send(PacketDistributor.ALL.noArg(), new AreaPacket.Lightness(dim, id, lightness));
    }

    public void sendDurationToAll(int dim, int id, float duration) {
        channel.send(PacketDistributor.ALL.noArg(), new AreaPacket.Duration(dim, id, duration));
    }

    public void createArea(ServerPlayerEntity player, float lightness, float duration) {
        Vec3i pos1 = pos1s.get(player.getUniqueID());
        Vec3i pos2 = pos2s.get(player.getUniqueID());
        if (pos1 != null && pos2 != null) {
            Area area = addArea(player.dimension.getId(), pos1, pos2, lightness, duration);
            if (area == null) {
                sendChatTranslation(player, "create.conflict");
            } else {
                sendChatTranslation(player, "create.area");
                if (isDedicated(player)) {
                    sendUpdateToAll(player.dimension.getId(), AREA_ID, area);
                }
                save();
            }
        } else {
            sendChatTranslation(player, "notSelect");
        }
    }

    public Area addArea(int dim, Vec3i pos1, Vec3i pos2, float lightness, float duration) {
        Area area = new Area(pos1.x, pos1.y, pos1.z, pos2.x, pos2.y, pos2.z, lightness, duration);
        if (checkConflict(dim, area)) {
            return null;
        } else {
            AREA_ID++;
            lightAreas.computeIfAbsent(dim, d -> new HashMap<>()).put(AREA_ID, area);
            return area;
        }
    }

    public void deleteArea(ServerPlayerEntity player) {
        Area area = findAreaAt(player);
        if (area != null) {
            lightAreas.get(player.dimension.getId()).remove(area.id);
            save();
            if (isDedicated(player)) {
                sendDeleteToAll(player.dimension.getId(), area.id);
            }
        }
    }

    public Area findAreaAt(PlayerEntity player) {
        for (Map.Entry<Integer, Area> entry : lightAreas.getOrDefault(player.dimension.getId(), new HashMap<>()).entrySet()) {
            Area area = entry.getValue();
            if (area.contains(new Vec3d(player))) {
                area.id = entry.getKey();
                return area;
            }
        }
        return null;
    }

    public boolean checkConflict(int dim, Area intent) {
        for (Area area : lightAreas.getOrDefault(dim, new HashMap<>()).values()) {
            if (intent.conflict(area)) {
                return true;
            }
        }
        return false;
    }

    public void clearSelect(PlayerEntity player) {
        pos1s.remove(player.getUniqueID());
        pos2s.remove(player.getUniqueID());
    }

    public void sendChatTranslation(ICommandSource sender, String key, Object... args) {
        sender.sendMessage(new TranslationTextComponent(key, args));
    }

    public void sendChatTranslation2(PlayerEntity player, String key, String objKey) {
        player.sendMessage(new TranslationTextComponent(key, new TranslationTextComponent(objKey)));
    }

    public void sendAreaInfo(PlayerEntity player, int dim, int id, Area area) {
        Style style = new Style().setColor(TextFormatting.GREEN).setBold(true).setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/areaeffect tp " + id));
        ITextComponent click = new TranslationTextComponent("text.click").setStyle(style);
        player.sendMessage(new TranslationTextComponent("info.list", id, dim, area.pos1(), area.pos2(), area.lightness, area.duration, click));
    }

    public void commandTool(PlayerEntity player) {
        ItemStack stack = player.getHeldItemMainhand();
        if (stack.getItem() != Items.AIR) {
            tool = stack.getItem();
            save();
            sendChatTranslation2(player, "tool.set", tool.getTranslationKey(stack));
        } else {
            sendChatTranslation2(player, "tool.get", tool.getTranslationKey());
        }
    }

    public void showList(PlayerEntity player, int dim, boolean all) {
        if (all) {
            lightAreas.forEach((dimId, dimAreas) -> dimAreas.forEach((id, area) -> sendAreaInfo(player, dimId, id, area)));
        } else {
            lightAreas.getOrDefault(dim, new HashMap<>()).forEach((id, area) -> sendAreaInfo(player, dim, id, area));
        }
    }

    public void tpAreaById(ServerPlayerEntity player, int id) {
        if (player == null) {
            return;
        }
        for (Map.Entry<Integer, HashMap<Integer, Area>> entry : lightAreas.entrySet()) {
            int dim = entry.getKey();
            HashMap<Integer, Area> areas = entry.getValue();
            Area area = areas.get(id);
            if (area != null) {
                DimensionType type = DimensionType.getById(dim);
                if (type != null && changeDimension(player, type, area.center())) {
                    sendChatTranslation(player, "areaTpSuccess");
                }
                return;
            }
        }
        sendChatTranslation(player, "areaIdNotFound");
    }

    public boolean isSelectTool(ItemStack stack) {
        return stack != null && stack.getItem().equals(tool);
    }

    public static boolean isDedicated(ServerPlayerEntity player) {
        MinecraftServer server = getServer(player);
        return server != null && server.isDedicatedServer();
    }

    public static MinecraftServer getServer(ServerPlayerEntity player) {
        if (player != null && player.world instanceof ServerWorld) {
            return ((ServerWorld) player.world).getServer();
        }
        return null;
    }

    public static boolean changeDimension(ServerPlayerEntity player, DimensionType to, BlockPos target) {
        return changeDimension(player, to, target.getX(), target.getY(), target.getZ());
    }

    public static boolean changeDimension(ServerPlayerEntity player, DimensionType to, double toX, double toY, double toZ) {
        if (player.dimension == to) {
            player.setPositionAndUpdate(toX, toY, toZ);
            return true;
        }
        if (!ForgeHooks.onTravelToDimension(player, to)) {
            return false;
        }
        DimensionType from = player.dimension;
        player.detach();
        ServerWorld fromWorld = player.server.getWorld(from);
        player.dimension = to;
        ServerWorld toWorld = player.server.getWorld(to);
        WorldInfo oldInfo = toWorld.getWorldInfo();
        NetworkHooks.sendDimensionDataPacket(player.connection.netManager, player);
        player.connection.sendPacket(new SRespawnPacket(to, WorldInfo.byHashing(oldInfo.getSeed()), oldInfo.getGenerator(), player.interactionManager.getGameType()));
        player.connection.sendPacket(new SServerDifficultyPacket(oldInfo.getDifficulty(), oldInfo.isDifficultyLocked()));
        PlayerList playerlist = player.server.getPlayerList();
        playerlist.updatePermissionLevel(player);
        fromWorld.removeEntity(player, true);
        player.revive();

        float pitch = player.rotationPitch;
        float yaw = player.rotationYaw;

        fromWorld.getProfiler().startSection("moving");
        player.setLocationAndAngles(toX, toY, toZ, yaw, pitch);
        fromWorld.getProfiler().endSection();

        player.setWorld(toWorld);
        toWorld.addDuringPortalTeleport(player);
        player.connection.setPlayerLocation(player.getPosX(), player.getPosY(), player.getPosZ(), yaw, pitch);
        player.interactionManager.setWorld(toWorld);
        player.connection.sendPacket(new SPlayerAbilitiesPacket(player.abilities));
        playerlist.sendWorldInfo(player, toWorld);
        playerlist.sendInventory(player);

        for (EffectInstance instance : player.getActivePotionEffects()) {
            player.connection.sendPacket(new SPlayEntityEffectPacket(player.getEntityId(), instance));
        }

        player.connection.sendPacket(new SPlaySoundEventPacket(1032, BlockPos.ZERO, 0, false));
        BasicEventHooks.firePlayerChangedDimensionEvent(player, from, to);
        return true;
    }
}
