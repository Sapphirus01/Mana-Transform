package by.sapphirus.manatransform.event;

import by.sapphirus.manatransform.ManaTransform;
import by.sapphirus.manatransform.network.ConversionConfigNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = ManaTransform.MOD_ID)
public final class ConversionConfigEvents {
    private ConversionConfigEvents() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ConversionConfigNetwork.sendCurrentSettings(player);
        }
    }
}
