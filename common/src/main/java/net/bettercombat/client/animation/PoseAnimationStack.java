package net.bettercombat.client.animation;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.animation.layered.modifier.AdjustmentModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.MirrorModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.math.Vec3f;
import net.bettercombat.BetterCombatMod;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class PoseAnimationStack extends PlayerAnimationController {
    public static final Identifier MAIN_HAND_BODY_ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "pose_main_hand_body");
    public static final Identifier MAIN_HAND_ITEM_ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "pose_main_hand_item");
    public static final Identifier OFF_HAND_BODY_ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "pose_off_hand_body");
    public static final Identifier OFF_HAND_ITEM_ID = Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "pose_off_hand_item");

    /** Bone names as the animation library registers them - snake_case, not the rig's camelCase. */
    private static final String ITEM_RIGHT = "right_item";
    private static final String ITEM_LEFT = "left_item";
    private static final String[] BODY_PARTS = {
            "head", "torso", "body", "right_arm", "left_arm", "right_leg", "left_leg"
    };

    public final MirrorModifier mirror = new MirrorModifier();
    public boolean lastAnimationUsesBodyChannel = false;
    private final boolean isMainHand;
    private final boolean isBodyChannel;
    private PoseData lastPose;

    public PoseAnimationStack(Avatar player, AnimationStateHandler animationHandler, boolean isBodyChannel, boolean isMainHand) {
        super(player, animationHandler);
        this.isMainHand = isMainHand;
        this.isBodyChannel = isBodyChannel;
        postInit();
    }

    private void postInit() {
        // Add modifiers
        this.addModifier(mirror, 0);
        if (isMainHand && isBodyChannel) {
            this.addModifierLast(createPoseAdjustment());
        }

        // Configure first-person mode using compatibility layer
        this.firstPersonMode = (controller) -> FirstPersonMode.DISABLED;

        // Split the two channels apart by bone.
        //
        // This was dead code: it used camelCase names ("rightItem", "rightArm"), which are not in the
        // bone map, so every call returned null and threw - and with it commented out both channels
        // animated everything. That is why a weapon grip bled through on top of an emote: the "item"
        // layer was applying the whole grip pose, arms included. The real names are snake_case.
        this.setPostAnimationSetupConsumer((func) -> {
            if (isBodyChannel) {
                // Body channel: pose the body, leave the weapon to the item channel.
                func.apply(ITEM_RIGHT).setEnabled(false);
                func.apply(ITEM_LEFT).setEnabled(false);
                lastAnimationUsesBodyChannel = true;
            } else {
                // Item channel: place the weapon and nothing else, so it can stay active while an
                // emote owns the arms.
                for (String part : BODY_PARTS) {
                    func.apply(part).setEnabled(false);
                }
                lastAnimationUsesBodyChannel = false;
            }
        });
    }

    public void setPose(@Nullable String animationId, boolean isLeftHanded) {
        var mirror = isLeftHanded;
        if (!isMainHand) {
            mirror = !mirror;
        }

        var newPoseData = PoseData.from(animationId, mirror);
        if (lastPose != null && newPoseData.equals(lastPose)) {
            return;
        }

        if (animationId == null) {
            this.stopTriggeredAnimation();
            lastAnimationUsesBodyChannel = false;
        } else {
            var animation = PlayerAnimResources.getAnimation(Identifier.parse(animationId));
            this.mirror.enabled = mirror;
            // From tick 1, the pose's first keyframe. Tick 0 is before any keyframe - the rest pose -
            // so a pose put back after swimming, climbing or a swing flashed the arm down for a frame.
            this.triggerAnimation(animation, 1F);
        }

        lastPose = newPoseData;
    }

    private AdjustmentModifier createPoseAdjustment() {
        return new AdjustmentModifier((partName, data) -> {
            float offsetX = 0;
            float offsetY = 0;
            float offsetZ = 0;
            var player = this.getAvatar();
            if (!data.isFirstPersonPass()) {
                if (isArm(partName)) {
                    if (player.isCrouching()) {
                        offsetY -= 3;
                    }
                } else {
                    return Optional.empty();
                }
            }

            return Optional.of(new AdjustmentModifier.PartModifier(
                    new Vec3f(0, 0, 0),
                    new Vec3f(offsetX, offsetY, offsetZ))
            );
        });
    }

    private static boolean isArm(String partName) {
        return partName.equals(PartNames.RIGHT_ARM) || partName.equals(PartNames.LEFT_ARM);
    }
}
