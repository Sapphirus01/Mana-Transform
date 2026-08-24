package by.sapphirus.manatransform.integration;

import by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateProvider;
import by.dragonsurvivalteam.dragonsurvival.common.handlers.magic.ManaHandler;
import by.dragonsurvivalteam.dragonsurvival.registry.DSEffects;
import by.dragonsurvivalteam.dragonsurvival.registry.attachments.MagicData;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DragonSurvivalManaAdapter {
    private static final DragonSurvivalManaApi MANA_API = DragonSurvivalManaApi.resolve();

    private DragonSurvivalManaAdapter() {}

    public static boolean isDragon(Player player) {
        return DragonStateProvider.isDragon(player);
    }

    public static boolean hasFreeMana(Player player) {
        return player.hasInfiniteMaterials() || player.hasEffect(DSEffects.SOURCE_OF_MAGIC);
    }

    public static float getCurrentMana(Player player) {
        MagicData magicData = MagicData.getData(player);
        float availableMana = MANA_API.getAvailableMana(magicData);
        return Math.max(0.0F, Math.min(getMaxMana(player), availableMana));
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
        MagicData magicData = MagicData.getData(player);
        float clampedAvailableMana =
                Math.max(0.0F, Math.min(getMaxMana(player), requestedMana));

        // Dragon Survival 2.0.63+ keeps ability reservations inside currentMana but excludes
        // them from getAvailableMana(). Preserve that unavailable portion when changing the
        // castable pool so this addon cannot spend or erase another ability's reservation.
        float unavailableMana = MANA_API.hasAvailableManaGetter()
                ? Math.max(0.0F, magicData.getCurrentMana() - MANA_API.getAvailableMana(magicData))
                : 0.0F;
        MANA_API.setCurrentMana(magicData, player, unavailableMana + clampedAvailableMana);
        sync(player, magicData.getCurrentMana());
    }

    private static void sync(ServerPlayer player) {
        sync(player, MagicData.getData(player).getCurrentMana());
    }

    private static void sync(ServerPlayer player, float rawCurrentMana) {
        PacketDistributor.sendToPlayer(player, MANA_API.createSyncPayload(rawCurrentMana));
    }

    /**
     * Isolates the binary-compatible mana API differences between Dragon Survival 2.0.51-2.0.62
     * and 2.0.63-2.0.66. Stable APIs stay as ordinary Java calls; only the three changed members
     * are resolved once here so the same addon JAR can run against either line.
     */
    private record DragonSurvivalManaApi(
            Method availableManaGetter,
            Method currentManaSetter,
            boolean setterNeedsPlayer,
            Constructor<? extends CustomPacketPayload> syncManaConstructor,
            boolean syncNeedsFullFlag) {
        private static final String SYNC_MANA_CLASS_NAME =
                "by.dragonsurvivalteam.dragonsurvival.network.syncing.SyncMana";

        private static DragonSurvivalManaApi resolve() {
            Method availableManaGetter = findOptionalMethod(MagicData.class, "getAvailableMana");

            Method currentManaSetter;
            boolean setterNeedsPlayer;
            try {
                currentManaSetter =
                        MagicData.class.getMethod("setCurrentMana", Player.class, float.class);
                setterNeedsPlayer = true;
            } catch (NoSuchMethodException modernSetterMissing) {
                try {
                    currentManaSetter = MagicData.class.getMethod("setCurrentMana", float.class);
                    setterNeedsPlayer = false;
                } catch (NoSuchMethodException legacySetterMissing) {
                    legacySetterMissing.addSuppressed(modernSetterMissing);
                    throw incompatibleApi("Cannot find a supported MagicData#setCurrentMana", legacySetterMissing);
                }
            }

            try {
                Class<? extends CustomPacketPayload> syncManaClass = Class.forName(SYNC_MANA_CLASS_NAME)
                        .asSubclass(CustomPacketPayload.class);
                try {
                    return new DragonSurvivalManaApi(
                            availableManaGetter,
                            currentManaSetter,
                            setterNeedsPlayer,
                            syncManaClass.getConstructor(float.class, boolean.class),
                            true);
                } catch (NoSuchMethodException modernSyncMissing) {
                    try {
                        return new DragonSurvivalManaApi(
                                availableManaGetter,
                                currentManaSetter,
                                setterNeedsPlayer,
                                syncManaClass.getConstructor(float.class),
                                false);
                    } catch (NoSuchMethodException legacySyncMissing) {
                        legacySyncMissing.addSuppressed(modernSyncMissing);
                        throw incompatibleApi("Cannot find a supported SyncMana constructor", legacySyncMissing);
                    }
                }
            } catch (ClassNotFoundException | ClassCastException exception) {
                throw incompatibleApi("Cannot load Dragon Survival's SyncMana payload", exception);
            }
        }

        private boolean hasAvailableManaGetter() {
            return availableManaGetter != null;
        }

        private float getAvailableMana(MagicData magicData) {
            if (availableManaGetter == null) {
                return magicData.getCurrentMana();
            }

            try {
                return ((Number) availableManaGetter.invoke(magicData)).floatValue();
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw invocationFailure("read available Dragon Survival mana", exception);
            }
        }

        private void setCurrentMana(MagicData magicData, Player player, float rawCurrentMana) {
            try {
                if (setterNeedsPlayer) {
                    currentManaSetter.invoke(magicData, player, rawCurrentMana);
                } else {
                    currentManaSetter.invoke(magicData, rawCurrentMana);
                }
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw invocationFailure("write Dragon Survival mana", exception);
            }
        }

        private CustomPacketPayload createSyncPayload(float rawCurrentMana) {
            try {
                if (syncNeedsFullFlag) {
                    return syncManaConstructor.newInstance(rawCurrentMana, true);
                }
                return syncManaConstructor.newInstance(rawCurrentMana);
            } catch (ReflectiveOperationException exception) {
                throw invocationFailure("create Dragon Survival's mana sync payload", exception);
            }
        }

        private static Method findOptionalMethod(Class<?> owner, String methodName) {
            try {
                return owner.getMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }

        private static IllegalStateException incompatibleApi(String message, Exception exception) {
            return new IllegalStateException(
                    message + ". Install Dragon Survival 2.0.51 through 2.0.66.", exception);
        }

        private static IllegalStateException invocationFailure(
                String operation, ReflectiveOperationException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocationException
                    ? invocationException.getCause()
                    : exception;
            return new IllegalStateException(
                    "Failed to " + operation + " through the Dragon Survival compatibility layer",
                    cause);
        }
    }
}
