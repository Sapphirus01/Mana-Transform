package by.sapphirus.manatransform.integration;

import by.sapphirus.manatransform.integration.client.IronsSpellbooksClientManaAdapter;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public final class IronsSpellbooksManaAdapter {
    private IronsSpellbooksManaAdapter() {}

    public static float getCurrentMana(Player player) {
        if (player.level().isClientSide()) {
            // Iron's Spells does not synchronize mana into the player's MagicData attachment on
            // the client. Its SyncManaPacket updates the separate ClientMagicData singleton used
            // by the HUD, so client-side spell prediction must read that same canonical mirror.
            return IronsSpellbooksClientManaAdapter.getCurrentMana();
        }

        return Math.max(0.0F, data(player).getMana());
    }

    public static float getMaxMana(Player player) {
        return Math.max(0.0F, (float) player.getAttributeValue(AttributeRegistry.MAX_MANA));
    }

    public static float addMana(ServerPlayer player, float requestedAmount) {
        MagicData magicData = data(player);
        float before = Math.max(0.0F, magicData.getMana());
        float maxMana = getMaxMana(player);
        float target = Math.min(maxMana, before + Math.max(0.0F, requestedAmount));
        magicData.setMana(target);
        float added = Math.max(0.0F, magicData.getMana() - before);
        if (added > 0.0F) {
            sync(player, magicData);
        }
        return added;
    }

    public static float removeMana(ServerPlayer player, float requestedAmount) {
        MagicData magicData = data(player);
        float before = Math.max(0.0F, magicData.getMana());
        magicData.setMana(Math.max(0.0F, before - Math.max(0.0F, requestedAmount)));
        float removed = Math.max(0.0F, before - magicData.getMana());
        if (removed > 0.0F) {
            sync(player, magicData);
        }
        return removed;
    }

    private static MagicData data(Player player) {
        return MagicData.getPlayerMagicData(player);
    }

    private static void sync(ServerPlayer player, MagicData magicData) {
        PacketDistributor.sendToPlayer(player, new SyncManaPacket(magicData));
    }
}
