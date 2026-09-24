package net.bettercombat.client.animation;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import net.bettercombat.BetterCombatMod;
import net.bettercombat.client.compat.FirstPersonAnimationCompatibility;
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

    /** Both arms on the handles and the glider in the right hand; nothing is held in the left. */
    private static final FirstPersonConfiguration GLIDER_FIRST_PERSON =
            new FirstPersonConfiguration(true, true, true, false);


    public EmoteAnimationStack(Avatar entity, AnimationStateHandler animationHandler) {
        super(entity, animationHandler);
        postInit();
    }

    private void postInit() {
        this.addModifier(speed, 0);
        // Glider emotes bank into turns and lean with speed on a spring (Hytale's WiggleWeights).
        this.addModifierLast(new GlideWiggle(this::getAvatar, this::wantsGlideWiggle));

        // Emotes are a third-person expression. Rendering the animated body inside your own head was
        // tried and never read as anything but a glitch, so the layer stays out of first person;
        // poses worth looking at force third person instead. Glider poses are the exception: the
        // glider is flown from first person, and there the raised arms and the glider over them are
        // the view (as in Hytale). Drawn from the third-person model, like an attack, so the item
        // anchor and the ribbons that hang off the hand layer come along unchanged.
        this.firstPersonMode = (controller) -> wantsGlideWiggle()
                ? FirstPersonAnimationCompatibility.firstPersonMode()
                : FirstPersonMode.DISABLED;
        this.firstPersonConfiguration = (controller) -> GLIDER_FIRST_PERSON;
    }

    /** Whether the emote playing asks for {@link DashAimHolder dash aim}: {@code "dash_aim": true} in its JSON. */
    public boolean wantsDashAim() {
        var current = this.getCurrentAnimation();
        return current != null && Boolean.TRUE.equals(current.animation().data().getRaw("dash_aim"));
    }

    /** Whether the emote playing asks for {@link GlideWiggle}: {@code "glide_wiggle": true} in its JSON. */
    private boolean wantsGlideWiggle() {
        var current = this.getCurrentAnimation();
        return current != null && Boolean.TRUE.equals(current.animation().data().getRaw("glide_wiggle"));
    }
}
