package com.jovinull.omnitweaks.mixin;

import com.jovinull.omnitweaks.modules.autoshulker.AutoShulkerModule;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin na classe {@link ItemEntity} para interceptar a coleta de itens pelo jogador.
 *
 * <p>Injeta no início de {@code onPlayerCollision} (antes de qualquer lógica vanilla)
 * e delega ao {@link AutoShulkerModule}. Se o módulo consumir todo o item,
 * o evento vanilla é cancelado via {@link CallbackInfo#cancel()}.</p>
 *
 * <p>Verificações de segurança realizadas antes de delegar:</p>
 * <ul>
 *   <li>Ignora execução no lado cliente (apenas server-side).</li>
 *   <li>Respeita o delay de coleta do item ({@code pickupDelay > 0}).</li>
 *   <li>Garante que a entidade é um {@link ServerPlayerEntity}.</li>
 * </ul>
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Shadow
    private int pickupDelay;

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void omnitweaks$beforePickup(PlayerEntity player, CallbackInfo ci) {
        // Apenas server-side
        if (player.getWorld().isClient()) {
            return;
        }

        // Respeita o delay de coleta vanilla
        if (this.pickupDelay > 0) {
            return;
        }

        // Garante que é um jogador do servidor
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        ItemEntity self = (ItemEntity) (Object) this;

        // Se o AutoShulker consumiu todo o item, cancela o comportamento vanilla
        if (AutoShulkerModule.handlePickup(serverPlayer, self)) {
            ci.cancel();
        }
    }
}
