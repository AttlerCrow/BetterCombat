package net.bettercombat.client.animation;

import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.modifier.AdjustmentModifier;
import com.zigythebird.playeranimcore.math.Vec3f;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Hytale's glider "wiggle": the body banks into a strafe or a turn and leans with the speed, on a
 * spring, instead of snapping between left/right poses.
 *
 * <p>Hytale's {@code Glider.json} drives this from {@code WiggleWeights} - pitch and roll following
 * the motion with a slow deceleration - and it is most of why its glide looks alive. Here it is an
 * adjustment on the emote layer, added on top of whatever glide pose is playing, and only for
 * animations that ask for it with {@code "glide_wiggle": true} in their JSON. Because the glider is
 * anchored to the body, it banks with it and the hands never leave the handles.
 *
 * <p>Two springs: the body follows the motion briskly; the legs follow the body lazily, so they swing
 * out and lag behind like something hanging.
 */
public final class GlideWiggle extends AdjustmentModifier {

    /**
     * Bank (degrees) per block/tick of sideways speed, and per degree/tick of turning. Strafing is
     * zero on purpose: the glider's left/right poses already bank for a strafe, and they are liked
     * as they are - this only adds the bank into a turn of the camera, which no pose can know about.
     */
    private static final float BANK_PER_STRAFE = 0F;
    private static final float BANK_PER_TURN = 1.6F;
    private static final float MAX_BANK = 28F;
    /** Forward lean (degrees) per block/tick of forward speed, and from speeding up or braking. */
    private static final float LEAN_PER_SPEED = 30F;
    private static final float LEAN_PER_ACCEL = 160F;
    private static final float MAX_LEAN = 16F;
    /** Spring stiffness and damping per tick; the legs are softer so they trail the body. */
    private static final float BODY_K = 0.16F, BODY_DAMP = 0.42F;
    private static final float LEGS_K = 0.07F, LEGS_DAMP = 0.25F;

    private final Supplier<Avatar> avatar;
    private final Supplier<Boolean> active;

    private float bank, bankVel, bankPrev;
    private float lean, leanVel, leanPrev;
    private float legBank, legBankVel, legBankPrev;
    private float legLean, legLeanVel, legLeanPrev;
    private double lastForwardSpeed;
    private boolean primed;

    public GlideWiggle(Supplier<Avatar> avatar, Supplier<Boolean> active) {
        super((String bone, AnimationData data) -> Optional.empty());
        this.avatar = avatar;
        this.active = active;
        this.source = this::adjust;
        // A pose already on screen when it switches; the springs carry the motion across instead.
        this.fadeIn = false;
        this.fadeOut = false;
    }

    @Override
    public void tick(AnimationData state) {
        super.tick(state);
        bankPrev = bank;
        leanPrev = lean;
        legBankPrev = legBank;
        legLeanPrev = legLean;
        Avatar player = avatar.get();
        if (player == null || !active.get()) {
            // Settle to rest so the next glide starts level.
            bank = lean = legBank = legLean = 0F;
            bankVel = leanVel = legBankVel = legLeanVel = 0F;
            primed = false;
            return;
        }
        double yaw = Math.toRadians(player.getYRot());
        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        // Minecraft yaw 0 faces +Z: forward (-sin, cos), right (-cos, -sin).
        double forward = -Math.sin(yaw) * dx + Math.cos(yaw) * dz;
        double sideways = -Math.cos(yaw) * dx - Math.sin(yaw) * dz;
        float turn = Mth.wrapDegrees(player.getYRot() - player.yRotO);
        double accel = primed ? forward - lastForwardSpeed : 0;
        lastForwardSpeed = forward;
        primed = true;

        float bankTarget = Mth.clamp((float) (sideways * BANK_PER_STRAFE) + turn * BANK_PER_TURN, -MAX_BANK, MAX_BANK);
        float leanTarget = Mth.clamp((float) (forward * LEAN_PER_SPEED + accel * LEAN_PER_ACCEL), -MAX_LEAN, MAX_LEAN);

        bankVel += (bankTarget - bank) * BODY_K - bankVel * BODY_DAMP;
        bank += bankVel;
        leanVel += (leanTarget - lean) * BODY_K - leanVel * BODY_DAMP;
        lean += leanVel;
        // Legs chase the body's own motion, late: what is left over is their swing.
        legBankVel += (bank - legBank) * LEGS_K - legBankVel * LEGS_DAMP;
        legBank += legBankVel;
        legLeanVel += (lean - legLean) * LEGS_K - legLeanVel * LEGS_DAMP;
        legLean += legLeanVel;
    }

    private Optional<PartModifier> adjust(String bone, AnimationData data) {
        if (!active.get()) {
            return Optional.empty();
        }
        float t = data.getPartialTick();
        float b = (float) Math.toRadians(Mth.lerp(t, bankPrev, bank));
        float l = (float) Math.toRadians(Mth.lerp(t, leanPrev, lean));
        return switch (bone) {
            // Bank rolls toward the side moved to; lean pitches forward (negative X leans forward in
            // this rig, as the glide assets' own torso pitch does).
            case "torso" -> Optional.of(part(-l, 0F, -b));
            case "right_leg", "left_leg" -> {
                // The legs' lag behind the body, as a counter-rotation relative to it.
                float lb = (float) Math.toRadians(Mth.lerp(t, legBankPrev, legBank));
                float ll = (float) Math.toRadians(Mth.lerp(t, legLeanPrev, legLean));
                yield Optional.of(part(-(ll - l) * 1.4F, 0F, -(lb - b) * 1.4F));
            }
            default -> Optional.empty();
        };
    }

    private static PartModifier part(float x, float y, float z) {
        return new PartModifier(new Vec3f(x, y, z), new Vec3f(0F, 0F, 0F));
    }
}
