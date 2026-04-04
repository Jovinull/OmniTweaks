package com.jovinull.omnitweaks.modules.omnimagnet;

import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import com.jovinull.omnitweaks.OmniTweaks;
import com.jovinull.omnitweaks.core.ModuleManager;

/**
 * Módulo OmniMagnet — atrai itens caídos no chão em direção ao jogador
 * a cada tick do servidor, num raio de 5 blocos.
 *
 * <p>Usa {@link ServerTickEvents#END_SERVER_TICK} para iterar todos os
 * jogadores online com o módulo ativo. Para cada item encontrado no raio,
 * calcula o vetor unitário de direção (item → jogador) e aplica uma
 * velocidade suave de 0.45 blocos/tick.</p>
 */
public final class OmniMagnetModule {

    private static final double MAGNET_RADIUS = 5.0;
    private static final double MAGNET_SPEED = 0.45;

    private OmniMagnetModule() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(OmniMagnetModule::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        ModuleManager manager = OmniTweaks.getModuleManager();
        if (manager == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!manager.isEnabled(player.getUUID(), "omnimagnet")) {
                continue;
            }

            attractNearbyItems(player);
        }
    }

    private static void attractNearbyItems(ServerPlayer player) {
        ServerLevel world = (ServerLevel) player.level();

        // Caixa de colisão expandida em 5 blocos para buscar itens no raio
        AABB searchArea = player.getBoundingBox().inflate(MAGNET_RADIUS);

        // Busca todos os ItemEntity no raio que não estejam em pickup delay
        List<ItemEntity> items = world.getEntities(
                EntityType.ITEM, searchArea, item -> !item.hasPickUpDelay());

        // Ponto-alvo: centro do corpo do jogador (posição + 0.5Y)
        Vec3 target = player.position().add(0, 0.5, 0);

        for (ItemEntity item : items) {
            // Vetor direção: do item para o jogador, normalizado para vetor unitário
            Vec3 direction = target.subtract(item.position()).normalize();

            // Aplica velocidade suave na direção do jogador (0.45 blocos/tick)
            item.setDeltaMovement(direction.scale(MAGNET_SPEED));
        }
    }
}
