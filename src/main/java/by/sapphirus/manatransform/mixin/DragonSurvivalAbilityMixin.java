package by.sapphirus.manatransform.mixin;

import by.dragonsurvivalteam.dragonsurvival.registry.dragon.ability.DragonAbilityInstance;
import by.sapphirus.manatransform.ManaTransform;
import by.sapphirus.manatransform.config.ConversionSettings;
import by.sapphirus.manatransform.integration.DragonSurvivalManaAdapter;
import by.sapphirus.manatransform.integration.IronsSpellbooksManaAdapter;
import by.sapphirus.manatransform.service.ManaFallbackService;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DragonAbilityInstance.class, remap = false)
public abstract class DragonSurvivalAbilityMixin {
    @Inject(method = "hasEnoughMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void manaTransform$includeIronManaInAbilityCheck(
            Player player, CallbackInfoReturnable<Boolean> callback) {
        DragonAbilityInstance ability = (DragonAbilityInstance) (Object) this;
        float manaCost = ability.value().activation().getInitialManaCost(ability.level());
        if (ManaFallbackService.canSupplementDragonSpell(player, manaCost)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "handleNotEnoughMana", at = @At("HEAD"), remap = false)
    private void manaTransform$logRejectedAbility(Player player, CallbackInfo callback) {
        DragonAbilityInstance ability = (DragonAbilityInstance) (Object) this;
        float manaCost = ability.value().activation().getInitialManaCost(ability.level());
        ManaTransform.LOGGER.debug(
                "Dragon ability {} was rejected on {}: cost={}, dragonMana={}, ironMana={} ({} Dragon equivalent), xpMana={}",
                ability.location(),
                player.level().isClientSide() ? "client" : "server",
                manaCost,
                DragonSurvivalManaAdapter.getCurrentMana(player),
                IronsSpellbooksManaAdapter.getCurrentMana(player),
                IronsSpellbooksManaAdapter.getCurrentMana(player)
                        / ConversionSettings.ironManaPerDragonMana(player),
                DragonSurvivalManaAdapter.getExperienceMana(player));
    }
}
