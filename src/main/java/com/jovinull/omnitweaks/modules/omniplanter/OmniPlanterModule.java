package com.jovinull.omnitweaks.modules.omniplanter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import com.jovinull.omnitweaks.OmniTweaks;
import com.jovinull.omnitweaks.core.ModuleManager;

/**
 * Módulo OmniPlanter — ao clicar com o botão direito em uma plantação madura,
 * colhe automaticamente e replanta a semente correspondente.
 *
 * <p>Suporta: {@link CropBlock} (trigo, cenoura, batata, beterraba),
 * {@link NetherWartBlock} (fungo do nether) e {@link CocoaBlock} (cacau).</p>
 *
 * <p>O jogador precisa ter ao menos 1 semente correspondente no inventário
 * (exceto em modo criativo). A semente é consumida antes da plantação e o
 * bloco quebrado dropa os itens normalmente.</p>
 */
public final class OmniPlanterModule {

    private OmniPlanterModule() {}

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            // Executa apenas no servidor, apenas pela mão principal
            if (world.isClientSide() || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }

            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }

            ModuleManager manager = OmniTweaks.getModuleManager();
            if (manager == null || !manager.isEnabled(serverPlayer.getUUID(), "omniplanter")) {
                return InteractionResult.PASS;
            }

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            // Verifica se é uma plantação madura suportada
            if (!isMatureCrop(state)) {
                return InteractionResult.PASS;
            }

            // Obtém a semente correspondente ao bloco
            Item seedItem = state.getBlock().asItem();
            if (seedItem == Items.AIR) {
                return InteractionResult.PASS;
            }

            // Verifica se o jogador possui a semente (dispensa em criativo)
            if (!serverPlayer.isCreative() && !hasItem(serverPlayer, seedItem)) {
                return InteractionResult.PASS;
            }

            // Consome 1 semente do inventário (não em criativo)
            if (!serverPlayer.isCreative()) {
                consumeItem(serverPlayer, seedItem);
            }

            // Quebra o bloco com drops normais e replanta na idade 0
            world.destroyBlock(pos, true, player, 512);
            world.setBlock(pos, state.getBlock().defaultBlockState(), 3);

            // Som de plantio
            boolean isNetherWart = state.getBlock() instanceof NetherWartBlock;
            world.playSound(null, pos,
                    isNetherWart ? SoundEvents.NETHER_WART_PLANTED : SoundEvents.CROP_PLANTED,
                    SoundSource.BLOCKS, 1.0f, 1.0f);

            return InteractionResult.SUCCESS_SERVER;
        });
    }

    /** Retorna true se o bloco for uma plantação madura reconhecida. */
    private static boolean isMatureCrop(BlockState state) {
        if (state.getBlock() instanceof CropBlock cropBlock) {
            return cropBlock.isMaxAge(state);
        }
        if (state.getBlock() instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) == NetherWartBlock.MAX_AGE;
        }
        if (state.getBlock() instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) == CocoaBlock.MAX_AGE;
        }
        return false;
    }

    /** Retorna true se o jogador possuir pelo menos 1 unidade do item no inventário. */
    private static boolean hasItem(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() == item) {
                return true;
            }
        }
        return false;
    }

    /** Remove 1 unidade do item do inventário do jogador (primeiro slot encontrado). */
    private static void consumeItem(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == item) {
                stack.shrink(1);
                break;
            }
        }
    }
}
