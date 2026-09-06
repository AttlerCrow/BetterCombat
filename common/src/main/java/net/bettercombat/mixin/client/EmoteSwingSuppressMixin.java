package net.bettercombat.mixin.client;

import net.bettercombat.client.animation.PlayerAttackAnimatable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the arm still during an emote that a swing is not allowed to interrupt.
 *
 * <p>The server already refuses the action: a pose with {@code lock-position} cancels the attack, the
 * block break and the left click. What it cannot do is un-draw the swing, because the client plays
 * that itself the moment the button goes down and never asks permission. So a seated player looked
 * like they were punching blocks apart while nothing actually broke.
 *
 * <p>Hooked at {@code swing} rather than at the attack, because mining and attacking are the same
 * gesture from here: both arrive as a swing, and both looked equally wrong on top of a sit.
 *
 * <p>Gated on {@code keepOnAttack} - config's {@code cancel-on-attack: false}. That flag already
 * means "this emote is not interrupted by attacking", and an emote that survives a swing is exactly
 * the one that must not have a swing drawn over it. An emote a swing <em>would</em> end is untouched:
 * the emote stops and the swing plays, as it always did.
 */
@Mixin(LivingEntity.class)
public abstract class EmoteSwingSuppressMixin {

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), cancellable = true)
    private void bettercombat$holdStillDuringEmote(InteractionHand hand, boolean fromServer, CallbackInfo ci) {
        // Only the swings this client started. One arriving from the server is another player's, and
        // second-guessing that would hide swings that really happened.
        if (fromServer || !((Object) this instanceof Player)) {
            return;
        }
        if (this instanceof PlayerAttackAnimatable animatable
                && animatable.isEmoteActive()
                && animatable.isEmoteKeptOnAttack()) {
            ci.cancel();
        }
    }
}
