package net.bettercombat.client.animation;

import net.bettercombat.api.fx.ParticlePlacement;
import net.bettercombat.api.fx.TrailAppearance;
import net.bettercombat.logic.AnimatedHand;

import java.util.List;

public interface PlayerAttackAnimatable {
    void updateAnimationsOnTick();
    void playAttackAnimation(String name, AnimatedHand hand, float length, float upswing);

    /**
     * {@link #playAttackAnimation}, for an animation the server sent: when another is already playing
     * on the attack layer, the new one fades in from the pose on screen instead of starting from rest.
     *
     * <p>A triggered animation begins at tick zero, and before its first keyframe that is the rest
     * pose. Two server animations back to back - a parry attempt followed by the held guard, a guard
     * followed by the recoil of a blocked hit - therefore dropped the arms for a frame and raised them
     * again, which read as the move firing twice. Swings keep the hard start: their own combos are
     * authored for it, and a fade would soften every hit.
     */
    void playForcedAnimation(String name, AnimatedHand hand, float length, float upswing);
    void playAttackParticles(boolean isOffHand, float weaponRange, int delay, List<ParticlePlacement> particles, TrailAppearance appearance);
    void stopAttackAnimation(float length);

    /**
     * Plays an emote on the dedicated emote layer, at constant speed.
     *
     * <p>Whether it loops is a property of the asset, not of this call: the loader turns the JSON's
     * {@code isLoop}/{@code returnTick} into the animation's loop type, and the trigger path honours
     * it. A looping emote therefore runs until {@link #stopEmoteAnimation()}.
     *
     * @param length   total duration in ticks to fit the animation into; lower is faster
     * @param lockBody hold the body at {@code bodyYaw} for as long as this plays. A watcher only
     *                 ever learns a heading from movement, so a player who turns on the spot and
     *                 then sits was drawn on every other screen still facing the way they last
     *                 walked - the pose was right and the direction was somebody else's
     */
    void playEmoteAnimation(String name, float length, boolean hidePose,
                            boolean photoCamera, boolean hideItems, boolean thirdPerson,
                            boolean keepOnAttack,
                            float cameraHeightOffset,
                            @org.jetbrains.annotations.Nullable EmoteItemAnchor itemAnchor,
                            @org.jetbrains.annotations.Nullable EmoteItemAnchor offHandAnchor,
                            boolean lockBody, float bodyYaw);

    /** Ends whatever emote is playing, including a looping one. */
    void stopEmoteAnimation();

    /** How far the emote drops the eye, in blocks, or zero. */
    float getEmoteCameraHeightOffset();

    /** Whether an emote is playing right now, for compatibility layers that need to stand aside. */
    boolean isEmoteActive();

    /**
     * Marks the attack animation just started as one the server drove for a skill or a dodge, for
     * {@code lengthTicks}.
     *
     * <p>A swing and a skill share the attack layer, but not what they animate: a swing moves the
     * arms and leaves the body to whatever model pack is installed, while a skill animation is a
     * whole-body performance - a crouch, a leap, a spin - exactly like an emote. Compatibility layers
     * ask {@link #isSkillAnimationActive()} to tell the two apart. A swing started afterwards, or a
     * stop, clears the mark.
     */
    void markSkillAnimation(float lengthTicks);

    /** Whether a server-driven skill animation owns the whole body right now. */
    boolean isSkillAnimationActive();

    /**
     * Whether this player's emote pins them in place - {@code lock-position} on the server - from the
     * packet until the emote is stopped, outro included. Unlike {@link #isEmoteActive()} it is also
     * true for other players while their pose is on screen.
     */
    boolean isEmotePinned();

    /** How far the model is tipped along its flight path, degrees nose down; see {@link DashAimHolder}. */
    default float getDashAimPitch(float partialTick) {
        return 0F;
    }

    /** Where the held item should sit right now, or null to leave it in the hand. */
    @org.jetbrains.annotations.Nullable EmoteItemAnchor getEmoteItemAnchor();

    /**
     * {@link #getEmoteItemAnchor()} as drawn this frame: when an emote replaced another with a
     * different anchor, the item travels between the two over the pose's own fade.
     */
    default @org.jetbrains.annotations.Nullable EmoteItemAnchor getEmoteItemAnchor(float partialTick) {
        return getEmoteItemAnchor();
    }

    /** Moves the item of an emote already playing, without restarting the animation. */
    void updateEmoteItemAnchors(@org.jetbrains.annotations.Nullable EmoteItemAnchor itemAnchor,
                                @org.jetbrains.annotations.Nullable EmoteItemAnchor offHandAnchor);

    /** Whether the emote wants held items not drawn at all. */
    boolean isHidingEmoteItems();

    /** Whether a swing leaves this emote running, which also means no swing is drawn over it. */
    boolean isEmoteKeptOnAttack();

    /** Same, for the off hand. */
    @org.jetbrains.annotations.Nullable EmoteItemAnchor getEmoteOffHandAnchor();
}
