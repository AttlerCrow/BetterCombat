package net.bettercombat.client.menu;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import net.bettercombat.client.Keybindings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * The radial emote picker.
 *
 * <p>Held open by a key, aimed with the mouse, performed on release. That gesture is the whole point
 * of a wheel - it is faster than any list because the hand never leaves the mouse and the eye never
 * reads anything - so the release is the primary way out, and clicking is the fallback for when the
 * screen was opened by a command with no key held.
 *
 * <p>The key is watched through {@link net.minecraft.client.KeyMapping#matches(KeyEvent)} on the
 * screen's own key events rather than by polling {@code isDown()}. A screen receives key events
 * directly; whether a binding's held state keeps updating underneath one is not something to bet the
 * feature on, and a wheel that never notices the release is a wheel that never fires.
 */
public class EmoteWheelScreen extends Screen implements ClientMenuNetwork.MenuScreen {

    public static final String MENU_ID = "emote_wheel";

    /** Distance from the centre to the middle of a slot. */
    private static final int RADIUS = 74;
    /** No selection this close to the centre, so releasing in the middle cancels. */
    private static final int DEAD_ZONE = 26;
    private static final int PADDING = 4;

    private static final int SLOT_IDLE = 0x99000000;
    private static final int SLOT_SELECTED = 0xDD2F6FED;
    private static final int SLOT_EMPTY = 0x55000000;
    private static final int LOCKED_VEIL = 0xAA101010;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFA0A0A0;

    private MenuData.WheelState state;
    private int page;
    private int selected = -1;
    /** Set once something has been sent, so a release and a click cannot both fire. */
    private boolean committed;

    private EmoteWheelScreen(MenuData.WheelState state) {
        super(Component.literal("Emote Wheel"));
        this.state = state;
    }

    static EmoteWheelScreen of(String json, Gson gson) {
        MenuData.WheelState state = gson.fromJson(json, MenuData.WheelState.class);
        if (state == null) {
            // GSON returns null for empty input rather than throwing, and a caller that trusted it
            // would fail one line later with nothing pointing at the payload.
            return null;
        }
        state.normalise();
        return new EmoteWheelScreen(state);
    }

    @Override
    public void applyState(String json) {
        MenuData.WheelState replacement = new Gson().fromJson(json, MenuData.WheelState.class);
        if (replacement == null) {
            return;
        }
        replacement.normalise();
        this.state = replacement;
        this.page = Math.min(page, replacement.pages - 1);
    }

    /** The world behind this has to keep running, or the emote already playing would freeze. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        // Every way out lands here - release, click, escape, the inventory key, dying - which is why
        // the server is told from here rather than from each of them.
        ClientMenuNetwork.sendClosed(MENU_ID);
        super.removed();
    }

    // ---------------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int centreX = width / 2;
        int centreY = height / 2;
        this.selected = slotAt(mouseX, mouseY, centreX, centreY);

        graphics.fill(0, 0, width, height, 0x66000000);

        int icon = EmoteIcons.SIZE;
        for (int slot = 0; slot < state.slots; slot++) {
            double angle = angleOf(slot);
            int x = centreX + (int) Math.round(Math.cos(angle) * RADIUS) - icon / 2;
            int y = centreY + (int) Math.round(Math.sin(angle) * RADIUS) - icon / 2;

            String emoteId = state.at(page, slot);
            boolean isSelected = slot == selected;
            int background = emoteId == null ? SLOT_EMPTY : (isSelected ? SLOT_SELECTED : SLOT_IDLE);
            graphics.fill(x - PADDING, y - PADDING, x + icon + PADDING, y + icon + PADDING, background);

            if (emoteId == null) {
                continue;
            }
            MenuData.Entry entry = state.entry(emoteId);
            graphics.blit(RenderPipelines.GUI_TEXTURED, EmoteIcons.resolve(entry.icon),
                    x, y, 0F, 0F, icon, icon, icon, icon);
            if (!entry.unlocked) {
                // Drawn and then veiled rather than skipped: seeing what is behind a lock is the
                // point of showing it at all.
                graphics.fill(x, y, x + icon, y + icon, LOCKED_VEIL);
            }
        }

        String label = selectedEmoteId() == null ? "" : state.entry(selectedEmoteId()).displayName();
        graphics.centeredText(font, Component.literal(label), centreX, centreY - 4, TEXT);

        if (state.pages > 1) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + state.pages),
                    centreX, centreY + RADIUS + 40, TEXT_DIM);
        }
    }

    private double angleOf(int slot) {
        // Slot 0 at the top, going clockwise, which is where a hand expects the first one to be.
        return -Math.PI / 2D + slot * (2D * Math.PI / state.slots);
    }

    /** @return the slot the cursor is aimed at, or -1 inside the dead zone */
    private int slotAt(int mouseX, int mouseY, int centreX, int centreY) {
        double dx = mouseX - centreX;
        double dy = mouseY - centreY;
        if (dx * dx + dy * dy < (double) DEAD_ZONE * DEAD_ZONE) {
            return -1;
        }
        double step = 2D * Math.PI / state.slots;
        // Rotated so slot 0 sits at the top, then rounded to the nearest slot rather than floored:
        // a sector should be centred on its icon, not begin at it.
        double turns = (Math.atan2(dy, dx) + Math.PI / 2D) / step;
        int slot = (int) Math.round(turns) % state.slots;
        return slot < 0 ? slot + state.slots : slot;
    }

    private String selectedEmoteId() {
        return selected < 0 ? null : state.at(page, selected);
    }

    // ---------------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (Keybindings.emoteWheelKeyBinding.matches(event)) {
            commit();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case InputConstants.KEY_E -> {
                turnPage(1);
                return true;
            }
            case InputConstants.KEY_Q -> {
                turnPage(-1);
                return true;
            }
            default -> {
                return super.keyPressed(event);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // The command path opens this with no key held, so a click has to be able to pick too.
        commit();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (Keybindings.emoteWheelKeyBinding.matchesMouse(event)) {
            commit();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0D) {
            turnPage(scrollY > 0D ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void turnPage(int delta) {
        if (state.pages <= 1) {
            return;
        }
        page = Math.floorMod(page + delta, state.pages);
    }

    /**
     * Performs whatever is aimed at, and closes.
     *
     * <p>Closes even when nothing was aimed at: a release in the dead zone is a player deciding not
     * to, and leaving the wheel up after that would mean the only way to cancel is a second gesture.
     *
     * <p>A locked slot sends nothing. The server would refuse it anyway, so this is not the check
     * that matters - it is the one that stops the refusal from being the player's only feedback.
     */
    private void commit() {
        if (committed) {
            return;
        }
        committed = true;
        String emoteId = selectedEmoteId();
        if (emoteId != null && state.entry(emoteId).unlocked) {
            ClientMenuNetwork.sendAction(MENU_ID, "{\"action\":\"play\",\"emote\":\"" + escape(emoteId) + "\"}");
        }
        onClose();
    }

    /**
     * Escapes an id for the small JSON built by hand above.
     *
     * <p>Ids are lowercase and unremarkable, but they come from a config file this code has never
     * seen. A quote in one would otherwise produce a payload the server cannot parse.
     */
    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
