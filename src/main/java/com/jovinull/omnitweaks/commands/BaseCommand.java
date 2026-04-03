package com.jovinull.omnitweaks.commands;

import com.jovinull.omnitweaks.core.ModuleManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher,
                                          ModuleManager moduleManager) {
        // Comando principal: /omnitweaks
        LiteralCommandNode<ServerCommandSource> root = dispatcher.register(
                CommandManager.literal("omnitweaks")
                        // Subcomando: /omnitweaks autoshulker
                        .then(CommandManager.literal("autoshulker")
                                .executes(ctx -> toggleModule(ctx, moduleManager, "autoshulker")))
                        // Subcomando: /omnitweaks treecapitator
                        .then(CommandManager.literal("treecapitator")
                                .executes(ctx -> toggleModule(ctx, moduleManager, "treecapitator")))
        );

        // Alias: /ot redireciona para /omnitweaks (preserva subcomandos)
        dispatcher.register(CommandManager.literal("ot").redirect(root));
    }

    /**
     * Alterna o estado de um módulo para o jogador que executou o comando
     * e envia uma mensagem de feedback visual no chat.
     *
     * @return 1 (sucesso) para o Brigadier
     */
    private static int toggleModule(CommandContext<ServerCommandSource> ctx,
                                     ModuleManager moduleManager,
                                     String module) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        boolean enabled = moduleManager.toggle(player.getUuid(), module);

        String displayName = formatModuleName(module);

        Text status = enabled
                ? Text.literal("ativado!").formatted(Formatting.GREEN)
                : Text.literal("desativado.").formatted(Formatting.RED);

        Text message = Text.empty()
                .append(Text.literal("[OmniTweaks] ").formatted(Formatting.GOLD))
                .append(Text.literal(displayName + " ").formatted(Formatting.WHITE))
                .append(status);

        player.sendMessage(message, false);
        return 1;
    }

    /**
     * Converte o identificador interno do módulo em nome legível para o jogador.
     */
    private static String formatModuleName(String module) {
        return switch (module) {
            case "autoshulker" -> "AutoShulker";
            case "treecapitator" -> "TreeCapitator";
            default -> module;
        };
    }
}
