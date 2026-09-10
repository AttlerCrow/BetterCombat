package net.bettercombat.client;

import net.bettercombat.BetterCombatMod;
import net.bettercombat.Platform;
import net.bettercombat.client.animation.PlayerAttackAnimatable;
import net.bettercombat.logic.AnimatedHand;
import net.bettercombat.logic.WeaponRegistry;
import net.bettercombat.network.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

public class ClientNetwork {
    public static void handleWeaponRegistrySync(Packets.WeaponRegistrySync packet) {
        WeaponRegistry.decodeRegistry(packet);
    }

    public static void handleConfigSync(Packets.ConfigSync packet) {
        BetterCombatMod.LOGGER.info("Received config sync packet");
        BetterCombatMod.config = packet.deserialized();
        BetterCombatClientMod.ENABLED = true;
    }

    public static void handleAttackAnimation(Packets.AttackAnimation packet) {
        var client = Minecraft.getInstance();
        client.execute(() -> {
            var entity = client.level.getEntity(packet.playerId());
            if (entity instanceof Player player
                    // Avoid local playback, unless replay mod is loaded
                    && (player != client.player || Platform.isModLoaded("replaymod")) ) {
                var animatable = (PlayerAttackAnimatable) entity;
                if (packet.animationName().equals(Packets.AttackAnimation.StopSymbol)) {
                    animatable.stopAttackAnimation(packet.length());
                } else {
                    animatable.playAttackAnimation(packet.animationName(), packet.animatedHand(), packet.length(), packet.upswing());
                    animatable.playAttackParticles(
                            packet.animatedHand() == AnimatedHand.OFF_HAND,
                            packet.weaponRange(),
                            packet.upswingTicks(),
                            packet.particles().particles(),
                            packet.particles().appearance()
                    );
                }
            }
        });
    }

    /**
     * Plays an animation the server asked for, on any player including the local one.
     *
     * <p>Deliberately without the {@code player != client.player} guard that
     * {@link #handleAttackAnimation} needs: nothing was played locally beforehand, so skipping the
     * local player would mean the one who triggered the skill is the only one who never sees it.
     */
    public static void handleForcedAnimation(Packets.ForcedAnimation packet) {
        var client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.level == null) {
                return;
            }
            var entity = client.level.getEntity(packet.playerId());
            if (entity instanceof Player) {
                var animatable = (PlayerAttackAnimatable) entity;
                if (packet.animationName().equals(Packets.AttackAnimation.StopSymbol)) {
                    animatable.stopAttackAnimation(packet.length());
                } else {
                    animatable.playAttackAnimation(
                            packet.animationName(), packet.animatedHand(), packet.length(), packet.upswing());
                }
            }
        });
    }

    /**
     * Plays or stops an emote the server asked for, on any player including the local one.
     *
     * <p>Same reasoning as {@link #handleForcedAnimation}: nothing was played locally beforehand, so
     * skipping the local player would leave the one who typed the command as the only person who
     * never sees it.
     */
    public static void handlePlayEmote(Packets.PlayEmote packet) {
        var client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.level == null) {
                return;
            }
            var entity = client.level.getEntity(packet.playerId());
            if (entity instanceof Player) {
                var animatable = (PlayerAttackAnimatable) entity;
                if (packet.stop()) {
                    animatable.stopEmoteAnimation();
                } else {
                    var anchor = packet.hasItemAnchor()
                            ? new net.bettercombat.client.animation.EmoteItemAnchor(
                                    packet.itemX(), packet.itemY(), packet.itemZ(),
                                    packet.itemPitch(), packet.itemYaw(), packet.itemRoll())
                            : null;
                    var offAnchor = packet.hasOffHandAnchor()
                            ? new net.bettercombat.client.animation.EmoteItemAnchor(
                                    packet.offX(), packet.offY(), packet.offZ(),
                                    packet.offPitch(), packet.offYaw(), packet.offRoll())
                            : null;
                    if (packet.anchorOnly()) {
                        animatable.updateEmoteItemAnchors(anchor, offAnchor);
                    } else {
                        animatable.playEmoteAnimation(packet.animationName(), packet.length(),
                                packet.hidePose(), packet.photoCamera(), packet.hideItems(), packet.thirdPerson(), packet.keepOnAttack(),
                                packet.cameraHeightOffset(),
                                anchor, offAnchor,
                                packet.lockBody(), packet.bodyYaw());
                    }
                }
            }
        });
    }

    /** Applies a studio action requested by a command. */
    public static void handleEmoteStudio(Packets.S2C_EmoteStudio packet) {
        var client = Minecraft.getInstance();
        client.execute(() -> {
            switch (packet.action()) {
                case Packets.S2C_EmoteStudio.SWITCH_HAND ->
                        net.bettercombat.client.studio.EmoteStudio.switchHand(client);
                case Packets.S2C_EmoteStudio.SAVE ->
                        net.bettercombat.client.studio.EmoteStudio.save(client);
                case Packets.S2C_EmoteStudio.CLOSE ->
                        net.bettercombat.client.studio.EmoteStudio.close(client);
                default -> net.bettercombat.client.studio.EmoteStudio.toggle(client);
            }
        });
    }

    public static void handleAttackSound(Packets.AttackSound packet) {
        var client = Minecraft.getInstance();
        client.execute(() -> {
            try {
                if (BetterCombatClientMod.config.weaponSwingSoundVolume == 0) {
                    return;
                }

                var soundEvent = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(packet.soundId()));
                var configVolume = BetterCombatClientMod.config.weaponSwingSoundVolume;
                var volume = packet.volume() * ((float) Math.min(Math.max(configVolume, 0), 100) / 100F);
                client.level.playLocalSound(
                        packet.x(),
                        packet.y(),
                        packet.z(),
                        soundEvent,
                        SoundSource.PLAYERS,
                        volume,
                        packet.pitch(),
                        true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
