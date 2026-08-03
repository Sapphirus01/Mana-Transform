package by.sapphirus.manatransform;

import by.sapphirus.manatransform.config.ManaTransformConfig;
import by.sapphirus.manatransform.network.ConversionConfigNetwork;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(ManaTransform.MOD_ID)
public final class ManaTransform {
    public static final String MOD_ID = "mana_transform";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ManaTransform(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, ManaTransformConfig.SPEC);
        modEventBus.addListener(ConversionConfigNetwork::register);
        LOGGER.info("Mana Transform is loading");
    }
}
