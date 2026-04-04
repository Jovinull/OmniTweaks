package com.jovinull.omnitweaks.commands;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import com.jovinull.omnitweaks.core.ModuleManager;

/**
 * Registra o comando base {@code /omnitweaks} (alias {@code /ot})
 * e expõe cada módulo como um subcomando literal.
 *
 * Sintaxe: {@code /omnitweaks <modulo>}
 * Exemplo: {@code /ot autoshulker} — alterna o estado do AutoShulker.
 *
 * Para adicionar novos módulos, basta registrar um novo {@code .then(...)}
 * dentro de {@link #registerCommands} seguindo o mesmo padrão.
 */
public final class BaseCommand {

    private BaseCommand() {}

    /**
     * Registra os comandos via {@link CommandRegistrationCallback} do Fabric API.
     * Deve ser chamado uma única vez durante a inicialização do mod.
     *
     * @param moduleManager instância do gerenciador de módulos
     */
    public static void register(ModuleManager moduleManager) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerCommands(dispatcher, moduleManager));
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher,
                                          ModuleManager moduleManager) {
        // Comando principal: /omnitweaks
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(
                Commands.literal("omnitweaks")
                        // Subcomando: /omnitweaks autoshulker
                        .then(Commands.literal("autoshulker")
                                .executes(ctx -> toggleModule(ctx, moduleManager, "autoshulker")))
                        // Subcomando: /omnitweaks treecapitator
                        .then(Commands.literal("treecapitator")
                                .executes(ctx -> toggleModule(ctx, moduleManager, "treecapitator")))
                        // Subcomando: /omnitweaks quickdump
                        .then(Commands.literal("quickdump")
                                .executes(ctx -> toggleModule(ctx, moduleManager, "quickdump")))
                        // Subcomando: /omnitweaks planter
                        .then(Commands.literal("planter")
                                .executes(ctx -> toggleModule(ctx, moduleManager, "omniplanter")))
                        // Subcomando: /omnitweaks all — ativa ou desativa todos os módulos
                        .then(Commands.literal("all")
                                .executes(ctx -> toggleAll(ctx, moduleManager)))
                        // Subcomando: /omnitweaks drill [<largura> <altura> [<profundidade>]]
                        .then(Commands.literal("drill")
                                // Sem argumentos: alterna (toggle) ligado/desligado
                                .executes(ctx -> toggleModule(ctx, moduleManager, "omnidrill"))
                                // Com argumentos: define area e ativa (profundidade padrao 3)
                                .then(Commands.argument("largura", IntegerArgumentType.integer(1, 8))
                                        .then(Commands.argument("altura", IntegerArgumentType.integer(1, 8))
                                                .executes(ctx -> activateDrill(ctx, moduleManager, 3))
                                                .then(Commands.argument("profundidade", IntegerArgumentType.integer(1, 8))
                                                        .executes(ctx -> activateDrill(ctx, moduleManager,
                                                                IntegerArgumentType.getInteger(ctx, "profundidade")))))))
                        // Subcomando: /omnitweaks leveler <alturaY> | off
                        .then(Commands.literal("leveler")
                                .then(Commands.literal("off")
                                        .executes(ctx -> deactivateLeveler(ctx, moduleManager)))
                                .then(Commands.argument("alturaY", IntegerArgumentType.integer())
                                        .executes(ctx -> activateLeveler(ctx, moduleManager))))
        );

        // Alias: /ot redireciona para /omnitweaks (preserva subcomandos)
        dispatcher.register(Commands.literal("ot").redirect(root));
    }

    /**
     * Ativa todos os módulos se algum estiver inativo; desativa todos se todos estiverem ativos.
     *
     * @return 1 (sucesso) para o Brigadier
     */
    private static int toggleAll(CommandContext<CommandSourceStack> ctx,
                                  ModuleManager moduleManager) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        UUID uuid = player.getUUID();

        // OmniLeveler é exclusivo e manual — não participa do /ot all
        Set<String> modules = new HashSet<>(moduleManager.getAvailableModules());
        modules.remove("omnileveler");

        boolean allEnabled = modules.stream().allMatch(m -> moduleManager.isEnabled(uuid, m));

        if (allEnabled) {
            modules.forEach(m -> moduleManager.disable(uuid, m));
        } else {
            modules.forEach(m -> moduleManager.enable(uuid, m));
        }

        Component status = allEnabled
                ? Component.literal("desativados.").withStyle(ChatFormatting.RED)
                : Component.literal("ativados!").withStyle(ChatFormatting.GREEN);

        player.sendSystemMessage(Component.empty()
                .append(Component.literal("[OmniTweaks] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Todos os módulos ").withStyle(ChatFormatting.WHITE))
                .append(status));

        return 1;
    }

    /**
     * Alterna o estado de um módulo para o jogador que executou o comando
     * e envia uma mensagem de feedback visual no chat.
     *
     * @return 1 (sucesso) para o Brigadier
     */
    private static int toggleModule(CommandContext<CommandSourceStack> ctx,
                                     ModuleManager moduleManager,
                                     String module) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean enabled = moduleManager.toggle(player.getUUID(), module);

        // Exclusão mútua: drill ligado desativa leveler
        if (enabled && module.equals("omnidrill")) {
            moduleManager.disable(player.getUUID(), "omnileveler");
        }

        String displayName = formatModuleName(module);

        Component status = enabled
                ? Component.literal("ativado!").withStyle(ChatFormatting.GREEN)
                : Component.literal("desativado.").withStyle(ChatFormatting.RED);

        Component message = Component.empty()
                .append(Component.literal("[OmniTweaks] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(displayName + " ").withStyle(ChatFormatting.WHITE))
                .append(status);

        player.sendSystemMessage(message);
        return 1;
    }

    /**
     * Ativa o OmniDrill com a configuração de área especificada nos argumentos.
     * Sempre ativa o módulo (não alterna) e salva a nova configuração.
     *
     * @return 1 (sucesso) para o Brigadier
     */
    private static int activateDrill(CommandContext<CommandSourceStack> ctx,
                                      ModuleManager moduleManager,
                                      int depth) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int width = IntegerArgumentType.getInteger(ctx, "largura");
        int height = IntegerArgumentType.getInteger(ctx, "altura");

        moduleManager.setDrillArea(player.getUUID(), width, height, depth);
        moduleManager.enable(player.getUUID(), "omnidrill");
        moduleManager.disable(player.getUUID(), "omnileveler");

        Component message = Component.empty()
                .append(Component.literal("[OmniTweaks] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("OmniDrill ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("ativado: ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(width + "x" + height + "x" + depth).withStyle(ChatFormatting.AQUA));

        player.sendSystemMessage(message);
        return 1;
    }

    /**
     * Ativa o OmniLeveler com a altura mínima informada.
     * Desativa o OmniDrill automaticamente (exclusão mútua via ModuleManager).
     */
    private static int activateLeveler(CommandContext<CommandSourceStack> ctx,
                                        ModuleManager moduleManager) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int minY = IntegerArgumentType.getInteger(ctx, "alturaY");

        moduleManager.enableLeveler(player.getUUID(), minY);

        Component message = Component.empty()
                .append(Component.literal("[OmniTweaks] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("OmniLeveler ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("ativado! ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("Quebrando apenas acima da altura ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.valueOf(minY)).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(".").withStyle(ChatFormatting.WHITE));

        player.sendSystemMessage(message);
        return 1;
    }

    /**
     * Desativa o OmniLeveler para o jogador.
     */
    private static int deactivateLeveler(CommandContext<CommandSourceStack> ctx,
                                          ModuleManager moduleManager) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        moduleManager.disable(player.getUUID(), "omnileveler");

        Component message = Component.empty()
                .append(Component.literal("[OmniTweaks] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("OmniLeveler ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("desativado.").withStyle(ChatFormatting.RED));

        player.sendSystemMessage(message);
        return 1;
    }

    /**
     * Converte o identificador interno do módulo em nome legível para o jogador.
     */
    private static String formatModuleName(String module) {
        return switch (module) {
            case "autoshulker" -> "AutoShulker";
            case "treecapitator" -> "TreeCapitator";
            case "omnidrill" -> "OmniDrill";
            case "quickdump" -> "QuickDump";
            case "omniplanter" -> "OmniPlanter";
            case "omnileveler" -> "OmniLeveler";
            default -> module;
        };
    }
}
