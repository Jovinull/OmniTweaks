package com.jovinull.omnitweaks.modules.treecapitator;

import com.jovinull.omnitweaks.OmniTweaks;
import com.jovinull.omnitweaks.core.ModuleManager;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Modulo TreeCapitator — ao quebrar um tronco com um machado, derruba
 * automaticamente todos os troncos conectados da arvore.
 *
 * <h3>Fluxo de execucao:</h3>
 * <ol>
 *   <li>Registra um callback em {@link PlayerBlockBreakEvents#AFTER} para
 *       interceptar a quebra de blocos apos ela acontecer.</li>
 *   <li>Verifica se o modulo esta ativo, se o bloco era um tronco e se o
 *       jogador segura um machado.</li>
 *   <li>Executa BFS (busca em largura) nos 26 vizinhos de cada tronco para
 *       encontrar todos os troncos conectados, respeitando o limite de seguranca.</li>
 *   <li>Quebra cada tronco encontrado programaticamente via
 *       {@link World#breakBlock(BlockPos, boolean, net.minecraft.entity.Entity)}
 *       para garantir que os drops corretos sejam gerados.</li>
 *   <li>Aplica dano de durabilidade ao machado proporcional a quantidade de
 *       blocos extras quebrados. O metodo nativo lida com o encantamento Inquebravel.</li>
 * </ol>
 *
 * <h3>Limite de seguranca:</h3>
 * <p>O BFS limita a busca a {@value #MAX_BLOCOS} blocos para evitar
 * travamentos em arvores gigantes ou estruturas massivas de troncos.</p>
 */
public final class TreeCapitatorModule {

    /** Limite maximo de blocos para evitar travamentos com arvores gigantes. */
    private static final int MAX_BLOCOS = 200;

    private TreeCapitatorModule() {}

    /**
     * Registra o callback de evento de quebra de bloco.
     * Deve ser chamado uma unica vez durante a inicializacao do mod.
     */
    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            // Apenas processa no servidor e para jogadores reais
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }

            handleBlockBreak(serverPlayer, (ServerWorld) world, pos, state);
        });
    }

    /**
     * Processa a quebra de um bloco de tronco, derrubando toda a arvore conectada.
     *
     * <p>O evento {@code AFTER} fornece o {@link BlockState} original do bloco
     * que ja foi quebrado (agora e ar). A BFS parte dos vizinhos dessa posicao
     * para encontrar os troncos restantes.</p>
     *
     * @param player    o jogador servidor que quebrou o bloco
     * @param world     o mundo do servidor
     * @param brokenPos posicao do bloco original (ja e ar neste ponto)
     * @param brokenState estado do bloco antes de ser quebrado
     */
    private static void handleBlockBreak(ServerPlayerEntity player, ServerWorld world,
                                          BlockPos brokenPos, BlockState brokenState) {
        // Verifica se o modulo esta ativo para o jogador
        ModuleManager manager = OmniTweaks.getModuleManager();
        if (manager == null || !manager.isEnabled(player.getUuid(), "treecapitator")) {
            return;
        }

        // Verifica se o bloco quebrado era um tronco
        if (!brokenState.isIn(BlockTags.LOGS)) {
            return;
        }

        // Verifica se o jogador esta segurando um machado na mao principal
        ItemStack heldItem = player.getMainHandStack();
        if (!(heldItem.getItem() instanceof AxeItem)) {
            return;
        }

        // BFS para encontrar todos os troncos conectados
        List<BlockPos> troncosConectados = findConnectedLogs(world, brokenPos);

        if (troncosConectados.isEmpty()) {
            return;
        }

        // Quebra todos os troncos encontrados (gera drops normais)
        for (BlockPos logPos : troncosConectados) {
            world.breakBlock(logPos, true, player);
        }

        // Aplica dano de durabilidade ao machado (1 por bloco extra quebrado)
        // O metodo nativo lida com o encantamento Inquebravel automaticamente
        heldItem.damage(troncosConectados.size(), player, EquipmentSlot.MAINHAND);
    }

    /**
     * Executa BFS (busca em largura) para encontrar todos os blocos de tronco
     * conectados a posicao inicial.
     *
     * <p>Verifica os 26 blocos adjacentes (cubo 3x3x3 menos o centro) em cada
     * iteracao, permitindo detectar troncos nas diagonais. Utiliza um
     * {@link HashSet} para rastrear posicoes ja visitadas e evitar loops.</p>
     *
     * @param world    o mundo do servidor
     * @param startPos posicao do bloco quebrado originalmente (ja e ar neste ponto)
     * @return lista de posicoes de troncos conectados (sem incluir a posicao original)
     */
    private static List<BlockPos> findConnectedLogs(World world, BlockPos startPos) {
        List<BlockPos> resultado = new ArrayList<>();
        Set<BlockPos> visitados = new HashSet<>();
        Queue<BlockPos> fila = new LinkedList<>();

        // Marca a posicao original como visitada (ja foi quebrada pelo jogador)
        visitados.add(startPos);

        // Adiciona os 26 vizinhos da posicao original a fila de busca
        addNeighbors(startPos, visitados, fila);

        while (!fila.isEmpty() && resultado.size() < MAX_BLOCOS) {
            BlockPos atual = fila.poll();
            BlockState state = world.getBlockState(atual);

            // Verifica se o bloco atual e um tronco
            if (!state.isIn(BlockTags.LOGS)) {
                continue;
            }

            resultado.add(atual);

            // Adiciona vizinhos nao visitados a fila para continuar a busca
            addNeighbors(atual, visitados, fila);
        }

        return resultado;
    }

    /**
     * Adiciona todos os 26 vizinhos de uma posicao a fila de BFS,
     * desde que nao tenham sido visitados anteriormente.
     *
     * <p>Os 26 vizinhos correspondem ao cubo 3x3x3 ao redor do bloco central,
     * excluindo o proprio centro. Isso cobre adjacencias por face (6),
     * por aresta (12) e por vertice (8).</p>
     *
     * @param centro   posicao central cujos vizinhos serao adicionados
     * @param visitados conjunto de posicoes ja visitadas (atualizado in-place)
     * @param fila     fila de BFS (atualizada in-place)
     */
    private static void addNeighbors(BlockPos centro, Set<BlockPos> visitados, Queue<BlockPos> fila) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    // Pula o proprio centro
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }

                    BlockPos vizinho = centro.add(dx, dy, dz);

                    // add() retorna true se o elemento era novo no conjunto
                    if (visitados.add(vizinho)) {
                        fila.add(vizinho);
                    }
                }
            }
        }
    }
}
