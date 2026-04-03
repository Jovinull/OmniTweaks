package com.jovinull.omnitweaks.modules.omnidrill;

import java.util.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * destrói automaticamente blocos conectados do mesmo tipo em uma area
 * configuravel (largura x altura) no plano perpendicular a face minerada.
 *
 * <h3>Fluxo de execucao:</h3>
 * <ol>
 *   <li>Registra um callback em {@link PlayerBlockBreakEvents#BEFORE} para
 *       interceptar a quebra de blocos antes dela acontecer.</li>
 *   <li>Verifica se o modulo esta ativo, se o jogador segura uma ferramenta
 *       de mineracao e identifica o tipo de bloco.</li>
 *   <li>Determina a face sendo minerada via raycast do jogador para definir
 *       o plano de expansao (largura x altura).</li>
 *   <li>Executa BFS no plano perpendicular a face, buscando blocos conectados
 *       do mesmo tipo dentro da area configurada.</li>
 *   <li>Para cada bloco extra, verifica a durabilidade restante da ferramenta
 *       e a distancia do jogador antes de quebrar.</li>
 * </ol>
 *
 * <h3>Limites de seguranca:</h3>
 * <ul>
 *   <li>Area maxima: 8x8 (validada no comando).</li>
 *   <li>Distancia maxima do jogador: 6 blocos (euclidiana).</li>
 *   <li>Durabilidade minima: para antes de quebrar a ferramenta.</li>
 * </ul>
 */
public final class OmniDrillModule {

    /** Distancia maxima ao quadrado do jogador (6 blocos). */
    private static final double MAX_DISTANCIA_SQ = 6.0 * 6.0;

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
            // Sempre permite a quebra do bloco original
            return true;
        });
    }

    /**
     * Processa a quebra de um bloco, minerando blocos conectados do mesmo tipo
     * em uma area configuravel no plano perpendicular a face minerada.
     *
     * @param player    o jogador servidor que esta quebrando o bloco
     * @param world     o mundo do servidor
     * @param pos       posicao do bloco sendo quebrado (ainda intacto neste ponto)
     * @param state     estado do bloco sendo quebrado
     */
    private static void handleBlockBreak(ServerPlayer player, ServerLevel world,
                                          BlockPos pos, BlockState state) {
        // Verifica se o modulo esta ativo para o jogador
        ModuleManager manager = OmniTweaks.getModuleManager();
        if (manager == null || !manager.isEnabled(player.getUUID(), "omnidrill")) {
            return;
        }

        // Verifica se o jogador segura uma ferramenta de mineracao (possui o componente TOOL)
        ItemStack heldItem = player.getMainHandItem();
        if (!heldItem.has(DataComponents.TOOL)) {
            return;
        }

        // Determina a face sendo minerada via raycast
        HitResult hitResult = player.pick(6.0, 1.0f, false);
        if (!(hitResult instanceof BlockHitResult blockHitResult)
                || !blockHitResult.getBlockPos().equals(pos)) {
            return;
        }

        Direction face = blockHitResult.getDirection();
        Direction.Axis faceAxis = face.getAxis();

        // Define os eixos do plano perpendicular a face de mineracao
        Direction.Axis widthAxis;
        Direction.Axis heightAxis;
        switch (faceAxis) {
            case X -> { widthAxis = Direction.Axis.Z; heightAxis = Direction.Axis.Y; }
            case Z -> { widthAxis = Direction.Axis.X; heightAxis = Direction.Axis.Y; }
            default -> { widthAxis = Direction.Axis.X; heightAxis = Direction.Axis.Z; } // Y
        }

        // Obtem a configuracao de area do jogador (padrao 3x3)
        int[] area = manager.getDrillArea(player.getUUID());
        int width = area[0];
        int height = area[1];

        // Identifica o tipo exato de bloco a ser minerado
        Block targetBlock = state.getBlock();

        // BFS para encontrar blocos conectados do mesmo tipo no plano
        List<BlockPos> blocosConectados = findConnectedBlocks(
                player, world, pos, targetBlock, widthAxis, heightAxis, width, height);

        if (blocosConectados.isEmpty()) {
            return;
        }

        // Quebra cada bloco extra, verificando durabilidade antes de cada um
        for (BlockPos blockPos : blocosConectados) {
            // Verifica se a ferramenta esta prestes a quebrar (durabilidade <= 1)
            int durabilidadeRestante = heldItem.getMaxDamage() - heldItem.getDamageValue();
            if (durabilidadeRestante <= 1) {
                break; // Para imediatamente para preservar a ferramenta
            }

            // Quebra o bloco com drops normais
            world.destroyBlock(blockPos, true, player);

            // Aplica dano de durabilidade (1 por bloco extra)
            // O metodo nativo lida com o encantamento Inquebravel
            heldItem.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
    }

    /**
     * Executa BFS no plano perpendicular a face de mineracao para encontrar
     * blocos conectados do mesmo tipo dentro da area configurada.
     *
     * <p>A busca utiliza 4-conectividade no plano (cima, baixo, esquerda, direita
     * nos eixos de largura e altura). Blocos fora da area ou alem de 6 blocos
     * do jogador sao ignorados.</p>
     *
     * @param player      jogador (para calculo de distancia)
     * @param world       o mundo do servidor
     * @param center      posicao central da busca (bloco original)
     * @param targetBlock tipo de bloco a ser buscado
     * @param widthAxis   eixo de largura no plano
     * @param heightAxis  eixo de altura no plano
     * @param width       largura da area de mineracao
     * @param height      altura da area de mineracao
     * @return lista de posicoes conectadas (sem incluir a posicao central)
     */
    private static List<BlockPos> findConnectedBlocks(ServerPlayer player, Level world,
                                                       BlockPos center, Block targetBlock,
                                                       Direction.Axis widthAxis, Direction.Axis heightAxis,
                                                       int width, int height) {
        List<BlockPos> resultado = new ArrayList<>();
        Set<BlockPos> visitados = new HashSet<>();
        Queue<BlockPos> fila = new LinkedList<>();

        // Marca a posicao central como visitada (sera quebrada pelo evento vanilla)
        visitados.add(center);

        // Adiciona os 4 vizinhos do plano a fila inicial
        addPlaneNeighbors(center, center, widthAxis, heightAxis, width, height, visitados, fila);

        while (!fila.isEmpty()) {
            BlockPos atual = fila.poll();

            // Verifica distancia do jogador (maximo 6 blocos)
            double distSq = player.distanceToSqr(
                    atual.getX() + 0.5, atual.getY() + 0.5, atual.getZ() + 0.5);
            if (distSq > MAX_DISTANCIA_SQ) {
                continue;
            }

            // Verifica se o bloco e do mesmo tipo
            BlockState currentState = world.getBlockState(atual);
            if (!currentState.getBlock().equals(targetBlock)) {
                continue;
            }

            resultado.add(atual);

            // Expande a busca para os vizinhos no plano
            addPlaneNeighbors(center, atual, widthAxis, heightAxis, width, height, visitados, fila);
        }

        return resultado;
    }

    /**
     * Adiciona os 4 vizinhos no plano (largura e altura) a fila de BFS,
     * desde que estejam dentro da area configurada e nao tenham sido visitados.
     *
     * @param center     posicao central original (para calculo de limites)
     * @param current    posicao atual cujos vizinhos serao adicionados
     * @param widthAxis  eixo de largura
     * @param heightAxis eixo de altura
     * @param width      largura da area
     * @param height     altura da area
     * @param visitados  conjunto de posicoes ja visitadas
     * @param fila       fila de BFS
     */
    private static void addPlaneNeighbors(BlockPos center, BlockPos current,
                                           Direction.Axis widthAxis, Direction.Axis heightAxis,
                                           int width, int height,
                                           Set<BlockPos> visitados, Queue<BlockPos> fila) {
        // 4 direcoes no plano: +width, -width, +height, -height
        BlockPos[] vizinhos = {
                offsetOnAxis(current, widthAxis, 1),
                offsetOnAxis(current, widthAxis, -1),
                offsetOnAxis(current, heightAxis, 1),
                offsetOnAxis(current, heightAxis, -1)
        };

        for (BlockPos vizinho : vizinhos) {
            if (visitados.add(vizinho) && isWithinArea(center, vizinho, widthAxis, heightAxis, width, height)) {
                fila.add(vizinho);
            }
        }
    }

    /**
     * Verifica se uma posicao esta dentro da area definida por largura x altura,
     * centralizada na posicao central.
     *
     * <p>Para dimensoes impares (ex: 3), o centro fica exatamente no meio.
     * Para dimensoes pares (ex: 4), o centro fica levemente deslocado.</p>
     *
     * @param center     posicao central da area
     * @param pos        posicao a ser verificada
     * @param widthAxis  eixo de largura
     * @param heightAxis eixo de altura
     * @param width      largura da area
     * @param height     altura da area
     * @return {@code true} se a posicao esta dentro da area
     */
    private static boolean isWithinArea(BlockPos center, BlockPos pos,
                                         Direction.Axis widthAxis, Direction.Axis heightAxis,
                                         int width, int height) {
        int dw = getAxisComponent(pos, widthAxis) - getAxisComponent(center, widthAxis);
        int dh = getAxisComponent(pos, heightAxis) - getAxisComponent(center, heightAxis);

        int halfW = (width - 1) / 2;
        int halfH = (height - 1) / 2;

        return dw >= -halfW && dw <= (width - 1 - halfW)
                && dh >= -halfH && dh <= (height - 1 - halfH);
    }

    /**
     * Desloca uma posicao em um eixo especifico.
     *
     * @param pos    posicao original
     * @param axis   eixo do deslocamento (X, Y ou Z)
     * @param offset quantidade do deslocamento
     * @return nova posicao deslocada
     */
    private static BlockPos offsetOnAxis(BlockPos pos, Direction.Axis axis, int offset) {
        return switch (axis) {
            case X -> pos.offset(offset, 0, 0);
            case Y -> pos.offset(0, offset, 0);
            case Z -> pos.offset(0, 0, offset);
        };
    }

    /**
     * Retorna o componente de uma posicao no eixo especificado.
     *
     * @param pos  posicao
     * @param axis eixo (X, Y ou Z)
     * @return valor da coordenada no eixo
     */
    private static int getAxisComponent(BlockPos pos, Direction.Axis axis) {
        return switch (axis) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }
}
