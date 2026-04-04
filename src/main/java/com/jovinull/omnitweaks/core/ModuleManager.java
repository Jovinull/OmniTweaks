package com.jovinull.omnitweaks.core;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerencia o estado de ativação dos módulos por jogador.
 *
 * Cada jogador (identificado por UUID) possui um conjunto de módulos ativos.
 * A estrutura é thread-safe via {@link ConcurrentHashMap} para uso seguro
 * no ambiente multithreaded do servidor Minecraft.
 *
 * Os estados são mantidos apenas em memória — ao reiniciar o servidor,
 * todos os módulos voltam ao estado padrão (desativado). Persistência
 * em disco pode ser adicionada futuramente.
 */
public class ModuleManager {

    /** Identificadores de todos os módulos registrados no sistema. */
    private static final Set<String> AVAILABLE_MODULES = Set.of(
            "autoshulker", "treecapitator", "omnidrill", "quickdump", "omniplanter", "omnileveler", "fastdecay",
            "omnimagnet", "omnisaver");

    /** Mapa de UUID do jogador para o conjunto de módulos ativados. */
    private final Map<UUID, Set<String>> playerModules = new ConcurrentHashMap<>();

    /** Configuração de área do OmniDrill por jogador [largura, altura, profundidade]. Padrão: 3x3x3. */
    private final Map<UUID, int[]> drillAreaConfig = new ConcurrentHashMap<>();

    /** Configuração de altura mínima (Y) do OmniLeveler por jogador. */
    private final Map<UUID, Integer> levelerMinY = new ConcurrentHashMap<>();

    /**
     * Alterna (toggle) o estado de um módulo para o jogador informado.
     *
     * @param playerId UUID do jogador
     * @param module   identificador do módulo (lowercase, ex: "autoshulker")
     * @return {@code true} se o módulo ficou <b>ativado</b>, {@code false} se ficou <b>desativado</b>
     * @throws IllegalArgumentException se o módulo não existe no registro
     */
    public boolean toggle(UUID playerId, String module) {
        if (!AVAILABLE_MODULES.contains(module)) {
            throw new IllegalArgumentException("Módulo desconhecido: " + module);
        }

        Set<String> modules = playerModules.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());

        // remove() retorna true se o elemento existia — nesse caso, foi desativado
        if (modules.remove(module)) {
            return false;
        }

        modules.add(module);
        return true;
    }

    /**
     * Verifica se um módulo está ativo para o jogador.
     *
     * @param playerId UUID do jogador
     * @param module   identificador do módulo
     * @return {@code true} se o módulo está ativo
     */
    public boolean isEnabled(UUID playerId, String module) {
        Set<String> modules = playerModules.get(playerId);
        return modules != null && modules.contains(module);
    }

    /**
     * Retorna o conjunto imutável de todos os módulos disponíveis no sistema.
     * Útil para tab-completion e listagem de ajuda.
     */
    public Set<String> getAvailableModules() {
        return AVAILABLE_MODULES;
    }

    /**
     * Ativa um módulo para o jogador sem alternar.
     * Diferente de {@link #toggle}, este método sempre ativa.
     *
     * @param playerId UUID do jogador
     * @param module   identificador do módulo
     */
    public void enable(UUID playerId, String module) {
        if (!AVAILABLE_MODULES.contains(module)) {
            throw new IllegalArgumentException("Módulo desconhecido: " + module);
        }
        Set<String> modules = playerModules.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
        modules.add(module);
    }

    /**
     * Desativa um módulo para o jogador sem alternar.
     * Diferente de {@link #toggle}, este método sempre desativa.
     *
     * @param playerId UUID do jogador
     * @param module   identificador do módulo
     */
    public void disable(UUID playerId, String module) {
        Set<String> modules = playerModules.get(playerId);
        if (modules != null) {
            modules.remove(module);
        }
    }

    /**
     * Define a configuração de área do OmniDrill para o jogador.
     *
     * @param playerId UUID do jogador
     * @param width    largura da área (1–8)
     * @param height   altura da área (1–8)
     * @param depth    profundidade da área (1–8)
     */
    public void setDrillArea(UUID playerId, int width, int height, int depth) {
        drillAreaConfig.put(playerId, new int[]{width, height, depth});
    }

    /**
     * Retorna a configuração de área do OmniDrill para o jogador.
     * Se não houver configuração salva, retorna o padrão 3x3x3.
     *
     * @param playerId UUID do jogador
     * @return array [largura, altura, profundidade]
     */
    public int[] getDrillArea(UUID playerId) {
        return drillAreaConfig.getOrDefault(playerId, new int[]{3, 3, 3});
    }

    /**
     * Ativa o OmniLeveler para o jogador com a altura mínima informada.
     * Desativa o OmniDrill automaticamente (exclusão mútua).
     *
     * @param playerId UUID do jogador
     * @param minY     coordenada Y mínima para quebra
     */
    public void enableLeveler(UUID playerId, int minY) {
        disable(playerId, "omnidrill");
        enable(playerId, "omnileveler");
        levelerMinY.put(playerId, minY);
    }

    /**
     * Verifica se o OmniLeveler está ativo para o jogador.
     */
    public boolean isLevelerActive(UUID playerId) {
        return isEnabled(playerId, "omnileveler");
    }

    /**
     * Retorna a altura mínima (Y) configurada para o OmniLeveler do jogador.
     * Se não houver configuração, retorna 0.
     */
    public int getLevelerMinY(UUID playerId) {
        return levelerMinY.getOrDefault(playerId, 0);
    }

    /**
     * Remove todos os dados de módulos e configurações de um jogador.
     * Pode ser chamado ao desconectar para liberar memória.
     *
     * @param playerId UUID do jogador
     */
    public void clearPlayer(UUID playerId) {
        playerModules.remove(playerId);
        drillAreaConfig.remove(playerId);
        levelerMinY.remove(playerId);
    }
}
