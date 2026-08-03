package by.sapphirus.manatransform.network;

import by.sapphirus.manatransform.ManaTransform;
import by.sapphirus.manatransform.config.ConversionSettings;
import by.sapphirus.manatransform.config.ManaTransformConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ConversionConfigNetwork {
    private static final String NETWORK_VERSION = "1";

    private ConversionConfigNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(NETWORK_VERSION);
        registrar.playToServer(
                RequestSettingsPayload.TYPE,
                RequestSettingsPayload.STREAM_CODEC,
                ConversionConfigNetwork::handleRequest);
        registrar.playToServer(
                UpdateSettingsPayload.TYPE,
                UpdateSettingsPayload.STREAM_CODEC,
                ConversionConfigNetwork::handleUpdate);
        registrar.playToClient(
                SyncSettingsPayload.TYPE,
                SyncSettingsPayload.STREAM_CODEC,
                ConversionConfigNetwork::handleSync);
    }

    public static void sendCurrentSettings(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, currentSettings());
    }

    private static void handleRequest(RequestSettingsPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            sendCurrentSettings(player);
        }
    }

    private static void handleUpdate(UpdateSettingsPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        if (!player.hasPermissions(2)
                || !Float.isFinite(payload.ironManaPerDragonMana())
                || payload.ironManaPerDragonMana()
                        < ManaTransformConfig.MIN_IRON_MANA_PER_DRAGON_MANA
                || payload.ironManaPerDragonMana()
                        > ManaTransformConfig.MAX_IRON_MANA_PER_DRAGON_MANA) {
            sendCurrentSettings(player);
            return;
        }

        ManaTransformConfig.update(payload.enabled(), payload.ironManaPerDragonMana());
        PacketDistributor.sendToAllPlayers(currentSettings());
        ManaTransform.LOGGER.info(
                "{} changed mana conversion settings: enabled={}, 1 Dragon mana={} Iron mana",
                player.getGameProfile().getName(),
                payload.enabled(),
                payload.ironManaPerDragonMana());
    }

    private static void handleSync(SyncSettingsPayload payload, IPayloadContext context) {
        ConversionSettings.acceptServerValues(payload.enabled(), payload.ironManaPerDragonMana());
    }

    private static SyncSettingsPayload currentSettings() {
        return new SyncSettingsPayload(
                ManaTransformConfig.enabled(), ManaTransformConfig.ironManaPerDragonMana());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ManaTransform.MOD_ID, path);
    }

    public record RequestSettingsPayload() implements CustomPacketPayload {
        public static final Type<RequestSettingsPayload> TYPE =
                new Type<>(id("request_conversion_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestSettingsPayload> STREAM_CODEC =
                StreamCodec.unit(new RequestSettingsPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UpdateSettingsPayload(boolean enabled, float ironManaPerDragonMana)
            implements CustomPacketPayload {
        public static final Type<UpdateSettingsPayload> TYPE =
                new Type<>(id("update_conversion_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateSettingsPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeBoolean(payload.enabled());
                            buffer.writeFloat(payload.ironManaPerDragonMana());
                        },
                        buffer -> new UpdateSettingsPayload(
                                buffer.readBoolean(), buffer.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SyncSettingsPayload(boolean enabled, float ironManaPerDragonMana)
            implements CustomPacketPayload {
        public static final Type<SyncSettingsPayload> TYPE =
                new Type<>(id("sync_conversion_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncSettingsPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeBoolean(payload.enabled());
                            buffer.writeFloat(payload.ironManaPerDragonMana());
                        },
                        buffer -> new SyncSettingsPayload(
                                buffer.readBoolean(), buffer.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
