package net.bettercombat.client.animation;

import org.jetbrains.annotations.Nullable;

/**
 * Carries the emote item anchor onto the render state.
 *
 * <p>Needed because rendering in this version reads from a render state rather than from the entity:
 * the layer that draws the held item never sees the player, so anything it must know has to be copied
 * across during extraction.
 */
public interface EmoteItemAnchorHolder {

    @Nullable EmoteItemAnchor bettercombat$getEmoteItemAnchor();

    void bettercombat$setEmoteItemAnchor(@Nullable EmoteItemAnchor anchor);

    /** The off hand gets its own anchor, so two weapons can cross instead of stacking on one spot. */
    @Nullable EmoteItemAnchor bettercombat$getEmoteOffHandAnchor();

    void bettercombat$setEmoteOffHandAnchor(@Nullable EmoteItemAnchor anchor);

    /** Whether held items should not be drawn at all for the duration of the emote. */
    boolean bettercombat$isHidingEmoteItems();

    void bettercombat$setHidingEmoteItems(boolean hiding);

    /**
     * Whether the cape should be left undrawn for the duration of the emote.
     *
     * <p>Set for emotes that bend the torso. The cape hangs off the body, and PlayerAnimationLib
     * attaches it with {@code translateAndRotate} - position and rotation only, no bend - so a bent
     * torso curves away while the cape stays on the straight anchor and appears to float behind it.
     * There is no seam to hook: the bend is a deformation applied inside the cuboid renderer, with
     * nothing exposing where the top of a bent torso ended up.
     */
    boolean bettercombat$isHidingCape();

    void bettercombat$setHidingCape(boolean hiding);
}
