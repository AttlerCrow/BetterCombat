package net.bettercombat.client.menu;

import com.google.gson.Gson;
import net.bettercombat.network.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;

/**
 * Routes menu packets to the right screen, and screen actions back to the server.
 *
 * <p>The send hook is filled in by the loader-specific client networking, the same way
 * {@link net.bettercombat.client.EmoteStudioNetwork} does it: common code cannot reach Fabric's
 * networking, and a screen has no reason to know which loader it is running on.
 *
 * <p>This class keeps its own reference to the screen it opened. There is no public accessor for the
 * open screen in this version, and closing blindly would dismiss whatever the player happens to have
 * up - an inventory, a sign, someone else's GUI - the moment a stale close arrived.
 */
public final class ClientMenuNetwork {

    private static final Gson GSON = new Gson();

    private static Consumer<Packets.C2S_MenuAction> sender = packet -> { };

    /** The screen this class opened, or null. Ours to close; nothing else is. */
    private static Screen openScreen;
    private static String openMenuId;

    private ClientMenuNetwork() {
    }

    public static void setSender(Consumer<Packets.C2S_MenuAction> sender) {
        ClientMenuNetwork.sender = sender;
    }

    // ---------------------------------------------------------------------------------------------
    // Incoming
    // ---------------------------------------------------------------------------------------------

    public static void handleOpen(Packets.S2C_MenuOpen packet) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            Screen screen = build(packet.menuId(), packet.json());
            if (screen == null) {
                return;
            }
            openScreen = screen;
            openMenuId = packet.menuId();
            client.setScreenAndShow(screen);
        });
    }

    /**
     * Hands new state to a screen that is already up.
     *
     * <p>Ignored when that menu is not open. An update is always a follow-up to something the player
     * did in the screen, so arriving after they closed it means the answer is no longer wanted -
     * opening the screen to deliver it would yank them back into a menu they just left.
     */
    public static void handleUpdate(Packets.S2C_MenuUpdate packet) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (!packet.menuId().equals(openMenuId) || openScreen == null) {
                return;
            }
            if (openScreen instanceof MenuScreen menu) {
                menu.applyState(packet.json());
            }
        });
    }

    public static void handleClose(Packets.S2C_MenuClose packet) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (packet.menuId().equals(openMenuId)) {
                close(client);
            }
        });
    }

    private static Screen build(String menuId, String json) {
        try {
            return switch (menuId) {
                case EmoteWheelScreen.MENU_ID -> EmoteWheelScreen.of(json, GSON);
                case EmoteEditorScreen.MENU_ID -> EmoteEditorScreen.of(json, GSON);
                // A menu this build does not know about. Not an error: the server may be newer than
                // the client, and silently not opening is better than a crash on login day.
                default -> null;
            };
        } catch (RuntimeException malformed) {
            // Never let a bad payload take the game down. The screen simply does not open, which is
            // the same outcome as not having the mod.
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Outgoing
    // ---------------------------------------------------------------------------------------------

    public static void sendAction(String menuId, String actionJson) {
        sender.accept(new Packets.C2S_MenuAction(menuId, false, actionJson));
    }

    /**
     * Tells the server the screen went away.
     *
     * <p>Sent from the screen's own removal rather than from wherever it was closed, because escape,
     * a command, a death screen and opening an inventory all close it and only the screen sees all
     * of them.
     */
    public static void sendClosed(String menuId) {
        if (menuId.equals(openMenuId)) {
            openScreen = null;
            openMenuId = null;
        }
        sender.accept(new Packets.C2S_MenuAction(menuId, true, "{\"action\":\"close\"}"));
    }

    /** Closes the menu this class opened, if any. */
    public static void close(Minecraft client) {
        if (openScreen == null) {
            return;
        }
        openScreen = null;
        openMenuId = null;
        client.setScreenAndShow(null);
    }

    public static boolean isOpen() {
        return openScreen != null;
    }

    /** A screen that can be handed replacement state without being reopened. */
    public interface MenuScreen {
        void applyState(String json);
    }
}
