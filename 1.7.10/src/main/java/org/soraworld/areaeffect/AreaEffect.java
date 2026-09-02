package org.soraworld.areaeffect;

import net.minecraft.world.WorldServer;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import org.soraworld.areaeffect.command.AreaCommand;
import org.soraworld.areaeffect.proxy.CommonProxy;

import java.io.File;

@cpw.mods.fml.common.Mod(
        modid = AreaEffect.MOD_ID,
        name = AreaEffect.MOD_NAME,
        version = AreaEffect.MOD_VERSION,
        acceptedMinecraftVersions = "[1.7.10]"
)
public class AreaEffect {
    public static final String MOD_ID = "areaeffect";
    public static final String MOD_NAME = "AreaEffect";
    public static final String MOD_VERSION = "1.2.0";
    @cpw.mods.fml.common.SidedProxy(
            clientSide = "org.soraworld.areaeffect.proxy.ClientProxy",
            serverSide = "org.soraworld.areaeffect.proxy.CommonProxy"
    )
    private static CommonProxy proxy;

    @cpw.mods.fml.common.Mod.EventHandler
    public void onPreInit(cpw.mods.fml.common.event.FMLPreInitializationEvent event) {
        proxy.onPreInit(event);
    }

    @cpw.mods.fml.common.Mod.EventHandler
    public void onInit(cpw.mods.fml.common.event.FMLInitializationEvent event) {
        proxy.onInit(event);
    }

    @cpw.mods.fml.common.Mod.EventHandler
    public void onServerStarting(cpw.mods.fml.common.event.FMLServerStartingEvent event) {
        event.registerServerCommand(new AreaCommand(proxy, true, "light"));
        if (event.getServer().getEntityWorld() instanceof WorldServer) {
            WorldServer world = (WorldServer) event.getServer().getEntityWorld();
            File conf = new File(world.getChunkSaveLocation(), MOD_ID + ".cfg");
            proxy.config = new cpw.mods.fml.common.config.Configuration(conf, MOD_VERSION);
            proxy.load();
        }
    }
}
