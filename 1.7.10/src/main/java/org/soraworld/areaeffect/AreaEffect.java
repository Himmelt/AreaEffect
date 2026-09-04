package org.soraworld.areaeffect;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.config.Configuration;
import org.soraworld.areaeffect.command.AreaCommand;
import org.soraworld.areaeffect.proxy.CommonProxy;

import java.io.File;

@Mod(
        modid = AreaEffect.MOD_ID,
        name = AreaEffect.MOD_NAME,
        version = AreaEffect.MOD_VERSION,
        acceptedMinecraftVersions = "[1.7.10]"
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
        event.registerServerCommand(new AreaCommand(proxy, "areaeffect"));
        if (event.getServer().getEntityWorld() instanceof WorldServer) {
            WorldServer world = (WorldServer) event.getServer().getEntityWorld();
            File conf = new File(world.getChunkSaveLocation(), MOD_ID + ".cfg");
            proxy.config = new Configuration(conf);
            proxy.load();
        }
    }
}
