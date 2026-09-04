package net.bettercombat.client.menu;

import net.bettercombat.client.Keybindings;
import net.minecraft.client.Minecraft;

/**
 * Turns the wheel key into a request for the wheel.
 *
 * <p>The key is the client's, but what goes on the wheel is not: the arrangement lives in the
 * database, and which emotes are unlocked is a question only the server can answer. So the key does
 * not open anything - it asks, and the server replies by opening the screen.
 *
 * <p>That costs a round trip before the wheel appears. It buys a wheel that is never stale: an emote
 * unlocked a minute ago on another server is on it, and one revoked is not. If the delay ever reads
 * as sluggish, the fix is to push the wheel once when the client announces its channels and keep it
 * in step from there - not to let the client decide what it may perform.
 */
public final class ClientMenuKeys {

    private static final String OPEN_ACTION = "{\"action\":\"open\"}";

    private ClientMenuKeys() {
    }

    public static void tick(Minecraft client) {
        if (client.player == null) {
            return;
        }
        boolean pressed = false;
        // Drained rather than read once: a press that arrived while a screen was up would otherwise
        // sit in the queue and open the wheel the moment the player closed something else.
        while (Keybindings.emoteWheelKeyBinding.consumeClick()) {
            pressed = true;
        }
        if (!pressed || ClientMenuNetwork.isOpen()) {
            return;
        }
        ClientMenuNetwork.sendAction(EmoteWheelScreen.MENU_ID, OPEN_ACTION);
    }
}
