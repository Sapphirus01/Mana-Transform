package by.sapphirus.manatransform.integration.client;

import io.redspace.ironsspellbooks.player.ClientMagicData;

/** Client-only access to the mana mirror maintained by Iron's Spells networking. */
public final class IronsSpellbooksClientManaAdapter {
    private IronsSpellbooksClientManaAdapter() {}

    public static float getCurrentMana() {
        return Math.max(0, ClientMagicData.getPlayerMana());
    }
}
