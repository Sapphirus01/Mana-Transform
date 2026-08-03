package by.sapphirus.manatransform.config;

import net.minecraft.world.entity.player.Player;

/** Provides server-authoritative settings and the synchronized client prediction mirror. */
public final class ConversionSettings {
    private static volatile boolean clientEnabled = ManaTransformConfig.DEFAULT_ENABLED;
    private static volatile float clientRatio =
            (float) ManaTransformConfig.DEFAULT_IRON_MANA_PER_DRAGON_MANA;
    private static volatile boolean clientHasServerValues;
    private static volatile int clientRevision;

    private ConversionSettings() {}

    public static boolean enabled(Player player) {
        return player.level().isClientSide() ? clientEnabled : ManaTransformConfig.enabled();
    }

    public static float ironManaPerDragonMana(Player player) {
        return player.level().isClientSide()
                ? clientRatio
                : ManaTransformConfig.ironManaPerDragonMana();
    }

    public static void acceptServerValues(boolean enabled, float ratio) {
        clientEnabled = enabled;
        clientRatio = ratio;
        clientHasServerValues = true;
        clientRevision++;
    }

    public static boolean clientEnabled() {
        return clientEnabled;
    }

    public static float clientRatio() {
        return clientRatio;
    }

    public static boolean clientHasServerValues() {
        return clientHasServerValues;
    }

    public static int clientRevision() {
        return clientRevision;
    }
}
