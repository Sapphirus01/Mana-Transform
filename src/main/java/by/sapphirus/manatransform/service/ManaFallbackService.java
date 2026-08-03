package by.sapphirus.manatransform.service;

import by.sapphirus.manatransform.ManaTransform;
import by.sapphirus.manatransform.config.ConversionSettings;
import by.sapphirus.manatransform.integration.DragonSurvivalManaAdapter;
import by.sapphirus.manatransform.integration.IronsSpellbooksManaAdapter;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class ManaFallbackService {
    // Sun Breath is the most expensive built-in channeled ability at 0.04 mana/tick.
    // Refill before the client reaches zero and keep two seconds buffered. Waiting until the
    // current tick actually fails creates a client/server race in which the client can send a
    // stop-casting packet while the server is converting the next batch of Iron's mana.
    private static final float MAX_DRAGON_CONTINUOUS_MANA_COST = 0.04F;
    private static final float DRAGON_CONTINUOUS_REFILL_THRESHOLD =
            MAX_DRAGON_CONTINUOUS_MANA_COST * 4.0F;
    private static final float DRAGON_CONTINUOUS_MANA_BUFFER =
            MAX_DRAGON_CONTINUOUS_MANA_COST * 40.0F;
    // Iron's largest default continuous pulse is 25, and MagicManager reserves two pulses.
    private static final float IRON_CONTINUOUS_MANA_BUFFER = 50.0F;

    private static final float EPSILON = 0.0001F;
    private static final ThreadLocal<Boolean> BYPASS_DRAGON_CONSUME_HOOK =
            ThreadLocal.withInitial(() -> false);

    private ManaFallbackService() {}

    public static float ironManaForSpellCheck(Player player, float ironMana) {
        if (!ConversionSettings.enabled(player)
                || !DragonSurvivalManaAdapter.isDragon(player)
                || DragonSurvivalManaAdapter.hasFreeMana(player)) {
            return ironMana;
        }

        return ironMana
                + DragonSurvivalManaAdapter.getCurrentMana(player)
                        * ConversionSettings.ironManaPerDragonMana(player);
    }

    public static boolean canSupplementDragonSpell(Player player, float dragonManaCost) {
        if (!ConversionSettings.enabled(player)) {
            return false;
        }

        // DragonStateProvider can briefly lag behind the ability and mana attachments on the
        // logical client. Let the client use its synchronized pools for prediction; the server
        // remains authoritative and still requires the player to actually be a dragon.
        if (!player.level().isClientSide() && !DragonSurvivalManaAdapter.isDragon(player)) {
            return false;
        }

        float dragonMana = DragonSurvivalManaAdapter.getCurrentMana(player);
        float availableIronMana = IronsSpellbooksManaAdapter.getCurrentMana(player);
        float experienceMana = DragonSurvivalManaAdapter.getExperienceMana(player);
        float ratio = ConversionSettings.ironManaPerDragonMana(player);
        float totalDragonEquivalent =
                dragonMana + availableIronMana / ratio + experienceMana;
        return totalDragonEquivalent + EPSILON >= dragonManaCost;
    }

    public static void prepareDragonMana(Player player, float dragonManaCost) {
        if (ConversionSettings.enabled(player) && player instanceof ServerPlayer serverPlayer) {
            boolean continuousTick = dragonManaCost > 0.0F
                    && dragonManaCost <= MAX_DRAGON_CONTINUOUS_MANA_COST + EPSILON;
            float refillThreshold = continuousTick
                    ? DRAGON_CONTINUOUS_REFILL_THRESHOLD
                    : dragonManaCost;
            float targetDragonMana = continuousTick
                    ? DRAGON_CONTINUOUS_MANA_BUFFER
                    : dragonManaCost;
            transferIronToDragon(serverPlayer, refillThreshold, targetDragonMana);
        }
    }

    public static boolean isBypassingDragonConsumeHook() {
        return BYPASS_DRAGON_CONSUME_HOOK.get();
    }

    public static boolean payDragonSpellCost(Player player, float dragonManaCost) {
        if (!ConversionSettings.enabled(player)
                || dragonManaCost <= 0.0F
                || DragonSurvivalManaAdapter.hasFreeMana(player)) {
            return false;
        }

        if (!player.level().isClientSide() && !DragonSurvivalManaAdapter.isDragon(player)) {
            return false;
        }

        float dragonMana = DragonSurvivalManaAdapter.getCurrentMana(player);
        if (dragonMana >= dragonManaCost) {
            return false;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            // The server performs and synchronizes the real conversion. Cancel only the client-side
            // prediction so Dragon Survival cannot locally spend experience before that sync arrives.
            return IronsSpellbooksManaAdapter.getCurrentMana(player) > EPSILON;
        }

        transferIronToDragon(serverPlayer, dragonManaCost, dragonManaCost);
        dragonMana = DragonSurvivalManaAdapter.getCurrentMana(serverPlayer);
        if (dragonMana >= dragonManaCost) {
            return false;
        }

        float remainingDragonCost = dragonManaCost - dragonMana;
        float availableIronMana = IronsSpellbooksManaAdapter.getCurrentMana(serverPlayer);
        float ratio = ConversionSettings.ironManaPerDragonMana(serverPlayer);
        float ironManaToSpend = Math.min(
                availableIronMana, remainingDragonCost * ratio);
        float experienceManaToSpend = Math.max(
                0.0F, remainingDragonCost - ironManaToSpend / ratio);
        if (DragonSurvivalManaAdapter.getExperienceMana(serverPlayer) + EPSILON
                < experienceManaToSpend) {
            return false;
        }

        float removedDragonMana = DragonSurvivalManaAdapter.removeMana(serverPlayer, dragonMana);
        float removedIronMana =
                IronsSpellbooksManaAdapter.removeMana(serverPlayer, ironManaToSpend);
        experienceManaToSpend = Math.max(
                0.0F,
                dragonManaCost
                        - removedDragonMana
                        - removedIronMana / ratio);
        if (experienceManaToSpend > EPSILON) {
            BYPASS_DRAGON_CONSUME_HOOK.set(true);
            try {
                DragonSurvivalManaAdapter.consumeExperienceMana(
                        serverPlayer, experienceManaToSpend);
            } finally {
                BYPASS_DRAGON_CONSUME_HOOK.remove();
            }
        }
        return true;
    }

    public static void prepareIronContinuousSpellMana(ServerPlayer player) {
        if (!ConversionSettings.enabled(player)
                || !DragonSurvivalManaAdapter.isDragon(player)
                || DragonSurvivalManaAdapter.hasFreeMana(player)) {
            return;
        }

        MagicData magicData = MagicData.getPlayerMagicData(player);
        if (!magicData.isCasting() || !magicData.getCastSource().consumesMana()) {
            return;
        }
        if (player.isCreative() && !ServerConfigs.CREATIVE_MANA_COST.get()) {
            return;
        }

        var spell = SpellRegistry.getSpell(magicData.getCastingSpellId());
        if (spell.getCastType() != CastType.CONTINUOUS) {
            return;
        }

        float pulseCost = Math.max(0, spell.getManaCost(magicData.getCastingSpellLevel()));
        float requiredIronMana = pulseCost * 2.0F;
        transferDragonToIron(player, requiredIronMana);
    }

    public static void payIronSpellCost(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !ConversionSettings.enabled(player)
                || !DragonSurvivalManaAdapter.isDragon(player)
                || DragonSurvivalManaAdapter.hasFreeMana(player)
                || !event.getCastSource().consumesMana()
                || event.getManaCost() <= 0) {
            return;
        }

        if (player.isCreative() && !ServerConfigs.CREATIVE_MANA_COST.get()) {
            return;
        }

        MagicData magicData = MagicData.getPlayerMagicData(player);
        if (magicData.getPlayerRecasts().hasRecastForSpell(event.getSpellId())) {
            return;
        }

        float ironMana = Math.max(0.0F, magicData.getMana());
        int totalIronCost = event.getManaCost();
        if (ironMana + EPSILON >= totalIronCost) {
            return;
        }

        int ironCost = Math.min(totalIronCost, Math.max(0, (int) Math.floor(ironMana)));
        float ratio = ConversionSettings.ironManaPerDragonMana(player);
        float dragonCost = (totalIronCost - ironCost) / ratio;
        if (DragonSurvivalManaAdapter.getCurrentMana(player) + EPSILON < dragonCost) {
            return;
        }

        DragonSurvivalManaAdapter.removeMana(player, dragonCost);
        event.setManaCost(ironCost);
    }

    private static void transferIronToDragon(
            ServerPlayer player, float refillThreshold, float targetDragonMana) {
        if (refillThreshold <= 0.0F
                || targetDragonMana <= 0.0F
                || !DragonSurvivalManaAdapter.isDragon(player)
                || DragonSurvivalManaAdapter.hasFreeMana(player)) {
            return;
        }

        float currentDragonMana = DragonSurvivalManaAdapter.getCurrentMana(player);
        if (currentDragonMana >= refillThreshold) {
            return;
        }

        targetDragonMana = Math.max(targetDragonMana, refillThreshold);
        float dragonManaRoom =
                Math.max(0.0F, DragonSurvivalManaAdapter.getMaxMana(player) - currentDragonMana);
        float availableIronMana = IronsSpellbooksManaAdapter.getCurrentMana(player);
        float ratio = ConversionSettings.ironManaPerDragonMana(player);
        float dragonManaToAdd = Math.min(
                Math.min(targetDragonMana - currentDragonMana, dragonManaRoom),
                availableIronMana / ratio);
        if (dragonManaToAdd <= EPSILON) {
            return;
        }

        float requestedIronMana = dragonManaToAdd * ratio;
        float removedIronMana = IronsSpellbooksManaAdapter.removeMana(player, requestedIronMana);
        float paidDragonMana = removedIronMana / ratio;
        float addedDragonMana = DragonSurvivalManaAdapter.addMana(player, paidDragonMana);
        if (addedDragonMana + EPSILON < paidDragonMana) {
            IronsSpellbooksManaAdapter.addMana(
                    player, (paidDragonMana - addedDragonMana) * ratio);
        }
        if (addedDragonMana > EPSILON) {
            ManaTransform.LOGGER.debug(
                    "Converted {} Iron's mana into {} Dragon Survival mana for {} at ratio {} "
                            + "(Dragon mana {} -> {})",
                    addedDragonMana * ratio,
                    addedDragonMana,
                    player.getGameProfile().getName(),
                    ratio,
                    currentDragonMana,
                    DragonSurvivalManaAdapter.getCurrentMana(player));
        }
    }

    private static void transferDragonToIron(ServerPlayer player, float requiredIronMana) {
        if (requiredIronMana <= 0.0F) {
            return;
        }

        float currentIronMana = IronsSpellbooksManaAdapter.getCurrentMana(player);
        if (currentIronMana + EPSILON >= requiredIronMana) {
            return;
        }

        float targetIronMana = Math.max(requiredIronMana, IRON_CONTINUOUS_MANA_BUFFER);
        float ironManaRoom =
                Math.max(0.0F, IronsSpellbooksManaAdapter.getMaxMana(player) - currentIronMana);
        float availableDragonMana = DragonSurvivalManaAdapter.getCurrentMana(player);
        float ratio = ConversionSettings.ironManaPerDragonMana(player);
        float ironManaToAdd = Math.min(
                Math.min(targetIronMana - currentIronMana, ironManaRoom),
                availableDragonMana * ratio);
        if (ironManaToAdd <= EPSILON) {
            return;
        }

        float requestedDragonMana = ironManaToAdd / ratio;
        float removedDragonMana = DragonSurvivalManaAdapter.removeMana(player, requestedDragonMana);
        float paidIronMana = removedDragonMana * ratio;
        float addedIronMana = IronsSpellbooksManaAdapter.addMana(player, paidIronMana);
        if (addedIronMana + EPSILON < paidIronMana) {
            DragonSurvivalManaAdapter.addMana(
                    player, (paidIronMana - addedIronMana) / ratio);
        }
        if (addedIronMana > EPSILON) {
            ManaTransform.LOGGER.debug(
                    "Converted {} Dragon Survival mana into {} Iron's mana for {}",
                    addedIronMana / ratio,
                    addedIronMana,
                    player.getGameProfile().getName());
        }
    }
}
