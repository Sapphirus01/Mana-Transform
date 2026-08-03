package by.sapphirus.manatransform.mixin;

import by.sapphirus.manatransform.service.ManaFallbackService;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = AbstractSpell.class, remap = false)
public abstract class IronsSpellbooksAbstractSpellMixin {
    @Redirect(
            method = "canBeCastedBy",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/redspace/ironsspellbooks/api/magic/MagicData;getMana()F"),
            remap = false)
    private float manaTransform$includeDragonMana(
            MagicData magicData,
            int spellLevel,
            CastSource castSource,
            MagicData playerMagicData,
            Player player) {
        return ManaFallbackService.ironManaForSpellCheck(player, magicData.getMana());
    }
}
