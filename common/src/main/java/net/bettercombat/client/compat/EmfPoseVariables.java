package net.bettercombat.client.compat;

import net.bettercombat.BetterCombatMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Publishes the player's already-posed bones so the 3D armor can copy them instead of working them
 * out again.
 *
 * <p>Without this every armor piece carries a copy of Fresh Animations' rig and re-derives the pose
 * from scratch - the chestplate computing where your shoulder is, at the same moment your body is
 * computing the same thing beside it. Three pieces, three redundant rigs, roughly ninety-five
 * animation channels a frame between them.
 *
 * <p>The pose is captured where it is guaranteed to be final and correct: the model handed to
 * {@code EquipmentLayerRenderer.renderLayers}, which is the very model the armor is about to be drawn
 * on. No assumption about what EMF animates first - if the value is there, it is the right one.
 *
 * <p>A single slot rather than a map per entity, for the same reason {@link EmfBendVariables} uses
 * one: EMF does not say which entity it is evaluating, and it does not need to. The game draws one
 * player at a time, and the capture happens immediately before that player's armor is evaluated.
 */
public final class EmfPoseVariables {

    /** Bones an armor piece can ride, in the names CEM uses. */
    private static final String[] BONES = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

    /** Rotation then translation, in the order the values are stored per bone. */
    private static final String[] COMPONENTS = {"rx", "ry", "rz", "tx", "ty", "tz"};

    public static final String POSE_VARIABLE_PREFIX = "nf_pose_";

    private static final float[] POSE = new float[BONES.length * COMPONENTS.length];

    private EmfPoseVariables() {
    }

    /** Called with the model the armor layer is about to be drawn on, once per piece. */
    public static void capture(HumanoidModel<?> model) {
        store(0, model.head);
        store(1, model.body);
        store(2, model.rightArm);
        store(3, model.leftArm);
        store(4, model.rightLeg);
        store(5, model.leftLeg);
    }

    private static void store(int bone, ModelPart part) {
        if (part == null) {
            return;
        }
        int base = bone * COMPONENTS.length;
        // Rotations go across as they are: every humanoid bone rests at zero, so its current value is
        // already the offset from rest that CEM wants.
        POSE[base] = part.xRot;
        POSE[base + 1] = part.yRot;
        POSE[base + 2] = part.zRot;
        // Translations go across whole, not as a delta from the rest pose. Tried and measured in game:
        // a CEM translation channel is the part's position, not an offset onto it, so sending deltas
        // put every piece at the origin and the armor collapsed in on itself. A leg belongs at y=12
        // and that is the number CEM wants.
        POSE[base + 3] = part.x;
        POSE[base + 4] = part.y;
        POSE[base + 5] = part.z;
    }

    public static void register(Class<?> emfApi) {
        Method register;
        try {
            register = emfApi.getMethod("registerSingletonAnimationVariable",
                    String.class, String.class, String.class, Supplier.class);
        } catch (ReflectiveOperationException absent) {
            BetterCombatMod.LOGGER.warn("[bc] EMF has no float variable API; 3D armor will keep its own rig.");
            return;
        }

        int registered = 0;
        for (int bone = 0; bone < BONES.length; bone++) {
            for (int component = 0; component < COMPONENTS.length; component++) {
                String name = POSE_VARIABLE_PREFIX + BONES[bone] + "_" + COMPONENTS[component];
                int index = bone * COMPONENTS.length + component;
                Supplier<Float> value = () -> POSE[index];
                try {
                    register.invoke(null, "bettercombat", name, name, value);
                    registered++;
                } catch (ReflectiveOperationException failure) {
                    BetterCombatMod.LOGGER.error("[bc] Could not register EMF variable '{}': {}",
                            name, failure.toString());
                }
            }
        }
        BetterCombatMod.LOGGER.info("[bc] EMF pose variables registered: {}/{}.",
                registered, BONES.length * COMPONENTS.length);
    }
}
