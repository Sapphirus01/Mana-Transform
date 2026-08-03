package by.sapphirus.manatransform.mixin;

import by.sapphirus.manatransform.service.ManaFallbackService;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MagicManager.class, remap = false)
public abstract class IronsSpellbooksMagicManagerMixin {
    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void manaTransform$prepareContinuousSpellMana(Level level, CallbackInfo callback) {
        for (var player : level.players()) {
            if (player instanceof ServerPlayer serverPlayer) {
                ManaFallbackService.prepareIronContinuousSpellMana(serverPlayer);
            }
        }
    }
}
