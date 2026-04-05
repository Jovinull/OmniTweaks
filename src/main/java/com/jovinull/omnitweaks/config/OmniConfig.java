package com.jovinull.omnitweaks.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import com.jovinull.omnitweaks.OmniTweaks;

/**
 * Persistência de configuração do OmniTweaks via JSON.
 *
 * <p>Armazena o estado de cada módulo e parâmetros numéricos
 * (área do Drill, altura do Leveler) no arquivo {@code omnitweaks.json}
 * dentro da pasta de configurações do Fabric.</p>
 *
 * <p>Usa Gson para serialização/desserialização. O arquivo é
 * criado automaticamente na primeira vez que o jogador salva.</p>
 */
public class OmniConfig {

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("omnitweaks.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean autoShulker;
    public boolean treeCapitator;
    public boolean drill;
    public boolean quickDump;
    public boolean planter;
    public boolean leveler;
    public boolean fastDecay;
    public boolean magnet;
    public boolean saver;

    public int drillWidth = 3;
    public int drillHeight = 3;
    public int drillDepth = 3;
    public int levelerMinY = 0;

    /**
     * Carrega a configuração do disco. Se o arquivo não existir
     * ou houver erro de leitura, retorna valores padrão (tudo desativado).
     */
    public static OmniConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                OmniConfig config = GSON.fromJson(reader, OmniConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (IOException e) {
                OmniTweaks.LOGGER.error("[OmniTweaks] Erro ao carregar configuração", e);
            }
        }
        return new OmniConfig();
    }

    /**
     * Salva a configuração atual no disco em formato JSON legível.
     */
    public void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            OmniTweaks.LOGGER.error("[OmniTweaks] Erro ao salvar configuração", e);
        }
    }
}
