package com.jovinull.omnitweaks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

import com.jovinull.omnitweaks.commands.BaseCommand;
import com.jovinull.omnitweaks.core.ModuleManager;
import com.jovinull.omnitweaks.modules.omnidrill.OmniDrillModule;
import com.jovinull.omnitweaks.modules.omniplanter.OmniPlanterModule;
import com.jovinull.omnitweaks.modules.quickdump.QuickDumpModule;
import com.jovinull.omnitweaks.modules.treecapitator.TreeCapitatorModule;

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
        TreeCapitatorModule.register();
        OmniDrillModule.register();
        QuickDumpModule.register();
        OmniPlanterModule.register();

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
