package by.sapphirus.manatransform.integration;

import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateProvider;
import by.dragonsurvivalteam.dragonsurvival.common.handlers.magic.ManaHandler;
import by.dragonsurvivalteam.dragonsurvival.network.syncing.SyncMana;
import by.dragonsurvivalteam.dragonsurvival.registry.DSEffects;
import by.dragonsurvivalteam.dragonsurvival.registry.attachments.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DragonSurvivalManaAdapter {
    private DragonSurvivalManaAdapter() {}

    public static boolean isDragon(Player player) {
        return DragonStateProvider.isDragon(player);
    }

    public static boolean hasFreeMana(Player player) {
        return player.hasInfiniteMaterials() || player.hasEffect(DSEffects.SOURCE_OF_MAGIC);
    }

    public static float getCurrentMana(Player player) {
        return Math.max(0.0F, ManaHandler.getCurrentMana(player));
    }

    public static float getMaxMana(Player player) {
        return Math.max(0.0F, ManaHandler.getMaxMana(player));
    }

    public static float getExperienceMana(Player player) {
        return Math.max(0.0F, ManaHandler.getManaFromExperience(player));
    }

    public static float addMana(ServerPlayer player, float requestedAmount) {
        float before = getCurrentMana(player);
        setMana(player, before + Math.max(0.0F, requestedAmount));
        float added = Math.max(0.0F, getCurrentMana(player) - before);
        return added;
    }

    public static float removeMana(ServerPlayer player, float requestedAmount) {
        float before = getCurrentMana(player);
        setMana(player, before - Math.max(0.0F, requestedAmount));
        float removed = Math.max(0.0F, before - getCurrentMana(player));
        return removed;
    }

    public static void consumeExperienceMana(ServerPlayer player, float amount) {
        ManaHandler.consumeMana(player, Math.max(0.0F, amount));
        sync(player);
    }

    private static void setMana(ServerPlayer player, float requestedMana) {
        float clampedMana = Math.max(0.0F, Math.min(getMaxMana(player), requestedMana));
        MagicData.getData(player).setCurrentMana(clampedMana);
        sync(player);
    }

    private static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncMana(getCurrentMana(player)));
    }
}
