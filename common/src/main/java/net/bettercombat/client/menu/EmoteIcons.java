package net.bettercombat.client.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns an icon id from config into a texture that is actually there.
 *
 * <p>The server sends {@code nightfantasy:emote/wave} and never checks whether the file exists - it
 * cannot, the file lives in this jar. So the check happens here, and an emote whose PNG has not been
 * drawn yet falls back to a placeholder.
 *
 * <p>That fallback earns its keep: without it a missing icon renders as the missing-texture
 * chequerboard, which in a grid of twenty emotes reads as the menu being broken rather than as one
 * icon being unfinished. It also means an emote can be added to config before its art exists.
 */
public final class EmoteIcons {

    /** Every icon is drawn at its own size, so nothing is ever scaled. */
    public static final int SIZE = 32;

    public static final Identifier PLACEHOLDER =
            Identifier.fromNamespaceAndPath("nightfantasy", "textures/gui/emote/unknown.png");

    /**
     * Resolved ids, so the resource manager is not asked once per icon per frame.
     *
     * <p>Not invalidated on a resource reload. A pack swap can leave a stale answer until the client
     * restarts, which is the cheapest possible wrong outcome - one icon showing a placeholder it no
     * longer needs - against a lookup on every icon of every frame.
     */
    private static final Map<String, Identifier> RESOLVED = new ConcurrentHashMap<>();

    private EmoteIcons() {
    }

    /**
     * @param icon a namespaced id like {@code nightfantasy:emote/wave}, or null
     * @return the texture to draw, never null
     */
    public static Identifier resolve(String icon) {
        if (icon == null || icon.isBlank()) {
            return PLACEHOLDER;
        }
        return RESOLVED.computeIfAbsent(icon, EmoteIcons::lookUp);
    }

    private static Identifier lookUp(String icon) {
        Identifier texture = toTexture(icon);
        if (texture == null) {
            return PLACEHOLDER;
        }
        Minecraft client = Minecraft.getInstance();
        // Before resources are up there is nothing to ask, and answering "missing" then would cache
        // a placeholder for the whole session.
        if (client == null || client.getResourceManager() == null) {
            return texture;
        }
        return client.getResourceManager().getResource(texture).isPresent() ? texture : PLACEHOLDER;
    }

    private static Identifier toTexture(String icon) {
        String namespace;
        String path;
        int colon = icon.indexOf(':');
        if (colon < 0) {
            namespace = "nightfantasy";
            path = icon;
        } else {
            namespace = icon.substring(0, colon);
            path = icon.substring(colon + 1);
        }
        if (namespace.isEmpty() || path.isEmpty()) {
            return null;
        }
        try {
            return Identifier.fromNamespaceAndPath(namespace, "textures/gui/" + path + ".png");
        } catch (RuntimeException malformed) {
            // The id came off a config file on the server; a typo there must not take the screen down.
            return null;
        }
    }
}
