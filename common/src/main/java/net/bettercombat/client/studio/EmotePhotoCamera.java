package net.bettercombat.client.studio;

import net.bettercombat.client.animation.PlayerAttackAnimatable;
import net.minecraft.client.Minecraft;

/**
 * Pull the camera back while holding a pose, by holding the middle mouse button and moving.
 *
 * <p>Deliberately not the scroll wheel. The wheel switches the hotbar, and taking it over would break
 * that everywhere the pose is held. The middle button is unused during an emote and costs nothing.
 *
 * <p>Holding the right button detaches the view from the heading, so someone leading a line can look
 * around, or frame a shot, without steering the whole dance while they do it.
 */
public final class EmotePhotoCamera {

    /**
     * Distances the middle button cycles through, in blocks behind the normal third-person camera.
     *
     * <p>Steps rather than a drag: a drag gives no way back to a distance you liked, and wrapping
     * from the last step to the first keeps it to one button instead of two.
     */
    private static final float[] ZOOM_STEPS = { 0F, 2.5F, 5F, 9F, 14F };

    private static int zoomStep;
    private static boolean middleWasDown;
    private static boolean freeLook;
    private static boolean rightWasDown;

    private EmotePhotoCamera() {
    }

    /** Extra distance the camera should sit behind the player, in blocks. */
    public static float zoom() {
        return ZOOM_STEPS[zoomStep];
    }

    /**
     * Whether the view is currently loose from where the player is going.
     *
     * <p>Read both here, to hold the model still, and by the server through its own message, which
     * holds the heading. Freezing only one of the two left the body turning to follow the camera.
     */
    public static boolean isFreeLook() {
        return freeLook;
    }

    public static void reset() {
        zoomStep = 0;
        middleWasDown = false;
        if (freeLook) {
            freeLook = false;
            net.bettercombat.client.EmoteStudioNetwork.sendFreeLook(false);
        }
        rightWasDown = false;
    }

    public static void tick(Minecraft client) {
        if (!(client.player instanceof PlayerAttackAnimatable animatable)
                || animatable.getEmoteCameraHeightOffset() == 0F && !animatable.isEmoteActive()) {
            reset();
            return;
        }

        if (EmoteStudio.isActive()) {
            return;
        }

        // Edge triggered: one step per press, rather than racing through the cycle while held.
        boolean middleDown = client.mouseHandler.isMiddlePressed();
        if (middleDown && !middleWasDown) {
            zoomStep = (zoomStep + 1) % ZOOM_STEPS.length;
        }
        middleWasDown = middleDown;

        // Only the client knows a button is held, so the server is told when it changes.
        boolean rightDown = client.mouseHandler.isRightPressed();
        if (rightDown != rightWasDown) {
            freeLook = rightDown;
            net.bettercombat.client.EmoteStudioNetwork.sendFreeLook(rightDown);
        }
        rightWasDown = rightDown;
    }
}
