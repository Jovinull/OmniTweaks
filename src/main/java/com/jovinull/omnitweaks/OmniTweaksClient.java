package com.jovinull.omnitweaks;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.jovinull.omnitweaks.client.OmniConfigScreen;
import com.jovinull.omnitweaks.config.OmniConfig;
import com.jovinull.omnitweaks.network.ConfigSyncPayload;

/**
 * Entrypoint client-side do OmniTweaks.
 *
 * <p>Registra a tecla de atalho (O) para abrir a tela de configuração.
 * Ao entrar no mundo, carrega o arquivo local {@code omnitweaks.json}
 * e envia o {@link ConfigSyncPayload} para o servidor, garantindo
 * que o estado dos módulos seja restaurado ao logar.</p>
 */
public class OmniTweaksClient implements ClientModInitializer {

    private static KeyMapping configKey;
    private static OmniConfig config;

    @Override
    public void onInitializeClient() {
        config = OmniConfig.load();

        // Tecla O abre a tela de configuração (categoria "OmniTweaks")
        configKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.omnitweaks.config", GLFW.GLFW_KEY_O,
                        KeyMapping.Category.GAMEPLAY));

        // A cada tick do cliente, verifica se a tecla foi pressionada
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (configKey.consumeClick()) {
                client.setScreen(new OmniConfigScreen(client.screen, config));
            }
        });

        // Ao entrar no mundo, carrega config local e sincroniza com o servidor
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            config = OmniConfig.load();
            ClientPlayNetworking.send(createPayload());
        });
    }

    /**
     * Cria o pacote de rede a partir da configuração atual em memória.
     */
    private static ConfigSyncPayload createPayload() {
        return new ConfigSyncPayload(
                config.autoShulker, config.treeCapitator, config.drill,
                config.quickDump, config.planter, config.leveler,
                config.fastDecay, config.magnet, config.saver,
                config.drillWidth, config.drillHeight, config.drillDepth,
                config.levelerMinY);
    }
}
