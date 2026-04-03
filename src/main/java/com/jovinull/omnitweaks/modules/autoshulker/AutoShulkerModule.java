package com.jovinull.omnitweaks.modules.autoshulker;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import com.jovinull.omnitweaks.OmniTweaks;
import com.jovinull.omnitweaks.core.ModuleManager;

/**
 * Módulo AutoShulker — ao coletar itens do chão, tenta inseri-los
 * automaticamente em Shulker Boxes presentes no inventário do jogador.
 *
 * <h3>Fluxo de execução:</h3>
 * <ol>
 *   <li>O {@link com.jovinull.omnitweaks.mixin.ItemEntityMixin} intercepta a colisão
 *       jogador ↔ ItemEntity e delega para {@link #handlePickup}.</li>
 *   <li>Se o módulo estiver ativo, percorre o inventário buscando Shulker Boxes.</li>
 *   <li>Para cada Shulker Box encontrada, tenta mesclar ou inserir o item usando
 *       o Data Component {@code CONTAINER} (sem manipulação direta de NBT).</li>
 *   <li>Se o item for totalmente consumido, cancela a coleta vanilla.</li>
 *   <li>Se for parcialmente consumido, atualiza a entidade no chão e deixa
 *       o comportamento vanilla cuidar do restante.</li>
 * </ol>
 *
 * <h3>Restrições:</h3>
 * <ul>
 *   <li>Shulker Boxes não podem conter outras Shulker Boxes (restrição vanilla).</li>
 *   <li>Apenas Shulker Boxes com contagem 1 são processadas (stacks de shulker
 *       não existem em vanilla, mas a verificação previne comportamento inesperado).</li>
 * </ul>
 */
public final class AutoShulkerModule {

    /** Número de slots internos de uma Shulker Box. */
    private static final int SHULKER_SLOTS = 27;

    private AutoShulkerModule() {}

    /**
     * Ponto de entrada chamado pelo Mixin ao detectar colisão jogador ↔ item.
     *
     * @param player     o jogador servidor que está coletando o item
     * @param itemEntity a entidade de item no chão
     * @return {@code true} se o item foi <b>totalmente</b> consumido e o evento
     *         vanilla deve ser cancelado; {@code false} caso contrário
     */
    public static boolean handlePickup(ServerPlayer player, ItemEntity itemEntity) {
        ModuleManager manager = OmniTweaks.getModuleManager();
        if (manager == null || !manager.isEnabled(player.getUUID(), "autoshulker")) {
            return false;
        }

        ItemStack original = itemEntity.getItem();
        if (original.isEmpty()) {
            return false;
        }

        // Restrição vanilla: Shulker Boxes não cabem dentro de outras Shulker Boxes
        if (isShulkerBox(original)) {
            return false;
        }

        Inventory inventory = player.getInventory();
        ItemStack remaining = original.copy();
        int originalCount = remaining.getCount();
        boolean anyModified = false;

        // Percorre todos os slots do inventário buscando Shulker Boxes
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (remaining.isEmpty()) {
                break;
            }

            ItemStack slotStack = inventory.getItem(slot);

            // Pula slots vazios e itens que não são Shulker Box
            if (slotStack.isEmpty() || slotStack.getCount() != 1) {
                continue;
            }
            if (!isShulkerBox(slotStack)) {
                continue;
            }

            if (tryInsertIntoShulker(slotStack, remaining)) {
                anyModified = true;
            }
        }

        if (!anyModified) {
            return false;
        }

        int consumed = originalCount - remaining.getCount();

        if (remaining.isEmpty()) {
            // Item totalmente absorvido pelas Shulker Boxes
            player.awardStat(Stats.ITEM_PICKED_UP.get(original.getItem()), originalCount);
            playPickupEffects(player, itemEntity, originalCount);
            itemEntity.discard();
            return true;
        }

        // Item parcialmente absorvido: atualiza a entidade com a quantidade restante
        player.awardStat(Stats.ITEM_PICKED_UP.get(original.getItem()), consumed);
        playPickupEffects(player, itemEntity, consumed);
        itemEntity.setItem(remaining);
        return false;
    }

    /**
     * Tenta inserir a {@code remaining} stack dentro de uma Shulker Box.
     *
     * <p>Executa em duas fases:</p>
     * <ol>
     *   <li><b>Mesclagem:</b> tenta adicionar aos stacks existentes do mesmo tipo.</li>
     *   <li><b>Inserção:</b> ocupa slots vazios com o restante.</li>
     * </ol>
     *
     * O Data Component {@code CONTAINER} da Shulker Box é atualizado in-place
     * na ItemStack do inventário do jogador.
     *
     * @param shulkerStack a ItemStack da Shulker Box (modificada in-place via Data Component)
     * @param remaining    a ItemStack sendo coletada (o count é decrementado conforme inserção)
     * @return {@code true} se ao menos um item foi inserido na Shulker Box
     */
    private static boolean tryInsertIntoShulker(ItemStack shulkerStack, ItemStack remaining) {
        // Obtém o conteúdo atual da Shulker Box via Data Component
        ItemContainerContents container = shulkerStack.getOrDefault(
                DataComponents.CONTAINER, ItemContainerContents.EMPTY);

        // Copia o conteúdo para uma lista mutável de 27 slots
        NonNullList<ItemStack> contents = NonNullList.withSize(SHULKER_SLOTS, ItemStack.EMPTY);
        container.copyInto(contents);

        boolean modified = false;

        // --- Fase 1: Mesclar com stacks existentes do mesmo tipo e componentes ---
        for (int i = 0; i < SHULKER_SLOTS && !remaining.isEmpty(); i++) {
            ItemStack slot = contents.get(i);
            if (slot.isEmpty()) {
                continue;
            }

            // Verifica se o item e seus componentes são idênticos (ignora contagem)
            if (!ItemStack.isSameItemSameComponents(slot, remaining)) {
                continue;
            }

            int spaceAvailable = slot.getMaxStackSize() - slot.getCount();
            if (spaceAvailable <= 0) {
                continue;
            }

            int transfer = Math.min(remaining.getCount(), spaceAvailable);
            slot.grow(transfer);
            remaining.shrink(transfer);
            modified = true;
        }

        // --- Fase 2: Inserir em slots vazios ---
        for (int i = 0; i < SHULKER_SLOTS && !remaining.isEmpty(); i++) {
            if (!contents.get(i).isEmpty()) {
                continue;
            }

            int transfer = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            contents.set(i, remaining.copyWithCount(transfer));
            remaining.shrink(transfer);
            modified = true;
        }

        // Salva o Data Component atualizado de volta na ItemStack da Shulker Box
        if (modified) {
            shulkerStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        }

        return modified;
    }

    /**
     * Reproduz os efeitos visuais e sonoros de coleta, imitando o comportamento vanilla.
     * Envia a animação de item voando até o jogador e toca o som de coleta.
     *
     * @param player     jogador que coletou
     * @param itemEntity entidade do item coletado
     * @param count      quantidade de itens coletados (para a animação)
     */
    private static void playPickupEffects(ServerPlayer player, ItemEntity itemEntity, int count) {
        // Animação de coleta: o item "voa" até o jogador para todos os jogadores próximos
        player.take(itemEntity, count);

        // Som de coleta vanilla (mesmos parâmetros usados no código original do Minecraft)
        player.level().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS,
                0.2f,
                ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7f + 1.0f) * 2.0f
        );
    }

    /**
     * Verifica se a ItemStack é qualquer variante de Shulker Box (colorida ou neutra).
     *
     * @param stack a ItemStack a ser verificada
     * @return {@code true} se for uma Shulker Box
     */
    private static boolean isShulkerBox(ItemStack stack) {
        return Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }
}
