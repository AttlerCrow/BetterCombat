package net.bettercombat.client.studio;

import net.bettercombat.client.animation.EmoteItemAnchor;
import net.bettercombat.client.animation.PlayerAttackAnimatable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Places an emote's held item by dragging it with the mouse, live on your own model.
 *
 * <p>An anchor is six numbers in a space with an inverted Y axis, composed rotations and a scale of
 * 16 to the block. Nobody arrives at a good value by typing those in - it takes seeing the item move.
 * Here the item follows the mouse and the numbers fall out at the end.
 *
 * <p>Opened with {@code /emote studio}, not a key binding. Bindings were tried first and were the
 * wrong tool: the free letters are already taken by shader and map mods, and a binding that quietly
 * loses to another mod is indistinguishable from a broken feature.
 *
 * <p>Input arrives from {@link EmoteStudioScreen}. Holding it in a screen is what stops attacking,
 * frees the cursor and blocks movement, so the emote cannot be cancelled while it is being adjusted.
 *
 * <p>Drag with the left button to slide the item across the screen, the right button to rotate it,
 * the middle button to push it away or pull it closer. Holding control makes every movement finer -
 * control rather than shift, because shift is the modifier on the studio's own keys and reusing it
 * for two jobs in the same feature invites mistakes. The camera is held still while dragging, so the
 * same mouse motion is not doing two things at once.
 *
 * <p>Deliberately not a GUI screen. A screen would let the item be drawn behind a panel of readouts,
 * but the GUI pipeline in this version is a rewrite whose behaviour cannot be checked without running
 * it; the action bar is already used elsewhere in this mod and needs nothing new to be trusted.
 */
public final class EmoteStudio {

    /** Model units moved per pixel of mouse travel. Tuned so a screen-width drag crosses the body. */
    private static final float MOVE_PER_PIXEL = 0.015F;
    /** Degrees turned per pixel of mouse travel. */
    private static final float TURN_PER_PIXEL = 0.12F;
    /** Multiplier applied while control is held, for the last few units of placement. */
    private static final float FINE = 0.2F;

    private static boolean active;
    private static boolean offHand;
    private static float x, y, z;

    /**
     * Orientation held as a quaternion rather than three angles.
     *
     * <p>Euler angles gimbal lock: with the pitch near ninety degrees - exactly where a blade points
     * straight down - yaw and roll collapse onto the same axis and a whole degree of freedom
     * disappears, which made diagonal and crossed poses unreachable rather than merely awkward.
     * Rotating a quaternion around the axis being dragged has no such singularity, and the config
     * still stores angles: they are read back out only at the end.
     */
    private static final Quaternionf rotation = new Quaternionf();
    private static EmoteStudioScreen screen;

    /** 0 none, 1 X, 2 Y, 3 Z. Constrains a drag to a single axis. */
    private static int lockedAxis;


    private EmoteStudio() {
    }

    public static boolean isActive() {
        return active;
    }


    public static void toggle(Minecraft client) {
        if (active) {
            close(client);
            return;
        }
        active = true;
        net.bettercombat.BetterCombatMod.LOGGER.info("[bc] emote studio ON");
        seedFromPlayer(client);
        net.bettercombat.client.EmoteStudioNetwork.sendStudioState(true);
        screen = new EmoteStudioScreen(client);
        client.setScreenAndShow(screen);
        message(client, "Studio ON - " + hand() + ". Left drag orbits, right moves, middle rotates, "
                + "Shift fine. X/Y/Z lock axis, arrows step position, PgUp/PgDn step rotation, R resets, M mirrors, Tab hand, F5 camera, Enter saves.");
    }

    /**
     * Cycles the axis a drag is constrained to, or releases it when the same axis is pressed twice.
     *
     * <p>This is what a gizmo's coloured arrows are actually for. Dragging two axes at once is
     * uncontrollable for rotation in particular, and constraining the drag fixes that whether or not
     * anything is drawn on screen.
     */
    public static void lockAxis(Minecraft client, int axis) {
        lockedAxis = lockedAxis == axis ? 0 : axis;
        message(client, lockedAxis == 0
                ? "Free drag - both axes"
                : "Locked to " + axisName(lockedAxis) + " only");
    }

    /**
     * Nudges the locked axis by an exact amount.
     *
     * <p>Dragging is good for finding the neighbourhood and bad for landing on a value. Stepping with
     * the arrow keys gives whole numbers and repeatable angles, which is what "exactly there" needs.
     */
    public static void step(Minecraft client, boolean positive, boolean isRotation, boolean fine) {
        float amount = (positive ? 1F : -1F) * (isRotation ? (fine ? 1F : 5F) : (fine ? 0.1F : 0.5F));
        int axis = lockedAxis == 0 ? 1 : lockedAxis;
        if (isRotation) {
            rotateAround(axis, amount);
        } else {
            switch (axis) {
                case 1 -> x += amount;
                case 2 -> y += amount;
                default -> z += amount;
            }
        }
        applyAndReport(client);
    }

    /** Turns around one of the gizmo's axes, in the frame those axes are drawn in. */
    private static void rotateAround(int axis, float degrees) {
        float radians = (float) Math.toRadians(degrees);
        switch (axis) {
            case 1 -> rotation.rotateLocalX(radians);
            case 2 -> rotation.rotateLocalY(radians);
            default -> rotation.rotateLocalZ(radians);
        }
    }

    /**
     * Copies this hand's placement to the other one, mirrored.
     *
     * <p>A crossed pair is symmetric by definition, and matching a second weapon to the first by eye
     * is the hardest thing to do in here - the two are never quite the same and the eye catches it.
     * Mirroring across the body plane is exact: the offset flips side, and the two rotations that
     * change handedness flip with it while the third is unchanged.
     */
    public static void mirrorToOtherHand(Minecraft client) {
        Vector3f angles = rotation.getEulerAnglesXYZ(new Vector3f());

        x = -x;
        rotation.rotationXYZ(angles.x, -angles.y, -angles.z);
        offHand = !offHand;

        applyAndReport(client);
        message(client, "Mirrored onto " + hand() + " - press Enter to record it");
    }

    /** Zeroes the locked axis, for backing out of a value that went wrong. */
    public static void resetAxis(Minecraft client) {
        switch (lockedAxis) {
            case 1 -> x = 0F;
            case 2 -> y = 0F;
            case 3 -> z = 0F;
            default -> {
                x = y = z = 0F;
                rotation.identity();
            }
        }
        applyAndReport(client);
    }

    public static int lockedAxis() {
        return lockedAxis;
    }

    public static String axisName(int axis) {
        return switch (axis) {
            case 1 -> "X (red, left-right)";
            case 2 -> "Y (green, up-down)";
            case 3 -> "Z (blue, depth)";
            default -> "free";
        };
    }

    /** Applies a drag to the item's position, in the plane facing the camera. */
    public static void dragMove(Minecraft client, double dragX, double dragY, boolean fine) {
        float scale = fine ? FINE : 1F;
        // With an axis locked, both directions of the drag feed that one axis, so a diagonal wobble
        // of the hand cannot leak into a second value.
        float amount = (float) (Math.abs(dragX) > Math.abs(dragY) ? dragX : dragY) * MOVE_PER_PIXEL * scale;
        switch (lockedAxis) {
            case 1 -> x += amount;
            case 2 -> y += amount;
            case 3 -> z += amount;
            default -> {
                x += (float) dragX * MOVE_PER_PIXEL * scale;
                y += (float) dragY * MOVE_PER_PIXEL * scale;
            }
        }
        applyAndReport(client);
    }

    public static void dragRotate(Minecraft client, double dragX, double dragY, boolean fine) {
        float scale = fine ? FINE : 1F;
        float amount = (float) (Math.abs(dragX) > Math.abs(dragY) ? dragX : dragY) * TURN_PER_PIXEL * scale;
        if (lockedAxis != 0) {
            rotateAround(lockedAxis, amount);
        } else {
            rotateAround(2, (float) dragX * TURN_PER_PIXEL * scale);
            rotateAround(1, (float) dragY * TURN_PER_PIXEL * scale);
        }
        applyAndReport(client);
    }

    public static void dragDepth(Minecraft client, double amount, boolean fine) {
        z += (float) amount * MOVE_PER_PIXEL * (fine ? FINE : 1F) * 4F;
        applyAndReport(client);
    }

    private static void applyAndReport(Minecraft client) {
        apply(client);
        message(client, describe());
    }


    public static void close(Minecraft client) {
        if (!active) {
            return;
        }
        active = false;
        net.bettercombat.BetterCombatMod.LOGGER.info("[bc] emote studio OFF");
        net.bettercombat.client.EmoteStudioNetwork.sendStudioState(false);
        message(client, "Studio OFF");
        // Tracked rather than read back off the client: there is no public accessor for the open
        // screen in this version, and closing blindly could dismiss somebody else's.
        if (screen != null) {
            screen = null;
            client.setScreenAndShow(null);
        }
    }

    public static void switchHand(Minecraft client) {
        if (!active) {
            return;
        }
        offHand = !offHand;
        seedFromPlayer(client);
        message(client, "Editing " + hand() + " hand");
    }

    /** Reads the anchor already on the player, so editing starts from what is on screen. */
    private static void seedFromPlayer(Minecraft client) {
        if (!(client.player instanceof PlayerAttackAnimatable animatable)) {
            return;
        }
        EmoteItemAnchor current = offHand
                ? animatable.getEmoteOffHandAnchor()
                : animatable.getEmoteItemAnchor();
        if (current == null) {
            x = y = z = 0F;
            rotation.identity();
            return;
        }
        x = current.x();
        y = current.y();
        z = current.z();
        rotation.rotationXYZ(current.pitch(), current.yaw(), current.roll());
    }


    /** Pushes the current values onto the local player only; this is a preview, not a change. */
    private static void apply(Minecraft client) {
        if (!(client.player instanceof PlayerAttackAnimatable animatable)) {
            return;
        }
        EmoteItemAnchor edited = toAnchor();
        EmoteItemAnchor main = offHand ? animatable.getEmoteItemAnchor() : edited;
        EmoteItemAnchor off = offHand ? edited : animatable.getEmoteOffHandAnchor();
        animatable.updateEmoteItemAnchors(main, off);
    }

    public static EmoteItemAnchor toAnchor() {
        Vector3f angles = rotation.getEulerAnglesXYZ(new Vector3f());
        return new EmoteItemAnchor(x, y, z, angles.x, angles.y, angles.z);
    }

    public static boolean isOffHand() {
        return offHand;
    }

    public static String describe() {
        Vector3f angles = rotation.getEulerAnglesXYZ(new Vector3f());
        return String.format(Locale.ROOT,
                "%s%s  x %.1f  y %.1f  z %.1f  pitch %.1f  yaw %.1f  roll %.1f",
                hand(), lockedAxis == 0 ? "" : " [" + axisName(lockedAxis) + "]",
                x, y, z,
                Math.toDegrees(angles.x), Math.toDegrees(angles.y), Math.toDegrees(angles.z));
    }



    private static String hand() {
        return offHand ? "off-hand" : "main";
    }

    /** Sends the positioned anchor to the server, which records it against the emote being played. */
    public static void save(Minecraft client) {
        if (!active) {
            return;
        }
        // Radians on the wire, matching the ItemAnchor contract the server side builds from these.
        // Sending degrees here made the editor convert a second time and inflate every angle.
        Vector3f angles = rotation.getEulerAnglesXYZ(new Vector3f());
        net.bettercombat.client.EmoteStudioNetwork.sendAnchor(offHand, x, y, z,
                angles.x, angles.y, angles.z);
        message(client, "Saved: " + describe());
    }

    private static void message(Minecraft client, @Nullable String text) {
        if (client.gui != null && text != null) {
            client.gui.hud.setOverlayMessage(Component.literal(text), false);
        }
    }
}
