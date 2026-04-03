package com.jovinull.omnitweaks.modules.omnidrill;

import java.util.*;

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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import com.jovinull.omnitweaks.OmniTweaks;
import com.jovinull.omnitweaks.core.ModuleManager;

/**
 * Modulo OmniDrill — ao quebrar um bloco com uma ferramenta de mineracao,
 * destrói automaticamente blocos conectados do mesmo tipo em um volume
 * configuravel (largura x altura x profundidade) a partir da face minerada.
 *
 * <p>Se o modulo TreeCapitator estiver ativo e o bloco for um tronco,
 * o OmniDrill cede a vez ao TreeCapitator.</p>
 */
public final class OmniDrillModule {

    private OmniDrillModule() {}

    /**
     * Registra o callback de evento de quebra de bloco (BEFORE).
     * Deve ser chamado uma unica vez durante a inicializacao do mod.
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
        if (manager == null || !manager.isEnabled(player.getUUID(), "omnidrill")) {
            return;
        }

        // TreeCapitator tem prioridade: deixa ele tratar troncos quando estiver ativo
        if (state.is(BlockTags.LOGS) && manager.isEnabled(player.getUUID(), "treecapitator")) {
            return;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (!heldItem.has(DataComponents.TOOL)) {
            return;
        }

        HitResult hitResult = player.pick(6.0, 1.0f, false);
        if (!(hitResult instanceof BlockHitResult blockHitResult)
                || !blockHitResult.getBlockPos().equals(pos)) {
            return;
        }

        Direction face = blockHitResult.getDirection();
        Direction.Axis faceAxis = face.getAxis();

        Direction.Axis widthAxis;
        Direction.Axis heightAxis;
        switch (faceAxis) {
            case X -> { widthAxis = Direction.Axis.Z; heightAxis = Direction.Axis.Y; }
            case Z -> { widthAxis = Direction.Axis.X; heightAxis = Direction.Axis.Y; }
            default -> { widthAxis = Direction.Axis.X; heightAxis = Direction.Axis.Z; } // Y
        }

        int[] area = manager.getDrillArea(player.getUUID());
        int width = area[0];
        int height = area[1];
        int depth = area[2];

        Block targetBlock = state.getBlock();

        List<BlockPos> blocosConectados = findConnectedBlocks(
                world, pos, targetBlock, widthAxis, heightAxis, width, height, depth, face);

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

    private static List<BlockPos> findConnectedBlocks(Level world,
                                                       BlockPos center, Block targetBlock,
                                                       Direction.Axis widthAxis, Direction.Axis heightAxis,
                                                       int width, int height, int depth, Direction face) {
        List<BlockPos> resultado = new ArrayList<>();
        Set<BlockPos> visitados = new HashSet<>();
        Queue<BlockPos> fila = new LinkedList<>();

        visitados.add(center);
        addVolumeNeighbors(center, center, widthAxis, heightAxis, width, height, depth, face, visitados, fila);

        while (!fila.isEmpty()) {
            BlockPos atual = fila.poll();

            BlockState currentState = world.getBlockState(atual);
            if (!currentState.getBlock().equals(targetBlock)) {
                continue;
            }

            resultado.add(atual);
            addVolumeNeighbors(center, atual, widthAxis, heightAxis, width, height, depth, face, visitados, fila);
        }

        return resultado;
    }

    private static void addVolumeNeighbors(BlockPos center, BlockPos current,
                                            Direction.Axis widthAxis, Direction.Axis heightAxis,
                                            int width, int height, int depth, Direction face,
                                            Set<BlockPos> visitados, Queue<BlockPos> fila) {
        BlockPos[] vizinhos = {
                offsetOnAxis(current, widthAxis, 1),
                offsetOnAxis(current, widthAxis, -1),
                offsetOnAxis(current, heightAxis, 1),
                offsetOnAxis(current, heightAxis, -1),
                current.relative(face.getOpposite()),
        };

        for (BlockPos vizinho : vizinhos) {
            if (visitados.add(vizinho)
                    && isWithinVolume(center, vizinho, widthAxis, heightAxis, width, height, depth, face)) {
                fila.add(vizinho);
            }
        }
    }

    private static boolean isWithinVolume(BlockPos center, BlockPos pos,
                                           Direction.Axis widthAxis, Direction.Axis heightAxis,
                                           int width, int height, int depth, Direction face) {
        int dw = getAxisComponent(pos, widthAxis) - getAxisComponent(center, widthAxis);
        int dh = getAxisComponent(pos, heightAxis) - getAxisComponent(center, heightAxis);
        int halfW = (width - 1) / 2;
        int halfH = (height - 1) / 2;
        if (dw < -halfW || dw > (width - 1 - halfW)) return false;
        if (dh < -halfH || dh > (height - 1 - halfH)) return false;

        // Profundidade: 0 = plano da face, [0, depth-1] = valido
        Direction intoWall = face.getOpposite();
        int dd = (pos.getX() - center.getX()) * intoWall.getStepX()
               + (pos.getY() - center.getY()) * intoWall.getStepY()
               + (pos.getZ() - center.getZ()) * intoWall.getStepZ();
        return dd >= 0 && dd < depth;
    }

    private static BlockPos offsetOnAxis(BlockPos pos, Direction.Axis axis, int offset) {
        return switch (axis) {
            case X -> pos.offset(offset, 0, 0);
            case Y -> pos.offset(0, offset, 0);
            case Z -> pos.offset(0, 0, offset);
        };
    }

    private static int getAxisComponent(BlockPos pos, Direction.Axis axis) {
        return switch (axis) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }
}
