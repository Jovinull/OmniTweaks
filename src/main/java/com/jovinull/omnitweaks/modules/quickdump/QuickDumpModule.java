package com.jovinull.omnitweaks.modules.quickdump;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import net.fabricmc.fabric.api.event.player.UseItemCallback;

import com.jovinull.omnitweaks.OmniTweaks;
import com.jovinull.omnitweaks.core.ModuleManager;

/**
 * Módulo QuickDump — ao clicar com o botão direito no ar segurando uma
 * Shulker Box, despeja automaticamente seu conteúdo no inventário do jogador.
 *
 * <p>Só intercepta cliques no ar (USE_ITEM). Cliques em blocos continuam
 * colocando a Shulker Box normalmente (sem conflito com vanilla).</p>
 */
public final class QuickDumpModule {

    private static final int SHULKER_SLOTS = 27;

    private QuickDumpModule() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack heldItem = player.getItemInHand(hand);

            if (world.isClientSide()
                    || hand != InteractionHand.MAIN_HAND
                    || !isShulkerBox(heldItem)
                    || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }

            ModuleManager manager = OmniTweaks.getModuleManager();
            if (manager == null || !manager.isEnabled(serverPlayer.getUUID(), "quickdump")) {
                return InteractionResult.PASS;
            }

            int dumped = dumpShulkerBox(serverPlayer, heldItem);

            Component message;
            if (dumped > 0) {
                message = Component.empty()
                        .append(Component.literal("[OmniTweaks] ").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("Descarregados ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(dumped + " item(ns)").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" da Shulker Box.").withStyle(ChatFormatting.WHITE));
            } else {
                message = Component.empty()
                        .append(Component.literal("[OmniTweaks] ").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("Shulker Box vazia ou inventário cheio.").withStyle(ChatFormatting.RED));
            }

            serverPlayer.sendSystemMessage(message);
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    /**
     * Move o máximo possível do conteúdo da Shulker Box para o inventário do jogador.
     *
     * @return total de itens (não stacks) movidos para o inventário
     */
    private static int dumpShulkerBox(ServerPlayer player, ItemStack shulkerStack) {
        ItemContainerContents container = shulkerStack.getOrDefault(
                DataComponents.CONTAINER, ItemContainerContents.EMPTY);

        NonNullList<ItemStack> contents = NonNullList.withSize(SHULKER_SLOTS, ItemStack.EMPTY);
        container.copyInto(contents);

        Inventory inventory = player.getInventory();
        int totalDumped = 0;

        for (int i = 0; i < SHULKER_SLOTS; i++) {
            ItemStack slot = contents.get(i);
            if (slot.isEmpty()) continue;

            int countBefore = slot.getCount();
            inventory.add(slot);
            int moved = countBefore - slot.getCount();

            if (moved > 0) {
                totalDumped += moved;
                if (slot.isEmpty()) {
                    contents.set(i, ItemStack.EMPTY);
                }
            }
        }

        if (totalDumped > 0) {
            shulkerStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        }

        return totalDumped;
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }
}
