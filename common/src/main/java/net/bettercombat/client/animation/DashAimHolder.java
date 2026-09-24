package net.bettercombat.client.animation;

/**
 * How far to tip a player's whole model along their flight path, carried on the render state.
 *
 * <p>For emotes that ask for it with {@code "dash_aim": true} - the glider's air dash. The pose is a
 * flat dart; dashing at the ground, it has to point at the ground. Tipping the torso inside the
 * animation cannot do that without spoiling the spin the dash rolls through (the roll would no
 * longer be about the body), so the whole model is tipped from outside, where vanilla tips an
 * elytra flyer.
 */
public interface DashAimHolder {

    /** Degrees, positive nose down (flying downward). */
    float bettercombat$getDashAim();

    void bettercombat$setDashAim(float degrees);
}
