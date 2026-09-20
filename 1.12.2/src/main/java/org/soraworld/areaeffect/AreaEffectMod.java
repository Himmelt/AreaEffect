package org.soraworld.areaeffect;

import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.soraworld.areaeffect.common.command.AreaCommand;
import org.soraworld.areaeffect.common.CommonProxy;

import java.io.File;

@Mod(
        modid = AreaEffectMod.MOD_ID,
        name = AreaEffectMod.MOD_NAME,
        version = AreaEffectMod.MOD_VERSION,
        acceptedMinecraftVersions = "[1.12.2]"
)
public class AreaEffectMod {

    public static final String MOD_ID = "areaeffect";
    public static final String MOD_NAME = "AreaEffect";
    public static final String MOD_VERSION = "1.4.0";

    @SidedProxy(
            clientSide = "org.soraworld.areaeffect.client.ClientProxy",
            serverSide = "org.soraworld.areaeffect.common.CommonProxy"
    )
    private static CommonProxy proxy;

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        proxy.initConfig(event.getSuggestedConfigurationFile());
        proxy.onPreInit(event);
    }

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        proxy.onInit(event);
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new AreaCommand(proxy, "areaeffect", "aef"));
        if (event.getServer().getEntityWorld() instanceof WorldServer) {
            WorldServer world = (WorldServer) event.getServer().getEntityWorld();
            // 1.12.2 无 World#getChunkSaveLocation，世界目录经存盘处理器取得（与 saveHandler 的 worldDirectory 同源）
            File store = new File(world.getSaveHandler().getWorldDirectory(), "areaeffect.dat");
            proxy.setStoreFile(store);
            proxy.load();
        }
    }

    /**
     * 关服前兜底落盘。区域改动平时由 {@code ServerTick} 合并写入（见
     * {@code CommonProxy#flushStore}），这里保证最后一次改动（最多晚一个 tick）
     * 不会随正常关服丢失。
     */
    @Mod.EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        proxy.flushStore();
    }
}
