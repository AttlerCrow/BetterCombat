package net.bettercombat.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.bettercombat.Platform;
import net.bettercombat.network.Packets;
import net.minecraft.client.Minecraft;

/**
 * Tells the server that the attack or use button was pressed.
 *
 * <p>Everything else this mod sends describes an attack. This describes a <em>click</em>, which is a
 * different thing and the one a server cannot otherwise see: once a weapon is registered,
 * {@link AttackInteractor} cancels the vanilla attack, so no swing packet and no block action ever
 * leave the client, and it cancels item use during an upswing, so a right click inside one leaves
 * nothing either. NightFantasy's skill combos are typed as sequences of clicks, and without this the
 * server can only guess at them from the damage that happens to land.
 *
 * <p>The press is taken from {@code KeyMapping.click}, which vanilla calls once per physical press
 * and only when no screen has taken the click. Everything else was tried and is worse: the attack
 * hooks never fire for a click the weapon cooldown rejects, item use repeats itself while the button
 * is held, and polling {@code isDown()} once a tick misses a click that starts and ends inside one.
 *
 * <p>Presses are queued and flushed at the head of the client tick, ahead of vanilla's own
 * {@code handleKeybinds}. That ordering is load-bearing on the server: the press arrives before the
 * interact packet the same click may produce, which is what lets the server tell the two apart
 * instead of counting one click twice.
 */
public final class CombatInputReporter {

    /**
     * Presses kept for one tick. Three is a combo; anything past this is a held button reading as a
     * stutter or an auto-clicker, and neither is worth a packet.
     */
    private static final int MAX_PENDING = 8;

    private static final int[] PENDING = new int[MAX_PENDING];
    private static int pendingCount;
    private static int sequence;

    private CombatInputReporter() {
    }

    /** A key was pressed. Called for every binding, so the button has to be identified here. */
    public static void onKeyClicked(InputConstants.Key key) {
        if (!BetterCombatClientMod.ENABLED) {
            // No handshake: either a vanilla server or one that does not speak this protocol, and
            // sending it an unknown payload would be rude at best.
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.options == null) {
            return;
        }

        int button;
        if (client.options.keyAttack.matches(key)) {
            button = Packets.C2S_CombatInput.BUTTON_LEFT;
        } else if (client.options.keyUse.matches(key)) {
            button = Packets.C2S_CombatInput.BUTTON_RIGHT;
        } else {
            return;
        }

        synchronized (PENDING) {
            if (pendingCount < MAX_PENDING) {
                PENDING[pendingCount++] = button;
            }
        }
    }

    /** Sends what was pressed since the last tick. Called at the head of the client tick. */
    public static void flush() {
        if (pendingCount == 0) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        boolean connected = BetterCombatClientMod.ENABLED
                && client != null
                && client.player != null
                && client.getConnection() != null;

        int count;
        int[] buttons = new int[MAX_PENDING];
        synchronized (PENDING) {
            count = pendingCount;
            System.arraycopy(PENDING, 0, buttons, 0, count);
            pendingCount = 0;
        }
        if (!connected) {
            // Dropped rather than held: a press made while disconnecting describes nothing that is
            // still true by the time a connection exists again.
            return;
        }
        for (int i = 0; i < count; i++) {
            Platform.networkC2S_Send(new Packets.C2S_CombatInput(buttons[i], ++sequence));
        }
    }

    /** Forgets everything on disconnect, so the next session starts its count at one. */
    public static void reset() {
        synchronized (PENDING) {
            pendingCount = 0;
        }
        sequence = 0;
    }
}
