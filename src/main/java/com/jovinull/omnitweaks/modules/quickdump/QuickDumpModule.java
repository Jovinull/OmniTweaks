package com.jovinull.omnitweaks.modules.quickdump;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import com.jovinull.omnitweaks.OmniTweaks;
import com.jovinull.omnitweaks.core.ModuleManager;

/**
 * Módulo QuickDump — ao agachar e clicar com o botão direito em um container
 * segurando uma Shulker Box, despeja automaticamente seu conteúdo no container.
 *
 * <p>Suporta baús simples e duplos (via {@link ChestBlock#getContainer}).
 * Clique direito sem agachar abre o container normalmente (vanilla).</p>
 */
public final class QuickDumpModule {

    private static final int SHULKER_SLOTS = 27;

    private QuickDumpModule() {}

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()
                    || hand != InteractionHand.MAIN_HAND
                    || !player.isShiftKeyDown()
                    || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }

            ItemStack heldItem = player.getItemInHand(hand);
            if (!isShulkerBox(heldItem)) {
                return InteractionResult.PASS;
            }

            ModuleManager manager = OmniTweaks.getModuleManager();
            if (manager == null || !manager.isEnabled(serverPlayer.getUUID(), "quickdump")) {
                return InteractionResult.PASS;
            }

            Container targetContainer = resolveContainer(world, hitResult);
            if (targetContainer == null) {
                return InteractionResult.PASS;
            }

            int dumped = dumpIntoContainer(heldItem, targetContainer);

            // Força resync completo de todos os slots para corrigir a previsão
            // do cliente — sem isso, quando nada muda (baú cheio / shulker vazio)
            // o cliente "vê" a Shulker Box sendo colocada no chão e não corrige.
            serverPlayer.inventoryMenu.sendAllDataToRemote();

            Component message;
            if (dumped > 0) {
                message = Component.empty()
                        .append(Component.literal("[OmniTweaks] ").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(dumped + " item(ns)").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" descarregados no container.").withStyle(ChatFormatting.WHITE));
            } else {
                message = Component.empty()
                        .append(Component.literal("[OmniTweaks] ").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("Container cheio ou Shulker Box vazia.").withStyle(ChatFormatting.RED));
            }

            serverPlayer.sendSystemMessage(message);
            return InteractionResult.SUCCESS_SERVER;
        });
    }

    /**
     * Resolve o container no bloco clicado.
     * Para baús, usa {@link ChestBlock#getContainer} que trata baús duplos corretamente.
     * Para demais containers (barril, dispenser, etc.) usa o BlockEntity diretamente.
     */
    private static Container resolveContainer(Level world, BlockHitResult hitResult) {
        var pos = hitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);

        if (state.getBlock() instanceof ChestBlock chestBlock) {
            return ChestBlock.getContainer(chestBlock, state, world, pos, false);
        }

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof Container container) {
            return container;
        }

        return null;
    }

    /**
     * Move o máximo possível do conteúdo da Shulker Box para o container alvo.
     * Fase 1: mescla com stacks existentes do mesmo tipo.
     * Fase 2: preenche slots vazios com o restante.
     *
     * @return total de itens (não stacks) movidos
     */
    private static int dumpIntoContainer(ItemStack shulkerStack, Container target) {
        ItemContainerContents containerContents = shulkerStack.getOrDefault(
                DataComponents.CONTAINER, ItemContainerContents.EMPTY);

        NonNullList<ItemStack> contents = NonNullList.withSize(SHULKER_SLOTS, ItemStack.EMPTY);
        containerContents.copyInto(contents);

        int totalDumped = 0;
        int targetSize = target.getContainerSize();

        for (int i = 0; i < SHULKER_SLOTS; i++) {
            ItemStack slot = contents.get(i);
            if (slot.isEmpty()) continue;

            // Fase 1: mesclar com stacks existentes do mesmo tipo
            for (int j = 0; j < targetSize && !slot.isEmpty(); j++) {
                ItemStack targetSlot = target.getItem(j);
                if (targetSlot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, targetSlot)) {
                    continue;
                }
                int maxStack = Math.min(targetSlot.getMaxStackSize(), target.getMaxStackSize(targetSlot));
                int space = maxStack - targetSlot.getCount();
                if (space <= 0) continue;

                int transfer = Math.min(slot.getCount(), space);
                targetSlot.grow(transfer);
                slot.shrink(transfer);
                totalDumped += transfer;
                target.setChanged();
            }

            // Fase 2: inserir em slots vazios
            for (int j = 0; j < targetSize && !slot.isEmpty(); j++) {
                if (!target.getItem(j).isEmpty()) continue;
                if (!target.canPlaceItem(j, slot)) continue;

                int transfer = Math.min(slot.getCount(), Math.min(slot.getMaxStackSize(), target.getMaxStackSize(slot)));
                target.setItem(j, slot.copyWithCount(transfer));
                slot.shrink(transfer);
                totalDumped += transfer;
                target.setChanged();
            }

            if (slot.isEmpty()) {
                contents.set(i, ItemStack.EMPTY);
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
