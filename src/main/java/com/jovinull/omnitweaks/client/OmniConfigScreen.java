package com.jovinull.omnitweaks.client;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.jovinull.omnitweaks.config.OmniConfig;
import com.jovinull.omnitweaks.network.ConfigSyncPayload;

/**
 * Tela de configuração do OmniTweaks usando widgets vanilla do Minecraft.
 *
 * <p>Exibe toggles para cada módulo e campos numéricos para configurações
 * avançadas (área do Drill, altura mínima do Leveler). Ao salvar, persiste
 * no disco e envia {@link ConfigSyncPayload} ao servidor.</p>
 */
public class OmniConfigScreen extends Screen {

    private static final int BTN_W = 200;
    private static final int BTN_H = 20;
    private static final int GAP = 24;

    private final Screen parent;
    private final OmniConfig config;
    private EditBox levelerMinYField;

    public OmniConfigScreen(Screen parent, OmniConfig config) {
        super(Component.literal("OmniTweaks — Configurações"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        int col1 = width / 2 - BTN_W - 5;
        int col2 = width / 2 + 5;

        // ── Módulos (duas colunas) ──────────────────────────────────────
        int y = 40;

        addToggle(col1, y, "AutoShulker", config.autoShulker, v -> config.autoShulker = v);
        addToggle(col2, y, "OmniLeveler", config.leveler, v -> config.leveler = v);
        y += GAP;

        addToggle(col1, y, "TreeCapitator", config.treeCapitator, v -> config.treeCapitator = v);
        addToggle(col2, y, "Queda Rápida de Folhas", config.fastDecay, v -> config.fastDecay = v);
        y += GAP;

        addToggle(col1, y, "OmniDrill", config.drill, v -> config.drill = v);
        addToggle(col2, y, "OmniMagnet", config.magnet, v -> config.magnet = v);
        y += GAP;

        addToggle(col1, y, "QuickDump", config.quickDump, v -> config.quickDump = v);
        addToggle(col2, y, "OmniSaver", config.saver, v -> config.saver = v);
        y += GAP;

        addToggle(col1, y, "OmniPlanter", config.planter, v -> config.planter = v);

        // ── Configurações Avançadas ─────────────────────────────────────
        y += GAP + 16;
        List<Integer> range1a8 = IntStream.rangeClosed(1, 8).boxed().toList();

        addIntCycle(col1, y, "Drill — Largura", range1a8, config.drillWidth,
                v -> config.drillWidth = v);

        // Campo de texto para a altura mínima do Leveler
        levelerMinYField = new EditBox(font, col2, y, BTN_W, BTN_H,
                Component.literal("Min Y"));
        levelerMinYField.setValue(String.valueOf(config.levelerMinY));
        levelerMinYField.setHint(
                Component.literal("ex: 64").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(levelerMinYField);
        y += GAP;

        addIntCycle(col1, y, "Drill — Altura", range1a8, config.drillHeight,
                v -> config.drillHeight = v);
        y += GAP;

        addIntCycle(col1, y, "Drill — Profundidade", range1a8, config.drillDepth,
                v -> config.drillDepth = v);

        // ── Salvar ──────────────────────────────────────────────────────
        addRenderableWidget(Button.builder(
                        Component.literal("Salvar e Fechar"), button -> onSave())
                .bounds(width / 2 - 100, height - 28, 200, 20)
                .build());
    }

    private void addToggle(int x, int y, String label, boolean initial,
                            Consumer<Boolean> setter) {
        addRenderableWidget(CycleButton.onOffBuilder(initial)
                .create(x, y, BTN_W, BTN_H, Component.literal(label),
                        (button, value) -> setter.accept(value)));
    }

    private void addIntCycle(int x, int y, String label, List<Integer> values,
                              int initial, Consumer<Integer> setter) {
        addRenderableWidget(CycleButton.<Integer>builder(
                        v -> Component.literal(String.valueOf(v)), initial)
                .withValues(values)
                .create(x, y, BTN_W, BTN_H, Component.literal(label),
                        (button, value) -> setter.accept(value)));
    }

    private void onSave() {
        try {
            config.levelerMinY = Integer.parseInt(levelerMinYField.getValue());
        } catch (NumberFormatException ignored) {
            // Mantém o valor anterior se o campo estiver vazio ou inválido
        }

        config.save();

        ClientPlayNetworking.send(new ConfigSyncPayload(
                config.autoShulker, config.treeCapitator, config.drill,
                config.quickDump, config.planter, config.leveler,
                config.fastDecay, config.magnet, config.saver,
                config.drillWidth, config.drillHeight, config.drillDepth,
                config.levelerMinY));

        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX,
                                    int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // Título centralizado
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFF);

        // Subtítulo: Módulos
        graphics.text(font,
                Component.literal("Módulos").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                width / 2 - BTN_W - 5, 28, 0xFFFFFF);

        // Subtítulo: Configurações Avançadas
        int advY = 40 + GAP * 4 + GAP + 4;
        graphics.text(font,
                Component.literal("Configurações Avançadas")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                width / 2 - BTN_W - 5, advY, 0xFFFFFF);

        // Label para o campo Min Y do Leveler
        int fieldY = 40 + GAP * 5 + 16;
        graphics.text(font,
                Component.literal("Leveler — Altura Mínima (Y)")
                        .withStyle(ChatFormatting.WHITE),
                width / 2 + 5, fieldY - 10, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
