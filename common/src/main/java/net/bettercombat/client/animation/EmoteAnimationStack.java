package net.bettercombat.client.animation;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import net.bettercombat.BetterCombatMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;

/**
 * The layer emotes play on, kept apart from {@link AttackAnimationStack} on purpose.
 *
 * <p>Three differences from the attack layer, and each one is the reason this class exists rather
 * than emotes borrowing the combat one:
 *
 * <ul>
 *   <li>Constant speed. The attack layer splits playback into upswing and downwind gears, which is
 *       meaningful for a swing and wrong for a wave - it is what forces callers of the attack path
 *       to pass {@code upswing=1.0} to get an even playback. Here there are no gears at all.</li>
 *   <li>Every bone the animation touches is animated. The attack layer pins head pitch to the camera
 *       and drops the legs while the player moves, so a bow or a nod is invisible there. This layer
 *       installs no post-setup consumer, which leaves the library default: a bone is enabled exactly
 *       when the animation keyframes it.</li>
 *   <li>No pitch adjustment. The attack layer leans the body with the camera so a swing lands where
 *       you look; an emote should read the same whether you are staring at the sky or your feet.</li>
 * </ul>
 *
 * <p>Registered at priority 3000 in {@code BetterCombatClientMod.setupAnimations()}, above the attack
 * layer's 2000, so an emote in progress wins over a stale swing. The reverse case - a swing starting
 * while an emote plays - is handled by cancelling the emote outright, not by priority.
 */
public class EmoteAnimationStack extends PlayerAnimationController {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "emote");

    public final TransmissionSpeedModifier speed = new TransmissionSpeedModifier(1F);


    public EmoteAnimationStack(Avatar entity, AnimationStateHandler animationHandler) {
        super(entity, animationHandler);
        postInit();
    }

    private void postInit() {
        this.addModifier(speed, 0);

        // Emotes are a third-person expression. Rendering the animated body inside your own head was
        // tried and never read as anything but a glitch, so the layer simply stays out of first
        // person; poses worth looking at force third person instead.
        this.firstPersonMode = (controller) -> FirstPersonMode.DISABLED;
    }
}
