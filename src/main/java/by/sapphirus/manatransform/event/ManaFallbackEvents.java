package by.sapphirus.manatransform.event;

import by.sapphirus.manatransform.ManaTransform;
import by.sapphirus.manatransform.service.ManaFallbackService;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = ManaTransform.MOD_ID)
public final class ManaFallbackEvents {
    private ManaFallbackEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIronSpellCast(SpellOnCastEvent event) {
        ManaFallbackService.payIronSpellCost(event);
    }
}
