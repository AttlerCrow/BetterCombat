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
    private boolean emoteFreeLookHeld = false;
    private net.minecraft.client.CameraType emotePreviousCamera = null;
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

    @Override
    public void updateAnimationsOnTick() {
        var instance = (Object)this;
        var player = (Player)instance;
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
        }

        if (scheduledParticles != null && scheduledParticles.time() == player.tickCount) {
            SlashParticleUtil.spawnParticles(scheduledParticles.args());
            scheduledParticles = null;
        }

        if (player.swinging // Official mapping name: `isHandBusy`
                || player.isSwimming()
                || player.isUsingItem()
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

            controller.triggerAnimation(animation);
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
        if (attackAnimation.isActive()) {
            attackAnimation.stop();
        }
    }

    @Override
    public void playEmoteAnimation(String name, float length, boolean hidePose,
                                   boolean photoCamera, boolean hideItems, boolean thirdPerson,
                                   boolean keepOnAttack,
                                   float cameraHeightOffset,
                                   EmoteItemAnchor itemAnchor, EmoteItemAnchor offHandAnchor) {
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
            emoteAnimation.triggerAnimation(animation);
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
