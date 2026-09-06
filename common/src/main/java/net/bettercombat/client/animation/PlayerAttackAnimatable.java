package net.bettercombat.client.animation;

import net.bettercombat.api.fx.ParticlePlacement;
import net.bettercombat.api.fx.TrailAppearance;
import net.bettercombat.logic.AnimatedHand;

import java.util.List;

public interface PlayerAttackAnimatable {
    void updateAnimationsOnTick();
    void playAttackAnimation(String name, AnimatedHand hand, float length, float upswing);
    void playAttackParticles(boolean isOffHand, float weaponRange, int delay, List<ParticlePlacement> particles, TrailAppearance appearance);
    void stopAttackAnimation(float length);

    /**
     * Plays an emote on the dedicated emote layer, at constant speed.
     *
     * <p>Whether it loops is a property of the asset, not of this call: the loader turns the JSON's
     * {@code isLoop}/{@code returnTick} into the animation's loop type, and the trigger path honours
     * it. A looping emote therefore runs until {@link #stopEmoteAnimation()}.
     *
     * @param length total duration in ticks to fit the animation into; lower is faster
     */
    void playEmoteAnimation(String name, float length, boolean hidePose,
                            boolean photoCamera, boolean hideItems, boolean thirdPerson,
                            boolean keepOnAttack,
                            float cameraHeightOffset,
                            @org.jetbrains.annotations.Nullable EmoteItemAnchor itemAnchor,
                            @org.jetbrains.annotations.Nullable EmoteItemAnchor offHandAnchor);

    /** Ends whatever emote is playing, including a looping one. */
    void stopEmoteAnimation();

    /** How far the emote drops the eye, in blocks, or zero. */
    float getEmoteCameraHeightOffset();

    /** Whether an emote is playing right now, for compatibility layers that need to stand aside. */
    boolean isEmoteActive();

    /** Where the held item should sit right now, or null to leave it in the hand. */
    @org.jetbrains.annotations.Nullable EmoteItemAnchor getEmoteItemAnchor();

    /** Moves the item of an emote already playing, without restarting the animation. */
    void updateEmoteItemAnchors(@org.jetbrains.annotations.Nullable EmoteItemAnchor itemAnchor,
                                @org.jetbrains.annotations.Nullable EmoteItemAnchor offHandAnchor);

    /** Whether the emote wants held items not drawn at all. */
    boolean isHidingEmoteItems();

    /** Same, for the off hand. */
    @org.jetbrains.annotations.Nullable EmoteItemAnchor getEmoteOffHandAnchor();
}
