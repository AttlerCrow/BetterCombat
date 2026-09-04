package net.bettercombat.client.compat;

import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.bettercombat.BetterCombatMod;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

/**
 * Publishes Player Animation Library's <em>bend</em> to EMF's animation expressions, so the generated
 * 3D armor folds at the knee and elbow along with the player.
 *
 * <p>Bend is not a vanilla model channel - it is an extra deformation applied to the cuboid itself -
 * so a CEM armor model, which reads the vanilla channels only, stays rigid while the limb it rides
 * folds. During a sit the armor then reads as a separate object stuck to the player, however exactly
 * it copies {@code right_leg.rx}.
 *
 * <p>Three things about this were learned the hard way and are easy to undo by accident:
 *
 * <ul>
 *   <li>The value is <b>observed, never requested</b>. Asking the animation stack for a bone
 *       ({@code get3DTransform}) walks every layer; doing that per frame stops Fresh Animations
 *       animating while a weapon pose loops. {@code PlayerModelBendObserverMixin} copies the float out
 *       of the bone PAL is already posing.</li>
 *   <li>The hook is PAL's {@code pal$updatePart} on the model, the same callback BendableCuboids uses
 *       to draw the bend. An attempt on {@code AvatarAnimManager.updatePart} never fired at all.</li>
 *   <li>The torso is {@code torso} to PAL and {@code body} to CEM. {@link #variableFor} bridges the
 *       two vocabularies; without it {@code nf_bend_body} was fed from a bone that does not exist and
 *       silently stayed at zero.</li>
 * </ul>
 *
 * <p>EMF only evaluates these while the entity's animations run, which is why
 * {@code EmfAnimationPauseMixin} has to keep them running through an emote.
 */
public final class EmfBendVariables {

    /** Bones an armor piece rides on, named as Player Animation Library names them. */
    private static final List<String> BENDABLE_BONES =
            List.of("torso", "right_arm", "left_arm", "right_leg", "left_leg");

    /** Prefix of the variable names the generated .jem expressions read. */
    public static final String BEND_VARIABLE_PREFIX = "nf_bend_";

    /**
     * Latest bend per bone for the player currently being drawn.
     *
     * <p>A single slot rather than a map keyed by entity, because EMF does not say which entity it is
     * evaluating when it resolves a variable - measured: {@code getCurrentEntity()} returned null on
     * every call. It does not need to. The game draws one player at a time and PAL poses that player's
     * bones immediately before EMF evaluates their armor, so the last values written are the ones the
     * armor being drawn should use. BendableCuboids relies on the same ordering.
     */
    private static final float[] BENDS = new float[BENDABLE_BONES.size()];
    private static final long[] BEND_AT = new long[BENDABLE_BONES.size()];

    /**
     * When PAL last posed each bone at all, as opposed to when the stored value was written.
     *
     * <p>These are the only two clocks that differ in a way that matters: a bend expires because the
     * animation <em>stopped</em>, not because a weaker value arrived. Without this the last fold of
     * an emote was kept for good - nothing clears the store once PAL stops calling in, and the armor
     * stayed folded until some other animation started posing the bone again. That is why swapping to
     * a Better Combat weapon appeared to fix a stuck armor: the grip resumed the calls.
     */
    private static final long[] SEEN_AT = new long[BENDABLE_BONES.size()];

    /**
     * How long a bend is held before a smaller one may replace it, in milliseconds.
     *
     * <p>PAL runs several posing passes over the model and at least one arrives with the bends near
     * zero, so storing simply the last write let whichever pass happened to land before EMF read decide
     * the value. Keeping the strongest fold of the last few frames makes the reading independent of
     * pass order, and the window is short enough that the armor straightens as the emote ends.
     */
    private static final long BEND_HOLD_MS = 150L;

    private EmfBendVariables() {
    }

    /** Called as PAL poses each bone. Must stay a plain field copy - never ask the stack for a value. */
    public static void observe(PlayerAnimBone bone) {
        if (bone == null) {
            return;
        }
        int index = BENDABLE_BONES.indexOf(bone.getName());
        if (index < 0) {
            return;
        }
        float bend = Float.isFinite(bone.bend) ? bone.bend : 0.0F;
        long now = System.currentTimeMillis();
        SEEN_AT[index] = now;
        if (Math.abs(bend) >= Math.abs(BENDS[index]) || now - BEND_AT[index] > BEND_HOLD_MS) {
            BENDS[index] = bend;
            BEND_AT[index] = now;
        }
    }

    /**
     * Bend of one bone on the player being drawn, in radians; zero until something is observed, and
     * zero again once PAL stops posing that bone.
     */
    private static float bendOf(String bone) {
        int index = BENDABLE_BONES.indexOf(bone);
        if (index < 0) {
            return 0.0F;
        }
        // An animation that ends stops calling in rather than winding down to zero, so silence is
        // the signal. Read against SEEN_AT, not BEND_AT: the latter only moves when the stored value
        // is replaced, and a held fold would keep expiring and coming back.
        return System.currentTimeMillis() - SEEN_AT[index] > BEND_HOLD_MS ? 0.0F : BENDS[index];
    }

    /** CEM calls the torso {@code body}; PAL calls it {@code torso}. The .jem expects the CEM name. */
    private static String variableFor(String bone) {
        return BEND_VARIABLE_PREFIX + ("torso".equals(bone) ? "body" : bone);
    }

    public static void register(Class<?> emfApi) {
        Method register;
        try {
            register = emfApi.getMethod("registerSingletonAnimationVariable",
                    String.class, String.class, String.class, Supplier.class);
        } catch (ReflectiveOperationException absent) {
            BetterCombatMod.LOGGER.warn("[bc] EMF has no float variable API; armor will not follow bends.");
            return;
        }

        int registered = 0;
        for (String bone : BENDABLE_BONES) {
            String name = variableFor(bone);
            Supplier<Float> value = () -> bendOf(bone);
            try {
                register.invoke(null, "bettercombat", name, name, value);
                registered++;
            } catch (ReflectiveOperationException failure) {
                BetterCombatMod.LOGGER.error("[bc] Could not register EMF variable '{}': {}", name, failure.toString());
            }
        }
        BetterCombatMod.LOGGER.info("[bc] EMF bend variables registered: {}/{}.", registered, BENDABLE_BONES.size());
    }
}
