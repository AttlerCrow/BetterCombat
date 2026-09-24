package net.bettercombat.mixin.client;

import net.bettercombat.BetterCombatMod;
import net.bettercombat.logic.PlayerAttackHelper;
import net.bettercombat.mixin.player.LivingEntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class ClientPlayerInteractionManagerMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "stopDestroyBlock", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;resetAttackStrengthTicker()V",
            shift = At.Shift.AFTER))
    public void cancelBlockBreaking_FixAttackCD(CallbackInfo ci) {
        try {
            var player = minecraft.player;
            var cooldownLength = PlayerAttackHelper.getAttackCooldownTicksCapped(player); // `getAttackCooldownProgressPerTick` should be called `getAttackCooldownLengthTicks`
            float typicalUpswing = 0.5F;
            int reducedCooldown = Math.round(cooldownLength * typicalUpswing * BetterCombatMod.config.upswing_multiplier);
            ((LivingEntityAccessor)player).betterCombat_setTicksSinceLastAttack(reducedCooldown);
        } catch (Exception ignored) { } // We may get random exceptions when trying to access weapon cooldown
    }

    /**
     * BetterModel attaches Interaction entities for mob/boss hitboxes. In vanilla, an Interaction
     * entity's interact() method returns CONSUME on the client, which halts Minecraft.startUseItem()
     * before useItem() can run. When holding a weapon that blocks/parries, let the interaction PASS
     * through so item use (the guard/parry) activates normally. The ServerboundInteractPacket was
     * already sent above this line, so server hitbox listeners still receive the click.
     */
    @Inject(method = "interact", at = @At("RETURN"), cancellable = true)
    public void onInteractEntity_PassForBlocking(Player player, Entity entity, EntityHitResult hitResult, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (entity instanceof Interaction) {
            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();
            if (mainHand.has(DataComponents.BLOCKS_ATTACKS) || offHand.has(DataComponents.BLOCKS_ATTACKS)) {
                cir.setReturnValue(InteractionResult.PASS);
            }
        }
    }
}
