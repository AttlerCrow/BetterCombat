package net.bettercombat.client;

import net.bettercombat.network.Packets;

import java.util.function.Consumer;

/**
 * Send hook for the emote studio, filled in by the loader-specific client networking.
 *
 * <p>Common code cannot reach Fabric's networking directly, and the studio has no other reason to
 * know which loader it is running on.
 */
public final class EmoteStudioNetwork {

    private static Consumer<Packets.C2S_EmoteAnchor> sender = packet -> { };
    private static Consumer<Packets.C2S_EmoteStudioState> stateSender = packet -> { };
    private static Consumer<Packets.C2S_EmoteFreeLook> freeLookSender = packet -> { };

    private EmoteStudioNetwork() {
    }

    public static void setSender(Consumer<Packets.C2S_EmoteAnchor> sender) {
        EmoteStudioNetwork.sender = sender;
    }

    public static void setStateSender(Consumer<Packets.C2S_EmoteStudioState> sender) {
        EmoteStudioNetwork.stateSender = sender;
    }

    public static void setFreeLookSender(Consumer<Packets.C2S_EmoteFreeLook> sender) {
        EmoteStudioNetwork.freeLookSender = sender;
    }

    /** Tells the server the view has come loose from the heading. */
    public static void sendFreeLook(boolean active) {
        freeLookSender.accept(new Packets.C2S_EmoteFreeLook(active));
    }

    /** Tells the server to hold the emote unconditionally while the studio is open. */
    public static void sendStudioState(boolean open) {
        stateSender.accept(new Packets.C2S_EmoteStudioState(open));
    }

    public static void sendAnchor(boolean offHand, float x, float y, float z,
                                  float pitch, float yaw, float roll) {
        sender.accept(new Packets.C2S_EmoteAnchor(offHand, x, y, z, pitch, yaw, roll));
    }
}
