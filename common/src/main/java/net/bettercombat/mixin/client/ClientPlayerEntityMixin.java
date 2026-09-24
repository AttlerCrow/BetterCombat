package net.bettercombat.mixin.client;

import net.bettercombat.BetterCombatMod;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.bettercombat.client.animation.PlayerAttackAnimatable;
import net.bettercombat.client.misc.ItemStackViewerPlayer;
import net.bettercombat.utils.MathHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class ClientPlayerEntityMixin implements ItemStackViewerPlayer {
    @Shadow protected abstract boolean isControlledCamera();

    @Inject(method = "applyInput", at = @At(value = "TAIL"))
    private void tickMovement_ModifyInput(CallbackInfo ci) {
        var clientPlayer = (LocalPlayer)((Object)this);
        if (!isControlledCamera()) {
            return;
        }
        var config = BetterCombatMod.config;
        var client = (MinecraftClient_BetterCombat) Minecraft.getInstance();
        float multiplier = (float) Math.min(Math.max(config.movement_speed_while_attacking, 0.0), 1.0);
        var attack = client.getCurrentAttack();
        if (attack != null) {
            multiplier *= attack.movementSpeedMultiplier();
        }
//        System.out.println("Multiplier " + multiplier);
        if (multiplier == 1) {
            return;
        }
        if (clientPlayer.isPassenger() && !config.movement_speed_effected_while_mounting) {
            return;
        }

        var swingProgress = client.getSwingProgress();
        if (swingProgress < 0.98) {
            if (config.movement_speed_applied_smoothly) {
                double p2 = 0;
                if (swingProgress <= 0.5) {
                    p2 = MathHelper.easeOutCubic(swingProgress * 2);
                } else {
                    p2 = MathHelper.easeOutCubic(1 - ((swingProgress - 0.5) * 2));
                }
                multiplier = (float) ( 1.0 - (1.0 - multiplier) * p2 );
//                var chart = "-".repeat((int)(100.0 * multiplier)) + "x";
//                System.out.println("Movement speed multiplier: " + String.format("%.4f", multiplier) + ">" + chart);
            }
            clientPlayer.zza *= multiplier;
            clientPlayer.xxa *= multiplier;
        }
    }

    /**
     * A pinned pose makes sprinting impossible, the way blindness does.
     *
     * <p>The server clears sprinting when it pins a player, and that is not enough: vanilla starts it
     * again on the next tick whenever the sprint key is down and forward is held, and since the client
     * never saw itself stop, it sends nothing. The player sprinted on their own screen only, trailing
     * sprint particles from a seated model until they let go of forward.
     *
     * <p>{@code isSprintingPossible} is the one question behind both halves: it ends a sprint already
     * running ({@code shouldStopRunSprinting}, {@code shouldStopSwimSprinting}) and refuses a new one
     * ({@code canStartSprinting}). Answering it, rather than clearing the flag after the fact, lets
     * vanilla stop the sprint itself and send the matching packet, so the server and every viewer
     * agree.
     */
    @Inject(method = "isSprintingPossible", at = @At("HEAD"), cancellable = true)
    private void bettercombat$noSprintWhilePinned(boolean flying, CallbackInfoReturnable<Boolean> cir) {
        if (((PlayerAttackAnimatable) this).isEmotePinned()) {
            cir.setReturnValue(false);
        }
    }

    // MARK: ItemStackViewerPlayer
    @Unique private ItemStack bettercombat$viewedItemStack = null;
    public void betterCombat_setViewedItemStack(@Nullable ItemStack itemStack) {
        bettercombat$viewedItemStack = itemStack;
    }
    public ItemStack betterCombat_getViewedItemStack() {
        return bettercombat$viewedItemStack;
    }
}
