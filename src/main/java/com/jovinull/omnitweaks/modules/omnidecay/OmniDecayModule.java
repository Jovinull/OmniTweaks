package com.jovinull.omnitweaks.modules.omnidecay;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import com.jovinull.omnitweaks.OmniTweaks;
import com.jovinull.omnitweaks.core.ModuleManager;

/**
 * Módulo OmniDecay (Queda Rápida de Folhas) — ao quebrar um tronco ou folha,
 * agenda ticks para todas as folhas órfãs (distance == 7, não persistentes)
 * em um raio de 7 blocos, fazendo o motor do Minecraft processar o decay
 * naturalmente no próximo tick em vez de esperar pelo random tick.
 *
 * <p>Complementa o TreeCapitator: quando uma árvore inteira é derrubada,
 * as folhas desaparecem quase instantaneamente.</p>
 */
public final class OmniDecayModule {

    private static final int SCAN_RADIUS = 7;
    private static final int DECAY_DISTANCE = 7;
    private static final int TICK_DELAY = 1;

    private OmniDecayModule() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            ModuleManager manager = OmniTweaks.getModuleManager();
            if (manager == null || !manager.isEnabled(serverPlayer.getUUID(), "fastdecay")) {
                return;
            }

            // Só aciona ao quebrar troncos ou folhas
            if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) {
                return;
            }

            scheduleOrphanedLeaves((ServerLevel) world, pos);
        });
    }

    /**
     * Escaneia um cubo de raio {@value SCAN_RADIUS} ao redor da posição
     * e agenda um tick para cada folha órfã encontrada.
     * Folhas persistentes (colocadas por jogadores) são ignoradas.
     */
    private static void scheduleOrphanedLeaves(ServerLevel world, BlockPos center) {
        Set<BlockPos> agendados = new HashSet<>();

        BlockPos.betweenClosed(
                center.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS),
                center.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS)
        ).forEach(pos -> {
            BlockPos imutavel = pos.immutable();
            if (agendados.contains(imutavel)) {
                return;
            }

            BlockState leafState = world.getBlockState(imutavel);
            if (!leafState.is(BlockTags.LEAVES)) {
                return;
            }

            // Folha colocada por jogador — não destruir
            if (leafState.getValue(LeavesBlock.PERSISTENT)) {
                return;
            }

            // distance == DECAY_DISTANCE indica folha sem tronco conectado
            if (leafState.getValue(LeavesBlock.DISTANCE) >= DECAY_DISTANCE) {
                Block block = leafState.getBlock();
                world.scheduleTick(imutavel, block, TICK_DELAY);
                agendados.add(imutavel);
            }
        });
    }
}
