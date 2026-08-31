package org.soraworld.areaeffect;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.soraworld.areaeffect.proxy.CommonProxy;

/**
 * @author Himmelt
 */
@Mod(AreaEffect.MOD_ID)
public final class AreaEffect {
    public static final String MOD_ID = "areaeffect";

    public AreaEffect() {
        CommonProxy proxy = new CommonProxy();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(proxy::onCommonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(proxy::onClientSetup);
    }
}
