package org.soraworld.areaeffect;

import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.soraworld.areaeffect.command.AreaCommand;
import org.soraworld.areaeffect.proxy.CommonProxy;

import java.io.File;

@Mod(
        modid = AreaEffect.MOD_ID,
        name = AreaEffect.MOD_NAME,
        version = AreaEffect.MOD_VERSION,
        acceptedMinecraftVersions = "[1.8.9]"
)
public class AreaEffect {
    public static final String MOD_ID = "areaeffect";
    public static final String MOD_NAME = "AreaEffect";
    public static final String MOD_VERSION = "1.3.0";
    @SidedProxy(
            clientSide = "org.soraworld.areaeffect.proxy.ClientProxy",
            serverSide = "org.soraworld.areaeffect.proxy.CommonProxy"
    )
    private static CommonProxy proxy;

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        proxy.onPreInit(event);
    }

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        proxy.onInit(event);
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new AreaCommand(proxy, true, "areaeffect"));
        if (event.getServer().getEntityWorld() instanceof WorldServer) {
            WorldServer world = (WorldServer) event.getServer().getEntityWorld();
            File conf = new File(world.getChunkSaveLocation(), MOD_ID + ".cfg");
            proxy.config = new net.minecraftforge.common.config.Configuration(conf, MOD_VERSION);
            proxy.load();
        }
    }
}
