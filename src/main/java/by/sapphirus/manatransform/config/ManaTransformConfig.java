package by.sapphirus.manatransform.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ManaTransformConfig {
    public static final boolean DEFAULT_ENABLED = true;
    public static final double DEFAULT_IRON_MANA_PER_DRAGON_MANA = 50.0D;
    public static final double MIN_IRON_MANA_PER_DRAGON_MANA = 1.0D;
    public static final double MAX_IRON_MANA_PER_DRAGON_MANA = 1000.0D;

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.DoubleValue IRON_MANA_PER_DRAGON_MANA;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Mana Transform server settings").push("conversion");
        ENABLED = builder.comment("Allow Dragon Survival and Iron's Spells mana to pay for each other.")
                .translation("config.mana_transform.enabled")
                .define("enabled", DEFAULT_ENABLED);
        IRON_MANA_PER_DRAGON_MANA = builder.comment("How much Iron's Spells mana equals one Dragon Survival mana.")
                .translation("config.mana_transform.iron_mana_per_dragon_mana")
                .defineInRange(
                        "ironManaPerDragonMana",
                        DEFAULT_IRON_MANA_PER_DRAGON_MANA,
                        MIN_IRON_MANA_PER_DRAGON_MANA,
                        MAX_IRON_MANA_PER_DRAGON_MANA);
        builder.pop();
        SPEC = builder.build();
    }

    private ManaTransformConfig() {}

    public static boolean enabled() {
        return ENABLED.get();
    }

    public static float ironManaPerDragonMana() {
        return IRON_MANA_PER_DRAGON_MANA.get().floatValue();
    }

    public static void update(boolean enabled, float ironManaPerDragonMana) {
        float clampedRatio = Math.max(
                (float) MIN_IRON_MANA_PER_DRAGON_MANA,
                Math.min((float) MAX_IRON_MANA_PER_DRAGON_MANA, ironManaPerDragonMana));
        ENABLED.set(enabled);
        IRON_MANA_PER_DRAGON_MANA.set((double) clampedRatio);
        SPEC.save();
    }
}
