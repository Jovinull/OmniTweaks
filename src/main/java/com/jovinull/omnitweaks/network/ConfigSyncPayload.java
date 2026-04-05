package com.jovinull.omnitweaks.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.jovinull.omnitweaks.OmniTweaks;

/**
 * Pacote de rede C2S (Client → Server) para sincronizar configurações.
 *
 * <p>Carrega todos os estados de módulos (booleans) e parâmetros
 * numéricos (área do Drill, altura do Leveler). Enviado pelo cliente
 * ao salvar a tela de configuração ou ao entrar no mundo.</p>
 *
 * <p>Usa a Custom Payload API moderna do Fabric/Minecraft,
 * registrado via {@code PayloadTypeRegistry.playC2S()}.</p>
 */
public record ConfigSyncPayload(
        boolean autoShulker, boolean treeCapitator, boolean drill,
        boolean quickDump, boolean planter, boolean leveler,
        boolean fastDecay, boolean magnet, boolean saver,
        int drillWidth, int drillHeight, int drillDepth, int levelerMinY
) implements CustomPacketPayload {

    /** Identificador do pacote: omnitweaks:config_sync */
    public static final CustomPacketPayload.Type<ConfigSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    OmniTweaks.MOD_ID, "config_sync"));

    /** Codec para serialização/desserialização do pacote no buffer de rede. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ConfigSyncPayload decode(RegistryFriendlyByteBuf buf) {
                    return new ConfigSyncPayload(
                            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                            buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, ConfigSyncPayload payload) {
                    buf.writeBoolean(payload.autoShulker());
                    buf.writeBoolean(payload.treeCapitator());
                    buf.writeBoolean(payload.drill());
                    buf.writeBoolean(payload.quickDump());
                    buf.writeBoolean(payload.planter());
                    buf.writeBoolean(payload.leveler());
                    buf.writeBoolean(payload.fastDecay());
                    buf.writeBoolean(payload.magnet());
                    buf.writeBoolean(payload.saver());
                    buf.writeInt(payload.drillWidth());
                    buf.writeInt(payload.drillHeight());
                    buf.writeInt(payload.drillDepth());
                    buf.writeInt(payload.levelerMinY());
                }
            };

    @Override
    public CustomPacketPayload.Type<ConfigSyncPayload> type() {
        return TYPE;
    }
}
