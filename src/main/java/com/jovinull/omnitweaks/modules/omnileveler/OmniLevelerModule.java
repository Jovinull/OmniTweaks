package com.jovinull.omnitweaks.modules.omnileveler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import com.jovinull.omnitweaks.OmniTweaks;
import com.jovinull.omnitweaks.core.ModuleManager;

/**
 * Modulo OmniLeveler — ao quebrar um bloco com uma ferramenta, destrói
 * automaticamente todos os blocos conectados do mesmo tipo acima de uma
 * coordenada Y mínima, em um raio horizontal de até 32 blocos.
 *
 * <p>Limite de 800 blocos por execução para evitar sobrecarga no servidor.
 * Exclusão mútua com OmniDrill — ambos não podem estar ativos ao mesmo tempo.</p>
 */
public final class OmniLevelerModule {

    private static final int MAX_BLOCKS = 800;
    private static final double MAX_RADIUS_SQ = 32.0 * 32.0;

    private OmniLevelerModule() {}

    /**
     * Registra o callback de evento de quebra de bloco (BEFORE).
     * Deve ser chamado uma única vez durante a inicialização do mod.
     */
    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                handleBlockBreak(serverPlayer, (ServerLevel) world, pos, state);
            }
            return true;
        });
    }

    private static void handleBlockBreak(ServerPlayer player, ServerLevel world,
                                          BlockPos pos, BlockState state) {
        ModuleManager manager = OmniTweaks.getModuleManager();
        if (manager == null || !manager.isLevelerActive(player.getUUID())) {
            return;
        }

        // TreeCapitator tem prioridade para troncos
        if (state.is(BlockTags.LOGS) && manager.isEnabled(player.getUUID(), "treecapitator")) {
            return;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (!heldItem.has(DataComponents.TOOL)) {
            return;
        }

        int minY = manager.getLevelerMinY(player.getUUID());

        // Rejeita se o bloco original já está abaixo do minY
        if (pos.getY() < minY) {
            return;
        }

        Block targetBlock = state.getBlock();
        List<BlockPos> blocosConectados = findConnectedBlocks(world, pos, targetBlock, minY);

        if (blocosConectados.isEmpty()) {
            return;
        }

        for (BlockPos blockPos : blocosConectados) {
            int durabilidadeRestante = heldItem.getMaxDamage() - heldItem.getDamageValue();
            if (durabilidadeRestante <= 1) {
                break;
            }

            Block blockType = world.getBlockState(blockPos).getBlock();
            world.destroyBlock(blockPos, true, player);
            heldItem.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            player.awardStat(Stats.BLOCK_MINED.get(blockType));
        }
    }

    private static List<BlockPos> findConnectedBlocks(Level world, BlockPos start,
                                                       Block targetBlock, int minY) {
        List<BlockPos> resultado = new ArrayList<>();
        Set<BlockPos> visitados = new HashSet<>();
        Queue<BlockPos> fila = new LinkedList<>();

        visitados.add(start);

        for (Direction dir : Direction.values()) {
            BlockPos vizinho = start.relative(dir);
            if (vizinho.getY() >= minY && visitados.add(vizinho)) {
                fila.add(vizinho);
            }
        }

        while (!fila.isEmpty() && resultado.size() < MAX_BLOCKS) {
            BlockPos atual = fila.poll();

            if (start.distSqr(atual) > MAX_RADIUS_SQ) {
                continue;
            }

            BlockState currentState = world.getBlockState(atual);
            if (!currentState.getBlock().equals(targetBlock)) {
                continue;
            }

            resultado.add(atual);

            for (Direction dir : Direction.values()) {
                BlockPos vizinho = atual.relative(dir);
                if (vizinho.getY() >= minY && visitados.add(vizinho)) {
                    fila.add(vizinho);
                }
            }
        }

        return resultado;
    }
}
