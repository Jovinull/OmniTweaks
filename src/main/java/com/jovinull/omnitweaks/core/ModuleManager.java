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
    private static final Set<String> AVAILABLE_MODULES = Set.of("autoshulker");

    /** Mapa de UUID do jogador para o conjunto de módulos ativados. */
    private final Map<UUID, Set<String>> playerModules = new ConcurrentHashMap<>();

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
     * Remove todos os dados de módulos de um jogador.
     * Pode ser chamado ao desconectar para liberar memória.
     *
     * @param playerId UUID do jogador
     */
    public void clearPlayer(UUID playerId) {
        playerModules.remove(playerId);
    }
}
