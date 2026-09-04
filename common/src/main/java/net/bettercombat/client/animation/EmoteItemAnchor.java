package net.bettercombat.client.animation;

/**
 * Where a held item should sit while an emote plays, relative to the player's torso.
 *
 * <p>Anchoring to the torso rather than the hand is the whole point: the item bone in an animation is
 * a child of the arm, so anything expressed there swings with the arm and cannot stay put on the
 * back. This is applied at render time instead, replacing the hand transform outright.
 *
 * <p>Coordinates are in <strong>model units</strong> - 16 to a block, the numbers you read off a rig
 * or a model file - in model space, where Y points <em>down</em>. Rotations are radians.
 */
public record EmoteItemAnchor(float x, float y, float z, float pitch, float yaw, float roll) {
}
