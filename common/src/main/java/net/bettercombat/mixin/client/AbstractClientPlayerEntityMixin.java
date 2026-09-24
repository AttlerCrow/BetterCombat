package net.bettercombat.mixin.client;

import com.mojang.authlib.GameProfile;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.ExtraAnimationData;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import net.bettercombat.BetterCombatMod;
import net.bettercombat.Platform;
import net.bettercombat.api.EntityPlayer_BetterCombat;
import net.bettercombat.api.fx.ParticlePlacement;
import net.bettercombat.api.fx.TrailAppearance;
import net.bettercombat.client.BetterCombatClientMod;
import net.bettercombat.client.animation.*;
import net.bettercombat.client.particle.SlashParticleUtil;
import net.bettercombat.logic.AnimatedHand;
import net.bettercombat.logic.PlayerAttackHelper;
import net.bettercombat.mixin.player.LivingEntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerEntityMixin extends Player implements PlayerAttackAnimatable {
    private AttackAnimationStack attackAnimation;
    private EmoteAnimationStack emoteAnimation;
    private boolean emoteHidesPose = false;
    private EmoteItemAnchor emoteItemAnchor = null;
    private EmoteItemAnchor emoteOffHandAnchor = null;
    private boolean emotePhotoCamera = false;
    private float emoteCameraHeightOffset = 0F;
    private boolean emoteHidesItems = false;
    private boolean emoteKeepOnAttack = false;
    private float emoteFrozenBodyYaw;
    /** The heading the emote was started on, held for everybody watching. */
    private boolean emoteLockBody = false;
    private float emoteBodyYaw = 0F;
    private boolean emoteFreeLookHeld = false;
    private net.minecraft.client.CameraType emotePreviousCamera = null;
    /**
     * Last tick of the skill animation playing on the attack layer, or -1 when what is playing is a
     * plain swing (or nothing). A tick count and not the layer's own state, which on another player
     * has answered inactive while the animation was plainly on screen.
     */
    private int skillAnimationEndTick = -1;
    private PoseAnimationStack mainHandBodyPose;
    private PoseAnimationStack mainHandItemPose;
    private PoseAnimationStack offHandBodyPose;
    private PoseAnimationStack offHandItemPose;

    public AbstractClientPlayerEntityMixin(Level world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void postInit(ClientLevel world, GameProfile profile, CallbackInfo ci) {
        var player = (AbstractClientPlayer) ((Object) this);

        // Initialize attack animation
        attackAnimation = (AttackAnimationStack) PlayerAnimationAccess.getPlayerAnimationLayer(player, AttackAnimationStack.ID);
        emoteAnimation = (EmoteAnimationStack) PlayerAnimationAccess.getPlayerAnimationLayer(player, EmoteAnimationStack.ID);
        mainHandBodyPose = (PoseAnimationStack) PlayerAnimationAccess.getPlayerAnimationLayer(player, PoseAnimationStack.MAIN_HAND_BODY_ID);
        mainHandItemPose = (PoseAnimationStack) PlayerAnimationAccess.getPlayerAnimationLayer(player, PoseAnimationStack.MAIN_HAND_ITEM_ID);
        offHandBodyPose = (PoseAnimationStack) PlayerAnimationAccess.getPlayerAnimationLayer(player, PoseAnimationStack.OFF_HAND_BODY_ID);
        offHandItemPose = (PoseAnimationStack) PlayerAnimationAccess.getPlayerAnimationLayer(player, PoseAnimationStack.OFF_HAND_ITEM_ID);
    }

    /** Flight-path aim, degrees nose down, this tick and last; see {@link net.bettercombat.client.animation.DashAimHolder}. */
    @org.spongepowered.asm.mixin.Unique
    private float bettercombat$dashAim, bettercombat$dashAimO;
    /** How quickly the aim follows the flight path, and lets go of it after, per tick. */
    @org.spongepowered.asm.mixin.Unique
    private static final float DASH_AIM_FOLLOW = 0.35F;

    @Override
    public void updateAnimationsOnTick() {
        var instance = (Object)this;
        var player = (Player)instance;
        bettercombat$tickDashAim(player);
        var isLeftHanded = isLeftHanded();
        var hasActiveAttackAnimation = attackAnimation.isActive(); // attackAnimation.base.getAnimation() != null && attackAnimation.base.getAnimation().isActive();
        var mainHandStack = player.getMainHandItem();
        // No pose during special activities

        // Hold the model still while the camera is free to circle it.
        //
        // Two ways in: an emote configured for photography, and the right button held during any
        // emote. Freezing the heading on the server was not enough on its own - the body still
        // turned to follow the view, so looking behind you spun the character on the spot.
        boolean freeLook = net.bettercombat.client.studio.EmotePhotoCamera.isFreeLook()
                && Minecraft.getInstance().player == player;
        if ((emotePhotoCamera || freeLook) && emoteAnimation.isActive()) {
            if (freeLook && !emotePhotoCamera) {
                // Free look is momentary, so the angle to hold is taken the first tick it begins.
                if (!emoteFreeLookHeld) {
                    emoteFrozenBodyYaw = player.yBodyRot;
                    emoteFreeLookHeld = true;
                }
            }
            player.yBodyRot = emoteFrozenBodyYaw;
            player.yBodyRotO = emoteFrozenBodyYaw;
            player.yHeadRot = emoteFrozenBodyYaw;
            player.yHeadRotO = emoteFrozenBodyYaw;
        } else {
            emoteFreeLookHeld = false;
            // The heading the emote was started on, held on every client that can see it.
            //
            // Only the body, never the head: a seated player should still be able to look around,
            // and pinning the head would nail their view to the chair. Both the current and the
            // previous value, or the renderer spends the tick interpolating away from what was
            // just set and the model wobbles.
            //
            // This is the whole fix for a pose facing the wrong way on other screens. A watcher
            // derives a body heading from movement, so somebody who turned on the spot and then
            // sat down was drawn still facing wherever they last walked.
            // Held on our own flag, not on whether the animation library says something is
            // playing. Asking it was the whole reason this never ran: the heading has to be held
            // from the moment the packet lands until the emote is stopped, and that window is
            // ours to know - the library has its own idea of when a layer counts as active, and
            // on another player it answered no while the pose was plainly on screen.
            if (emoteLockBody) {
                player.yBodyRot = emoteBodyYaw;
                player.yBodyRotO = emoteBodyYaw;
            }
        }

        if (scheduledParticles != null && scheduledParticles.time() == player.tickCount) {
            SlashParticleUtil.spawnParticles(scheduledParticles.args());
            scheduledParticles = null;
        }

        // Holding up a weapon that blocks - a parrying katana - is not an activity that replaces the
        // grip: it is the grip. Clearing the pose for it meant letting go restarted the pose from its
        // empty first tick, and for a frame the arm hung at rest with the blade pointing at the ground.
        boolean guardingWithWeapon = player.isUsingItem()
                && player.getUseItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
        if (player.swinging // Official mapping name: `isHandBusy`
                || player.isSwimming()
                || (player.isUsingItem() && !guardingWithWeapon)
                || player.onClimbable()
                || player.isFallFlying()
                || Platform.isCastingSpell(player)
                || CrossbowItem.isCharged(mainHandStack)) {
            // Clear all poses during special activities
            mainHandBodyPose.setPose(null, isLeftHanded);
            mainHandItemPose.setPose(null, isLeftHanded);
            offHandBodyPose.setPose(null, isLeftHanded);
            offHandItemPose.setPose(null, isLeftHanded);
            return;
        }

        // Restore auto body rotation upon swing - Fix issue #11
        if (hasActiveAttackAnimation) {
            ((LivingEntityAccessor)player).invokeTurnHead(player.getYHeadRot());
        }

        // Pose animations
        var betterCombatPlayer = (EntityPlayer_BetterCombat)player;

        String newMainHandPoseId = null;
        String newOffHandPoseId = null;

        if (Minecraft.getInstance().player == player) {
            // Logic on local player too for improved responsiveness
            var pose = PlayerAttackHelper.poseForPlayer(player);
            if (!pose.base().isEmpty()) {
                newMainHandPoseId = pose.base();
            }
            if (!pose.offHand().isEmpty()) {
                newOffHandPoseId = pose.offHand();
            }
        } else {
            // For other players, prefer the synced animation IDs.
            String syncedMainHand = betterCombatPlayer.getMainHandIdleAnimation();
            String syncedOffHand = betterCombatPlayer.getOffHandIdleAnimation();
            if (syncedMainHand != null && !syncedMainHand.isEmpty()) {
                newMainHandPoseId = syncedMainHand;
            }
            if (syncedOffHand != null && !syncedOffHand.isEmpty()) {
                newOffHandPoseId = syncedOffHand;
            }

            // Fall back to resolving the pose locally when nothing was synced.
            //
            // The synced ids ride on entity attachments, which only the mod's own server sets. On a
            // server that speaks the protocol without running the mod - a Paper bridge - they are
            // always empty, so every other player was drawn holding their weapon the vanilla way
            // while holding it correctly on their own screen. The client already has the full weapon
            // registry and can see what the other player is holding, so it can work the pose out by
            // itself; this is the same call the local player branch above makes.
            if (newMainHandPoseId == null && newOffHandPoseId == null) {
                var pose = PlayerAttackHelper.poseForPlayer(player);
                if (!pose.base().isEmpty()) {
                    newMainHandPoseId = pose.base();
                }
                if (!pose.offHand().isEmpty()) {
                    newOffHandPoseId = pose.offHand();
                }
            }
        }

        // Update item poses (always active when pose is set).
        //
        // Deliberately BEFORE the emote suppression below. An emote owns the arms, so the body half
        // of the weapon pose has to go or the two fight over the same bones - but the item half is
        // what holds the weapon in the hand at all. Clearing it too drops the weapon back to its
        // vanilla grip, which on a modelled weapon reads as the blade floating beside the hand.
        mainHandItemPose.setPose(newMainHandPoseId, isLeftHanded);
        offHandItemPose.setPose(newOffHandPoseId, isLeftHanded);

        if (emoteHidesPose && emoteAnimation.isActive()) {
            newMainHandPoseId = null;
            newOffHandPoseId = null;
        }

        // Update body poses (disabled during walking/sneaking for non-two-handed weapons)
        if (!PlayerAttackHelper.isTwoHandedWielding(player)) {
            if (this.isWalking() || this.isShiftKeyDown()) {
                newMainHandPoseId = null;
                newOffHandPoseId = null;
            }
        }
        mainHandBodyPose.setPose(newMainHandPoseId, isLeftHanded);
        offHandBodyPose.setPose(newOffHandPoseId, isLeftHanded);
    }

    @Override
    public void playAttackAnimation(String name, AnimatedHand animatedHand, float length, float upswing) {
        startAttackAnimation(name, animatedHand, length, upswing, false);
    }

    @Override
    public void playForcedAnimation(String name, AnimatedHand animatedHand, float length, float upswing) {
        startAttackAnimation(name, animatedHand, length, upswing, true);
    }

    /** Ticks a server animation takes to blend in from the one it replaces. */
    @Unique
    private static final int FORCED_ANIMATION_FADE_TICKS = 3;

    /**
     * Ticks an emote takes to blend in when it replaces one still playing. A held pose that changes
     * state - a glide turning into a dive - would otherwise restart from rest and drop the arms.
     */
    @Unique
    private static final int EMOTE_SWITCH_FADE_TICKS = 4;

    @Unique
    private void startAttackAnimation(String name, AnimatedHand animatedHand, float length, float upswing,
                                      boolean fadeFromCurrent) {
        // Whatever plays next is a swing until the forced-animation path says otherwise.
        skillAnimationEndTick = -1;
        // A swing always wins over an emote. Cancelling here rather than relying on layer priority
        // keeps it instant - no round trip to the server - and covers the swing the client started
        // itself, which the server never hears about until the attack request lands.
        //
        // Except while the studio is open, where the emote is the thing being worked on and must
        // outlast anything that would normally end it.
        if (!net.bettercombat.client.studio.EmoteStudio.isActive() && !emoteKeepOnAttack) {
            stopEmoteAnimation();
        }
        try {
            var controller = attackAnimation;
            var animation = PlayerAnimResources.getAnimation(Identifier.parse(name));

            var endTick = animation.data().<Float>get(ExtraAnimationData.END_TICK_KEY).orElse(animation.length());
            var speed = endTick / length;
            var mirror = animatedHand.isOffHand();
            if(isLeftHanded()) {
                mirror = !mirror;
            }
            var trueUpswingRatio = upswing / BetterCombatMod.config.getUpswingMultiplier();
            float upswingSpeed = speed / trueUpswingRatio;
            float downwindSpeed = (float) (speed *
                    Mth.lerp(Math.max(BetterCombatMod.config.getUpswingMultiplier() - 0.5, 0) / 0.5, // Choosing value :D
                            (1F - upswing),                     // Use this value at config `0.5`
                            upswing / (1F - upswing)));         // Use this value at config `1.0`

            var fistPersonConfig = firstPersonConfig(animatedHand);
            if (animatedHand == AnimatedHand.OFF_HAND) {
                fistPersonConfig = FirstPersonHelper.mirrored(fistPersonConfig);
            }
            controller.activeFirstPersonConfig = fistPersonConfig;
            controller.speed.speed = speed;
            controller.mirror.enabled = mirror;
            attackAnimation.speed.set(upswingSpeed,
                    List.of(
                            new TransmissionSpeedModifier.Gear(length * upswing, downwindSpeed),
                            new TransmissionSpeedModifier.Gear(length, speed)
                    ));

            if (fadeFromCurrent && controller.isActive()) {
                controller.replaceAnimationWithFade(
                        com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier.standardFadeIn(
                                FORCED_ANIMATION_FADE_TICKS, com.zigythebird.playeranimcore.easing.EasingType.EASE_OUT_QUAD),
                        animation);
            } else {
                controller.triggerAnimation(animation);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Nullable private SlashParticleUtil.ScheduledSpawnArgs scheduledParticles = null;

    @Override
    public void playAttackParticles(boolean isOffHand, float weaponRange, int delay, List<ParticlePlacement> particles, TrailAppearance appearance) {
        var player = (AbstractClientPlayer)(Object)this;
        var spawn = new SlashParticleUtil.SpawnArgs(
                player,
                isOffHand,
                weaponRange,
                particles,
                appearance
        );
        scheduledParticles = new SlashParticleUtil.ScheduledSpawnArgs(
                spawn,
                player.tickCount + delay
        );
    }

    private boolean isWalking() {
        return !this.isDeadOrDying() && (this.isSwimming() || this.getDeltaMovement().horizontalDistance() > 0.03);
    }

    public boolean isLeftHanded() {
        return this.getMainArm() == HumanoidArm.LEFT;
    }

    // PlayerAttackAnimatable

    @Override
    public void stopAttackAnimation(float length) {
        scheduledParticles = null;
        skillAnimationEndTick = -1;
        if (attackAnimation.isActive()) {
            attackAnimation.stop();
        }
    }

    @Override
    public void playEmoteAnimation(String name, float length, boolean hidePose,
                                   boolean photoCamera, boolean hideItems, boolean thirdPerson,
                                   boolean keepOnAttack,
                                   float cameraHeightOffset,
                                   EmoteItemAnchor itemAnchor, EmoteItemAnchor offHandAnchor,
                                   boolean lockBody, float bodyYaw) {
        emoteLockBody = lockBody;
        emoteBodyYaw = bodyYaw;
        if (lockBody) {
            // Straight away, and not left to the next tick.
            var self = (Player) (Object) this;
            self.yBodyRot = bodyYaw;
            self.yBodyRotO = bodyYaw;
            // And this is the one that actually mattered. The photo-camera branch below pins the
            // body to emoteFrozenBodyYaw, and that field is only ever filled by startPhotoCamera,
            // which returns early for anybody who is not the local player. On every other screen
            // it therefore held its default of zero - due south - and pinned the pose there every
            // tick. Seeding it from the packet gives that branch a real heading for other people
            // too; the local player still overwrites it a line later with their own.
            emoteFrozenBodyYaw = bodyYaw;
        }
        emoteHidesPose = hidePose;
        emoteCameraHeightOffset = cameraHeightOffset;
        emoteHidesItems = hideItems;
        emoteKeepOnAttack = keepOnAttack;
        startPhotoCamera(photoCamera || thirdPerson, photoCamera);
        emoteItemAnchor = itemAnchor;
        emoteOffHandAnchor = offHandAnchor;
        try {
            var animation = PlayerAnimResources.getAnimation(Identifier.parse(name));
            if (animation == null || length <= 0F) {
                return;
            }

            var endTick = animation.data().<Float>get(ExtraAnimationData.END_TICK_KEY).orElse(animation.length());
            // One gear, so the whole emote plays at one speed. The attack path needs two because a
            // swing has an impact point; an emote does not.
            emoteAnimation.speed.set(endTick / length, List.of());
            if (emoteAnimation.isActive()) {
                // Pose to pose, not through rest: the new emote fades in from what is on screen.
                emoteAnimation.replaceAnimationWithFade(
                        com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier.standardFadeIn(
                                EMOTE_SWITCH_FADE_TICKS, com.zigythebird.playeranimcore.easing.EasingType.EASE_IN_OUT_QUAD),
                        animation);
            } else {
                emoteAnimation.triggerAnimation(animation);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isHidingEmoteItems() {
        return emoteAnimation.isActive() && emoteHidesItems;
    }

    @Override
    public float getEmoteCameraHeightOffset() {
        return emoteAnimation.isActive() ? emoteCameraHeightOffset : 0F;
    }

    @Override
    public boolean isEmoteActive() {
        return emoteAnimation.isActive();
    }

    @Override
    public void markSkillAnimation(float lengthTicks) {
        skillAnimationEndTick = this.tickCount + (int) Math.ceil(Math.max(0F, lengthTicks)) + 1;
    }

    @Override
    public boolean isSkillAnimationActive() {
        return skillAnimationEndTick >= 0 && this.tickCount <= skillAnimationEndTick;
    }

    @Override
    public boolean isEmotePinned() {
        // Our flag, not the library's active layer, for the same reason as the facing: it is set when
        // the packet lands and cleared in stopEmoteAnimation. That is the window the server holds the
        // pin, outro included - the outro arrives with the flag too, and the final stop is sent by the
        // same task that releases the lock.
        return emoteLockBody;
    }

    @Override
    public boolean isEmoteKeptOnAttack() {
        return emoteKeepOnAttack;
    }

    @Override
    public float getDashAimPitch(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, bettercombat$dashAimO, bettercombat$dashAim);
    }

    /**
     * Follows the direction the player is actually flying, while an emote asks for it: the pitch of
     * the line of this tick's movement, eased in. The line, not the arrow: flying backwards, feet
     * first, the body lies along the path the same way, so rising while going back puts the head
     * down. Out of such an emote it eases back to level instead of snapping, so the dash hands over
     * to the glide without a jolt. Movement is read from the entity's own positions, so it works
     * the same for every player on screen.
     */
    @org.spongepowered.asm.mixin.Unique
    private void bettercombat$tickDashAim(Player player) {
        bettercombat$dashAimO = bettercombat$dashAim;
        float target = 0F;
        if (emoteAnimation.isActive() && emoteAnimation.wantsDashAim()) {
            double dx = player.getX() - player.xo;
            double dy = player.getY() - player.yo;
            double dz = player.getZ() - player.zo;
            // Movement along the way the body faces: negative flying backwards.
            double yaw = Math.toRadians(player.yBodyRot);
            double along = -Math.sin(yaw) * dx + Math.cos(yaw) * dz;
            // Barely moving says nothing about a direction; hold what there is.
            if (along * along + dy * dy > 0.01D) {
                target = (float) Math.toDegrees(Math.atan2(along >= 0 ? -dy : dy, Math.abs(along)));
            } else {
                target = bettercombat$dashAim;
            }
            target = net.minecraft.util.Mth.clamp(target, -80F, 80F);
        }
        bettercombat$dashAim += (target - bettercombat$dashAim) * DASH_AIM_FOLLOW;
    }

    public EmoteItemAnchor getEmoteItemAnchor() {
        // Only while something is actually playing: a stale anchor would keep the weapon pinned to
        // the player's back long after the emote ended.
        return emoteAnimation.isActive() ? emoteItemAnchor : null;
    }

    @Override
    public void updateEmoteItemAnchors(EmoteItemAnchor itemAnchor, EmoteItemAnchor offHandAnchor) {
        emoteItemAnchor = itemAnchor;
        emoteOffHandAnchor = offHandAnchor;
    }

    @Override
    public EmoteItemAnchor getEmoteOffHandAnchor() {
        return emoteAnimation.isActive() ? emoteOffHandAnchor : null;
    }

    /**
     * Pins the model's facing and pulls the view out to third person.
     *
     * <p>Only the body and head yaw are frozen, never the player's own look angle: the camera still
     * turns with the mouse, so the pose can be circled and photographed, while the subject holds
     * still instead of swinging round to stare down the lens.
     */
    @Unique
    private void startPhotoCamera(boolean thirdPerson, boolean freezeFacing) {
        var client = Minecraft.getInstance();
        var self = (Player) (Object) this;
        // Two separate things, deliberately: pulling the camera out, and pinning which way the
        // model faces. A line wants the first for everybody and the second for nobody.
        emotePhotoCamera = freezeFacing;
        if (!thirdPerson || client.player != self) {
            return;
        }
        emoteFrozenBodyYaw = self.yBodyRot;
        // Only remembered when we are the ones changing it. Someone already in third person keeps
        // whichever third person they chose, and an F5 during the pose is theirs to keep too.
        if (client.options.getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON) {
            emotePreviousCamera = net.minecraft.client.CameraType.FIRST_PERSON;
            client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        } else {
            emotePreviousCamera = null;
        }
    }

    @Unique
    private void endPhotoCamera() {
        var client = Minecraft.getInstance();
        if (client.player == (Object) this) {
            if (emotePhotoCamera && emotePreviousCamera != null) {
                client.options.setCameraType(emotePreviousCamera);
            }
            net.bettercombat.client.studio.EmotePhotoCamera.reset();
        }
        emotePhotoCamera = false;
        emotePreviousCamera = null;
    }

    @Override
    public void stopEmoteAnimation() {
        endPhotoCamera();
        emoteHidesPose = false;
        emoteLockBody = false;
        emoteCameraHeightOffset = 0F;
        emoteHidesItems = false;
        emoteKeepOnAttack = false;
        emoteItemAnchor = null;
        emoteOffHandAnchor = null;
        // Both, in this order. Clearing the triggered animation is what ends it cleanly rather than
        // freezing on the last frame - but on its own it does not settle a looping one, which kept
        // dancing after the player had left. The pose emotes never showed this because they have an
        // outro: playing a second animation replaced the first and hid the problem.
        emoteAnimation.stopTriggeredAnimation();
        if (emoteAnimation.isActive()) {
            emoteAnimation.stop();
        }
    }

    // FirstPersonAnimator

    private FirstPersonConfiguration firstPersonConfig(AnimatedHand animatedHand) {
        // boolean leftHanded = getMainArm() == Arm.LEFT;
        var showRightItem = true;
        var showLeftItem = BetterCombatClientMod.config.isShowingOtherHandFirstPerson || animatedHand == AnimatedHand.TWO_HANDED;
        var showRightArm = showRightItem && BetterCombatClientMod.config.isShowingArmsInFirstPerson;
        var showLeftArm = showLeftItem && BetterCombatClientMod.config.isShowingArmsInFirstPerson;

        var config = new FirstPersonConfiguration(showRightArm, showLeftArm, showRightItem, showLeftItem);
        // System.out.println("Animation config: " + config);
        return config;
    }
}
