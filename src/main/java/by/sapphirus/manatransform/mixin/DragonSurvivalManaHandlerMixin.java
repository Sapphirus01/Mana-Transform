package by.sapphirus.manatransform.mixin;

import by.dragonsurvivalteam.dragonsurvival.common.handlers.magic.ManaHandler;
import by.sapphirus.manatransform.service.ManaFallbackService;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ManaHandler.class, remap = false)
public abstract class DragonSurvivalManaHandlerMixin {
    @Inject(method = "hasEnoughMana", at = @At("HEAD"), remap = false)
    private static void manaTransform$prepareDragonMana(
            Player player, float manaCost, CallbackInfoReturnable<Boolean> callback) {
        ManaFallbackService.prepareDragonMana(player, manaCost);
    }

    @Inject(method = "hasEnoughMana", at = @At("RETURN"), cancellable = true, remap = false)
    private static void manaTransform$includeIronMana(
            Player player, float manaCost, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue() && ManaFallbackService.canSupplementDragonSpell(player, manaCost)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "consumeMana", at = @At("HEAD"), cancellable = true, remap = false)
    private static void manaTransform$consumeIronMana(
            Player player, float manaCost, CallbackInfo callback) {
        if (!ManaFallbackService.isBypassingDragonConsumeHook()
                && ManaFallbackService.payDragonSpellCost(player, manaCost)) {
            callback.cancel();
        }
    }
}
