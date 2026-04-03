package com.jovinull.omnitweaks;

import com.jovinull.omnitweaks.commands.BaseCommand;
import com.jovinull.omnitweaks.core.ModuleManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classe principal do mod OmniTweaks.
 *
 * Responsável por inicializar o gerenciador de módulos e registrar
 * os comandos do Brigadier. Funciona como ponto de entrada único
 * definido no fabric.mod.json.
 */
public class OmniTweaks implements ModInitializer {

    public static final String MOD_ID = "omnitweaks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ModuleManager moduleManager;

    @Override
    public void onInitialize() {
        LOGGER.info("[OmniTweaks] Inicializando mod...");

        moduleManager = new ModuleManager();

        BaseCommand.register(moduleManager);

        LOGGER.info("[OmniTweaks] Mod inicializado com {} módulo(s) disponível(is).",
                moduleManager.getAvailableModules().size());
    }

    /**
     * Retorna a instância global do gerenciador de módulos.
     * Utilizado pelos módulos e mixins para consultar o estado de ativação.
     */
    public static ModuleManager getModuleManager() {
        return moduleManager;
    }
}
