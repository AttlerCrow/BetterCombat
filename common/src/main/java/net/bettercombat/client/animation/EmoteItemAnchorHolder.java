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
}
