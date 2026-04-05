package com.jovinull.omnitweaks.modules.omnisaver;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import com.jovinull.omnitweaks.OmniTweaks;
import com.jovinull.omnitweaks.core.ModuleManager;

/**
 * Módulo OmniSaver — monitora durabilidade de ferramentas e armaduras
 * equipadas a cada tick do servidor. Quando a durabilidade restante
 * cai abaixo de 5, o item é automaticamente movido para o inventário
 * (ou dropado se cheio) para evitar destruição acidental.
 *
 * <p>Slots monitorados: mão principal, mão secundária e peito (Elytra).
 * Itens não danificáveis (blocos, alimentos etc.) são ignorados.</p>
 */
public final class OmniSaverModule {

    private static final int DURABILITY_THRESHOLD = 5;
    private static final EquipmentSlot[] MONITORED_SLOTS = {
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND,
            EquipmentSlot.CHEST
    };

    private OmniSaverModule() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(OmniSaverModule::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        ModuleManager manager = OmniTweaks.getModuleManager();
        if (manager == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!manager.isEnabled(player.getUUID(), "omnisaver")) {
                continue;
            }

            checkEquipmentDurability(player);
        }
    }

    private static void checkEquipmentDurability(ServerPlayer player) {
        for (EquipmentSlot slot : MONITORED_SLOTS) {
            ItemStack item = player.getItemBySlot(slot);

            if (item.isEmpty() || !item.isDamageableItem()) {
                continue;
            }

            // Calcula durabilidade restante: máximo - dano acumulado
            int remaining = item.getMaxDamage() - item.getDamageValue();

            if (remaining >= DURABILITY_THRESHOLD) {
                continue;
            }

            // Copia o item antes de limpar o slot
            ItemStack savedItem = item.copy();

            // Limpa o slot de equipamento para impedir uso contínuo
            player.setItemSlot(slot, ItemStack.EMPTY);

            // Tenta mover para o inventário; se cheio, dropa no chão
            if (!player.getInventory().add(savedItem)) {
                player.drop(savedItem, false);
            }

            // Som de alerta: pitch alto (2.0) para chamar atenção
            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f);

            // Mensagem na action bar (segundo parâmetro true = overlay/action bar)
            player.sendSystemMessage(Component.empty()
                    .append(Component.translatable("message.omnitweaks.saver.warning")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.translatable("message.omnitweaks.saver.saved")
                            .withStyle(ChatFormatting.WHITE)), true);
        }
    }
}
