package net.bettercombat.fabric.client;

import net.bettercombat.client.ClientNetwork;
import net.bettercombat.fabric.network.FabricServerNetwork;
import net.bettercombat.network.Packets;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class FabricClientNetwork {
    public static void init() {
        ClientConfigurationNetworking.registerGlobalReceiver(Packets.WeaponRegistrySync.PACKET_ID, (packet, context) -> {
            ClientNetwork.handleWeaponRegistrySync(packet);
            context.responseSender().sendPacket(new Packets.Ack(FabricServerNetwork.WeaponRegistrySyncTask.name));
        });

        ClientConfigurationNetworking.registerGlobalReceiver(Packets.ConfigSync.PACKET_ID, (packet, context) -> {
            ClientNetwork.handleConfigSync(packet);
            context.responseSender().sendPacket(new Packets.Ack(FabricServerNetwork.ConfigurationTask.name));
        });

        // Handshake over PLAY, for servers that cannot deliver it during configuration (see
        // FabricServerNetwork). Handling it twice is harmless: both handlers are idempotent.
        ClientPlayNetworking.registerGlobalReceiver(Packets.ConfigSync.PACKET_ID, (packet, context) -> {
            ClientNetwork.handleConfigSync(packet);
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.WeaponRegistrySync.PACKET_ID, (packet, context) -> {
            ClientNetwork.handleWeaponRegistrySync(packet);
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.AttackAnimation.PACKET_ID, (packet, context) -> {
            ClientNetwork.handleAttackAnimation(packet);
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.ForcedAnimation.PACKET_ID, (packet, context) -> {
            ClientNetwork.handleForcedAnimation(packet);
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.CombatState.PACKET_ID, (packet, context) -> {
            ClientNetwork.handleCombatState(packet);
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.PlayEmote.PACKET_ID, (packet, context) -> {
            ClientNetwork.handlePlayEmote(packet);
        });

        ClientPlayNetworking.registerGlobalReceiver(Packets.S2C_EmoteStudio.PACKET_ID, (packet, context) -> {
            ClientNetwork.handleEmoteStudio(packet);
        });

        net.bettercombat.client.EmoteStudioNetwork.setSender(ClientPlayNetworking::send);
        net.bettercombat.client.EmoteStudioNetwork.setStateSender(ClientPlayNetworking::send);
        net.bettercombat.client.EmoteStudioNetwork.setFreeLookSender(ClientPlayNetworking::send);

        ClientPlayNetworking.registerGlobalReceiver(Packets.AttackSound.PACKET_ID, (packet, context) -> {
            ClientNetwork.handleAttackSound(packet);
        });

    }
}
