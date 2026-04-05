package com.jovinull.omnitweaks;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.jovinull.omnitweaks.core.ModuleManager;
import com.jovinull.omnitweaks.modules.omnidecay.OmniDecayModule;
import com.jovinull.omnitweaks.modules.omnidrill.OmniDrillModule;
import com.jovinull.omnitweaks.modules.omnileveler.OmniLevelerModule;
import com.jovinull.omnitweaks.modules.omnimagnet.OmniMagnetModule;
import com.jovinull.omnitweaks.modules.omniplanter.OmniPlanterModule;
import com.jovinull.omnitweaks.modules.omnisaver.OmniSaverModule;
import com.jovinull.omnitweaks.modules.quickdump.QuickDumpModule;
import com.jovinull.omnitweaks.modules.treecapitator.TreeCapitatorModule;
import com.jovinull.omnitweaks.network.ConfigSyncPayload;

/**
 * Classe principal do mod OmniTweaks.
 *
 * <p>Responsável por inicializar o gerenciador de módulos, registrar
 * os módulos e configurar o recebedor de pacotes de rede C2S
 * para sincronização de configurações dos jogadores.</p>
 */
public class OmniTweaks implements ModInitializer {

    public static final String MOD_ID = "omnitweaks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ModuleManager moduleManager;

    @Override
    public void onInitialize() {
        LOGGER.info("[OmniTweaks] Inicializando mod...");

        moduleManager = new ModuleManager();

        // Registra o tipo de pacote C2S para sincronização de configuração
        PayloadTypeRegistry.serverboundPlay().register(
                ConfigSyncPayload.TYPE, ConfigSyncPayload.STREAM_CODEC);

        // Recebedor server-side: aplica config do jogador no ModuleManager
        ServerPlayNetworking.registerGlobalReceiver(
                ConfigSyncPayload.TYPE, (payload, context) -> {
                    UUID uuid = context.player().getUUID();

                    // Módulos simples: ativa ou desativa conforme o pacote
                    setModuleState(uuid, "autoshulker", payload.autoShulker());
                    setModuleState(uuid, "treecapitator", payload.treeCapitator());
                    setModuleState(uuid, "quickdump", payload.quickDump());
                    setModuleState(uuid, "omniplanter", payload.planter());
                    setModuleState(uuid, "fastdecay", payload.fastDecay());
                    setModuleState(uuid, "omnimagnet", payload.magnet());
                    setModuleState(uuid, "omnisaver", payload.saver());

                    // Drill e Leveler: exclusão mútua (leveler tem prioridade)
                    if (payload.leveler()) {
                        moduleManager.enableLeveler(uuid, payload.levelerMinY());
                    } else if (payload.drill()) {
                        moduleManager.enable(uuid, "omnidrill");
                        moduleManager.disable(uuid, "omnileveler");
                    } else {
                        moduleManager.disable(uuid, "omnidrill");
                        moduleManager.disable(uuid, "omnileveler");
                    }

                    // Configuração de área do Drill (salva independente do toggle)
                    moduleManager.setDrillArea(uuid,
                            payload.drillWidth(), payload.drillHeight(), payload.drillDepth());

                    LOGGER.debug("[OmniTweaks] Config sincronizada para jogador {}", uuid);
                });

        // Registro dos módulos (event listeners)
        TreeCapitatorModule.register();
        OmniDecayModule.register();
        OmniDrillModule.register();
        OmniLevelerModule.register();
        OmniMagnetModule.register();
        OmniSaverModule.register();
        QuickDumpModule.register();
        OmniPlanterModule.register();

        LOGGER.info("[OmniTweaks] Mod inicializado com {} módulo(s) disponível(is).",
                moduleManager.getAvailableModules().size());
    }

    private static void setModuleState(UUID uuid, String module, boolean enabled) {
        if (enabled) {
            moduleManager.enable(uuid, module);
        } else {
            moduleManager.disable(uuid, module);
        }
    }

    /**
     * Retorna a instância global do gerenciador de módulos.
     * Utilizado pelos módulos e mixins para consultar o estado de ativação.
     */
    public static ModuleManager getModuleManager() {
        return moduleManager;
    }
}
