package net.bettercombat.client.studio;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The studio's input surface.
 *
 * <p>Being a screen is doing most of the work here. Opening one frees the cursor, stops movement
 * input from reaching the player, and makes it impossible to swing - three things the studio needs
 * that would otherwise each have taken a mixin, and would each have been able to break on their own.
 * The emote keeps playing because a screen does not pause a multiplayer game.
 *
 * <p>Nothing is drawn. {@link #extractRenderState} is overridden to do nothing at all, so the world
 * and the model stay unobstructed and no part of the reworked GUI pipeline is relied upon; the live
 * readout goes to the action bar, which this mod already uses elsewhere.
 *
 * <p>Left drag orbits the camera so the item can be judged from any side. Right drag moves the item,
 * middle drag rotates it, the wheel pushes it away or pulls it closer, and shift makes any of those
 * finer.
 *
 * <p>M mirrors the placement onto the other hand, which is the only reliable way to make a crossed
 * pair symmetric - by eye the two never quite match.
 *
 * <p>X, Y and Z constrain a drag to one axis - the thing a gizmo's coloured arrows are actually for,
 * and what makes rotation controllable. Tab switches between the two hands, F5 changes the camera and
 * Enter records the result. These
 * are keys rather than commands because the chat cannot be reached while a screen is open - the
 * command form of the same actions was unusable from inside the studio, which is what made dual
 * wielding impossible to edit.
 */
public class EmoteStudioScreen extends Screen {

    private static final float ORBIT_PER_PIXEL = 0.35F;

    private final CameraType previousCamera;

    public EmoteStudioScreen(Minecraft client) {
        super(Component.literal("Emote Studio"));
        this.previousCamera = client.options.getCameraType();
        // There is nothing to position against from inside your own head.
        if (previousCamera == CameraType.FIRST_PERSON) {
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }


    /** Draw nothing: the point of this screen is the world behind it. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
    }

    /** The game must keep running, or the emote would freeze while it is being adjusted. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Claims the click so drags are delivered at all.
     *
     * <p>A screen only receives {@code mouseDragged} while it considers itself dragging, and the
     * container default only enters that state when a child widget accepts the click. This screen has
     * no widgets, so without claiming the press here every drag was silently dropped - which looked
     * exactly like the camera and the item both being frozen.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        Minecraft client = Minecraft.getInstance();
        switch (event.key()) {
            case InputConstants.KEY_RIGHT -> {
                EmoteStudio.step(client, true, false, isShiftDown(client));
                return true;
            }
            case InputConstants.KEY_LEFT -> {
                EmoteStudio.step(client, false, false, isShiftDown(client));
                return true;
            }
            case InputConstants.KEY_UP -> {
                EmoteStudio.step(client, true, false, isShiftDown(client));
                return true;
            }
            case InputConstants.KEY_DOWN -> {
                EmoteStudio.step(client, false, false, isShiftDown(client));
                return true;
            }
            case InputConstants.KEY_PAGEUP -> {
                EmoteStudio.step(client, true, true, isShiftDown(client));
                return true;
            }
            case InputConstants.KEY_PAGEDOWN -> {
                EmoteStudio.step(client, false, true, isShiftDown(client));
                return true;
            }
            case InputConstants.KEY_R -> {
                EmoteStudio.resetAxis(client);
                return true;
            }
            case InputConstants.KEY_X -> {
                EmoteStudio.lockAxis(client, 1);
                return true;
            }
            case InputConstants.KEY_Y -> {
                EmoteStudio.lockAxis(client, 2);
                return true;
            }
            case InputConstants.KEY_Z -> {
                EmoteStudio.lockAxis(client, 3);
                return true;
            }
            case InputConstants.KEY_M -> {
                EmoteStudio.mirrorToOtherHand(client);
                return true;
            }
            case InputConstants.KEY_TAB -> {
                // Both hands of a dual wield are edited from here; one anchor each.
                EmoteStudio.switchHand(client);
                return true;
            }
            case InputConstants.KEY_F5 -> {
                client.options.setCameraType(client.options.getCameraType().cycle());
                return true;
            }
            case InputConstants.KEY_RETURN -> {
                EmoteStudio.save(client);
                return true;
            }
            default -> {
                return super.keyPressed(event);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        setDragging(true);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        setDragging(false);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return false;
        }

        boolean fine = isShiftDown(client);
        switch (event.button()) {
            case 0 -> orbit(client, dragX, dragY, fine);
            case 1 -> EmoteStudio.dragMove(client, dragX, dragY, fine);
            default -> EmoteStudio.dragRotate(client, dragX, dragY, fine);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Minecraft client = Minecraft.getInstance();
        EmoteStudio.dragDepth(client, -scrollY, isShiftDown(client));
        return true;
    }

    private static boolean isShiftDown(Minecraft client) {
        return InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_LSHIFT);
    }

    /**
     * Turns the player to look around the model.
     *
     * <p>Moving the player's own view rather than a separate studio camera keeps the item anchored to
     * the same body it is being fitted to, and costs nothing: movement is already blocked while a
     * screen is open, so turning cannot carry them away from the spot.
     */
    private void orbit(Minecraft client, double dragX, double dragY, boolean fine) {
        float scale = fine ? 0.25F : 1F;
        float yaw = client.player.getYRot() + (float) dragX * ORBIT_PER_PIXEL * scale;
        float pitch = client.player.getXRot() + (float) dragY * ORBIT_PER_PIXEL * scale;
        client.player.setYRot(yaw);
        client.player.setXRot(Math.max(-90F, Math.min(90F, pitch)));
    }

    @Override
    public void onClose() {
        Minecraft client = Minecraft.getInstance();
        client.options.setCameraType(previousCamera);
        EmoteStudio.close(client);
        super.onClose();
    }
}
