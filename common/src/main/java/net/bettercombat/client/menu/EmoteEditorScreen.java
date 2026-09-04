package net.bettercombat.client.menu;

import com.google.gson.Gson;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Arranges the emote wheel: everything the player has on the left, their wheel on the right, drag
 * between them.
 *
 * <p>Drag and drop rather than click-to-assign because a wheel is a shape, and the thing being
 * chosen is <em>where</em> an emote goes as much as which one. Clicking a slot then clicking an
 * emote asks the player to hold that mapping in their head; dragging shows it.
 */
public class EmoteEditorScreen extends Screen implements ClientMenuNetwork.MenuScreen {

    public static final String MENU_ID = "emote_editor";

    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 230;

    private static final int GRID_COLUMNS = 5;
    private static final int GRID_ROWS = 4;
    private static final int CELL = 38;

    private static final int WHEEL_RADIUS = 52;

    private static final int PANEL = 0xF0161616;
    private static final int PANEL_EDGE = 0xFF3A3A3A;
    private static final int CELL_IDLE = 0x66000000;
    private static final int CELL_HOVER = 0x99404040;
    private static final int SLOT_IDLE = 0x66000000;
    private static final int SLOT_HOVER = 0xDD2F6FED;
    private static final int LOCKED_VEIL = 0xAA101010;
    private static final int BUTTON = 0xFF2F6FED;
    private static final int BUTTON_HOVER = 0xFF4A88FF;
    /** Amber while there is something to save, so the button reads as waiting on the player. */
    private static final int BUTTON_UNSAVED = 0xFFE0902A;
    private static final int WARNING = 0xFFE0902A;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFF9A9A9A;

    private MenuData.EditorState state;

    private EditBox search;
    private String category = "";
    private int scrollRow;
    private int page;

    /** The emotes the grid is currently showing, rebuilt whenever a filter changes. */
    private final List<MenuData.Emote> visible = new ArrayList<>();

    /**
     * Set the moment the arrangement differs from the server's, cleared when the server confirms.
     *
     * <p>Nothing is sent until the button is pressed, and without a mark on screen that is invisible:
     * the wheel on the right already shows the change, so the editor looks like it saved itself. The
     * first person to use this dragged emotes out, closed it, and found them all still there.
     */
    private boolean dirty;

    /** What is under the cursor mid-drag, or null. */
    private String dragging;
    /** Where the drag started, so dropping outside the wheel can empty the slot it came from. */
    private int draggingFromSlot = -1;
    private int draggingFromPage = -1;

    private int panelX;
    private int panelY;

    private EmoteEditorScreen(MenuData.EditorState state) {
        super(Component.literal("Emote Editor"));
        this.state = state;
    }

    static EmoteEditorScreen of(String json, Gson gson) {
        MenuData.EditorState state = gson.fromJson(json, MenuData.EditorState.class);
        if (state == null) {
            return null;
        }
        state.normalise();
        return new EmoteEditorScreen(state);
    }

    @Override
    public void applyState(String json) {
        MenuData.EditorState replacement = new Gson().fromJson(json, MenuData.EditorState.class);
        if (replacement == null) {
            return;
        }
        replacement.normalise();
        this.state = replacement;
        this.page = Math.min(page, replacement.pages - 1);
        // A drag in flight refers to the wheel that just got replaced; keeping it would drop an
        // emote into a slot the player never aimed at.
        this.dragging = null;
        this.draggingFromSlot = -1;
        // This arrives as the server's answer to a save, so what is on screen is now what is stored.
        // It is also the first thing to arrive when the screen opens, which is the same statement.
        this.dirty = false;
        rebuildVisible();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        String previous = search == null ? "" : search.getValue();
        search = new EditBox(font, panelX + 10, panelY + 22, 150, 16, Component.literal("Buscar"));
        search.setMaxLength(32);
        search.setValue(previous);
        search.setResponder(text -> {
            scrollRow = 0;
            rebuildVisible();
        });
        addRenderableWidget(search);

        rebuildVisible();
    }

    @Override
    public void removed() {
        ClientMenuNetwork.sendClosed(MENU_ID);
        super.removed();
    }

    /** Applies the search text and the category tab. */
    private void rebuildVisible() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (MenuData.Emote emote : state.emotes) {
            if (!category.isEmpty() && !category.equals(emote.categoryOrDefault())) {
                continue;
            }
            if (!query.isEmpty()
                    && !emote.displayName().toLowerCase(Locale.ROOT).contains(query)
                    && !String.valueOf(emote.id).toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            visible.add(emote);
        }
        int maxScroll = Math.max(0, (visible.size() + GRID_COLUMNS - 1) / GRID_COLUMNS - GRID_ROWS);
        scrollRow = Math.min(scrollRow, maxScroll);
    }

    // ---------------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, PANEL_EDGE);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, PANEL);

        graphics.text(font, Component.literal("Editor de emotes"), panelX + 10, panelY + 8, TEXT);

        drawCategories(graphics, mouseX, mouseY);
        drawGrid(graphics, mouseX, mouseY);
        drawWheel(graphics, mouseX, mouseY);
        drawSaveButton(graphics, mouseX, mouseY);

        // The EditBox is a child widget, and this method replaces the one that would have drawn it.
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (dragging != null) {
            // Last, and unclipped, so the thing being dragged is never behind the thing it is being
            // dragged onto.
            MenuData.Emote emote = state.find(dragging);
            if (emote != null) {
                int icon = EmoteIcons.SIZE;
                graphics.blit(RenderPipelines.GUI_TEXTURED, EmoteIcons.resolve(emote.icon),
                        mouseX - icon / 2, mouseY - icon / 2, 0F, 0F, icon, icon, icon, icon);
            }
        }
    }

    private void drawCategories(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = panelX + 168;
        int y = panelY + 26;
        for (String name : categories()) {
            String label = name.isEmpty() ? "Todos" : name;
            int textWidth = font.width(label);
            boolean active = name.equals(category);
            boolean hovered = within(mouseX, mouseY, x, y - 4, textWidth + 6, 14);
            graphics.text(font, Component.literal(label), x + 3, y,
                    active || hovered ? TEXT : TEXT_DIM);
            x += textWidth + 12;
        }
    }

    /** The tab row: everything, then whatever categories the server sent, in its order. */
    private List<String> categories() {
        List<String> tabs = new ArrayList<>(state.categories.size() + 1);
        tabs.add("");
        tabs.addAll(state.categories);
        return tabs;
    }

    private void drawGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int gridX = panelX + 10;
        int gridY = panelY + 46;
        int icon = EmoteIcons.SIZE;

        graphics.enableScissor(gridX, gridY, gridX + GRID_COLUMNS * CELL, gridY + GRID_ROWS * CELL);
        for (int index = 0; index < visible.size(); index++) {
            int row = index / GRID_COLUMNS - scrollRow;
            int column = index % GRID_COLUMNS;
            if (row < 0 || row >= GRID_ROWS) {
                continue;
            }
            MenuData.Emote emote = visible.get(index);
            int x = gridX + column * CELL;
            int y = gridY + row * CELL;
            boolean hovered = within(mouseX, mouseY, x, y, CELL - 2, CELL - 2);
            graphics.fill(x, y, x + CELL - 2, y + CELL - 2, hovered ? CELL_HOVER : CELL_IDLE);
            graphics.blit(RenderPipelines.GUI_TEXTURED, EmoteIcons.resolve(emote.icon),
                    x + 2, y + 2, 0F, 0F, icon, icon, icon, icon);
            if (!emote.placeable()) {
                graphics.fill(x + 2, y + 2, x + 2 + icon, y + 2 + icon, LOCKED_VEIL);
            }
        }
        graphics.disableScissor();

        MenuData.Emote hovered = emoteAt(mouseX, mouseY);
        if (hovered != null) {
            String line = hovered.displayName();
            if (!hovered.unlocked) {
                line += " (bloqueado)";
            } else if (!hovered.wheel) {
                line += " (no va en la rueda)";
            }
            graphics.text(font, Component.literal(line), panelX + 10, panelY + 46 + GRID_ROWS * CELL + 4, TEXT);
            if (hovered.description != null && !hovered.description.isBlank()) {
                graphics.text(font, Component.literal(hovered.description),
                        panelX + 10, panelY + 46 + GRID_ROWS * CELL + 15, TEXT_DIM);
            }
        }
    }

    private void drawWheel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int centreX = wheelCentreX();
        int centreY = wheelCentreY();
        int icon = EmoteIcons.SIZE;

        for (int slot = 0; slot < state.slots; slot++) {
            int[] position = slotPosition(slot);
            boolean hovered = within(mouseX, mouseY, position[0], position[1], icon, icon);
            graphics.fill(position[0] - 3, position[1] - 3,
                    position[0] + icon + 3, position[1] + icon + 3,
                    hovered ? SLOT_HOVER : SLOT_IDLE);

            String emoteId = state.at(page, slot);
            if (emoteId == null) {
                continue;
            }
            MenuData.Emote emote = state.find(emoteId);
            graphics.blit(RenderPipelines.GUI_TEXTURED,
                    EmoteIcons.resolve(emote == null ? null : emote.icon),
                    position[0], position[1], 0F, 0F, icon, icon, icon, icon);
        }

        String pageLabel = "< " + (page + 1) + " / " + state.pages + " >";
        graphics.centeredText(font, Component.literal(pageLabel), centreX, centreY - 4, TEXT_DIM);
    }

    private void drawSaveButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int[] bounds = saveBounds();
        boolean hovered = within(mouseX, mouseY, bounds[0], bounds[1], bounds[2], bounds[3]);
        graphics.fill(bounds[0], bounds[1], bounds[0] + bounds[2], bounds[1] + bounds[3],
                hovered ? BUTTON_HOVER : (dirty ? BUTTON_UNSAVED : BUTTON));
        graphics.centeredText(font, Component.literal(dirty ? "Guardar *" : "Guardar"),
                bounds[0] + bounds[2] / 2, bounds[1] + 5, TEXT);
        if (dirty) {
            // Said in words as well as in colour. The wheel on the right already shows the new
            // arrangement, so nothing else on screen distinguishes "changed" from "saved".
            graphics.text(font, Component.literal("Sin guardar"),
                    bounds[0] - font.width("Sin guardar") - 8, bounds[1] + 5, WARNING);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Geometry
    // ---------------------------------------------------------------------------------------------

    private int wheelCentreX() {
        return panelX + 292;
    }

    private int wheelCentreY() {
        return panelY + 110;
    }

    /** Top-left corner of a slot's icon. */
    private int[] slotPosition(int slot) {
        double angle = -Math.PI / 2D + slot * (2D * Math.PI / state.slots);
        int icon = EmoteIcons.SIZE;
        return new int[] {
                wheelCentreX() + (int) Math.round(Math.cos(angle) * WHEEL_RADIUS) - icon / 2,
                wheelCentreY() + (int) Math.round(Math.sin(angle) * WHEEL_RADIUS) - icon / 2 };
    }

    /**
     * Bottom right, under the wheel rather than under the grid.
     *
     * <p>It sat on the left first and covered the hovered emote's name and description, which are
     * drawn just below the grid and end up at the same height. Two things wanted the same corner;
     * the button is the one that can move, and sitting beside the wheel it edits reads better anyway.
     */
    private int[] saveBounds() {
        return new int[] { panelX + PANEL_WIDTH - 100, panelY + PANEL_HEIGHT - 26, 90, 18 };
    }

    private static boolean within(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private MenuData.Emote emoteAt(int mouseX, int mouseY) {
        int gridX = panelX + 10;
        int gridY = panelY + 46;
        if (!within(mouseX, mouseY, gridX, gridY, GRID_COLUMNS * CELL, GRID_ROWS * CELL)) {
            return null;
        }
        int column = (mouseX - gridX) / CELL;
        int row = (mouseY - gridY) / CELL;
        int index = (row + scrollRow) * GRID_COLUMNS + column;
        return index >= 0 && index < visible.size() ? visible.get(index) : null;
    }

    private int slotAt(int mouseX, int mouseY) {
        int icon = EmoteIcons.SIZE;
        for (int slot = 0; slot < state.slots; slot++) {
            int[] position = slotPosition(slot);
            if (within(mouseX, mouseY, position[0], position[1], icon, icon)) {
                return slot;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Children first, or the search box never takes focus.
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        int[] bounds = saveBounds();
        if (within(mouseX, mouseY, bounds[0], bounds[1], bounds[2], bounds[3])) {
            save();
            return true;
        }

        String tab = categoryAt(mouseX, mouseY);
        if (tab != null) {
            category = tab;
            scrollRow = 0;
            rebuildVisible();
            return true;
        }

        if (turnPageAt(mouseX, mouseY)) {
            return true;
        }

        MenuData.Emote fromGrid = emoteAt(mouseX, mouseY);
        if (fromGrid != null && fromGrid.placeable()) {
            beginDrag(fromGrid.id, -1);
            return true;
        }

        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0 && state.at(page, slot) != null) {
            beginDrag(state.at(page, slot), slot);
            return true;
        }
        return false;
    }

    /**
     * A screen only receives drag events while it believes it is dragging, and with the search box
     * as its only child it never enters that state on its own. Without this every drag is dropped,
     * which looks exactly like the icons being stuck.
     */
    private void beginDrag(String emoteId, int fromSlot) {
        dragging = emoteId;
        draggingFromSlot = fromSlot;
        draggingFromPage = fromSlot < 0 ? -1 : page;
        setDragging(true);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging == null) {
            return super.mouseReleased(event);
        }
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int target = slotAt(mouseX, mouseY);

        if (target >= 0) {
            dirty = true;
            state.put(page, target, dragging);
            // Dragging one slot onto another moves it rather than copying it - but only when the
            // source is still where it was. Across a page turn mid-drag, the source slot on the old
            // page is the one to empty.
            if (draggingFromSlot >= 0 && !(draggingFromPage == page && draggingFromSlot == target)) {
                state.put(draggingFromPage, draggingFromSlot, null);
            }
        } else if (draggingFromSlot >= 0) {
            // Dropped away from the wheel: taking something off is a drag out, which is the gesture
            // people already expect from every other hotbar-shaped thing.
            dirty = true;
            state.put(draggingFromPage, draggingFromSlot, null);
        }

        dragging = null;
        draggingFromSlot = -1;
        draggingFromPage = -1;
        setDragging(false);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0D) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int gridX = panelX + 10;
        int gridY = panelY + 46;
        if (within((int) mouseX, (int) mouseY, gridX, gridY, GRID_COLUMNS * CELL, GRID_ROWS * CELL)) {
            int maxScroll = Math.max(0, (visible.size() + GRID_COLUMNS - 1) / GRID_COLUMNS - GRID_ROWS);
            scrollRow = Math.max(0, Math.min(maxScroll, scrollRow + (scrollY > 0D ? -1 : 1)));
            return true;
        }
        if (state.pages > 1) {
            page = Math.floorMod(page + (scrollY > 0D ? -1 : 1), state.pages);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // The search box has to see typing before anything here reads a key as a shortcut.
        return super.keyPressed(event);
    }

    private String categoryAt(int mouseX, int mouseY) {
        int x = panelX + 168;
        int y = panelY + 26;
        for (String name : categories()) {
            String label = name.isEmpty() ? "Todos" : name;
            int textWidth = font.width(label);
            if (within(mouseX, mouseY, x, y - 4, textWidth + 6, 14)) {
                return name;
            }
            x += textWidth + 12;
        }
        return null;
    }

    private boolean turnPageAt(int mouseX, int mouseY) {
        if (state.pages <= 1) {
            return false;
        }
        int centreX = wheelCentreX();
        int centreY = wheelCentreY();
        if (!within(mouseX, mouseY, centreX - 30, centreY - 8, 60, 16)) {
            return false;
        }
        page = Math.floorMod(page + (mouseX < centreX ? -1 : 1), state.pages);
        return true;
    }

    /**
     * Sends the arrangement and lets the server answer with what it kept.
     *
     * <p>The screen is not closed here. The server replies with an update carrying the wheel it
     * actually stored, and staying open is what lets the player see a slot it dropped - closing
     * first would hide the one piece of feedback that matters.
     */
    private void save() {
        StringBuilder json = new StringBuilder("{\"action\":\"save\",\"wheel\":[");
        for (int pageIndex = 0; pageIndex < state.pages; pageIndex++) {
            if (pageIndex > 0) {
                json.append(',');
            }
            json.append('[');
            for (int slot = 0; slot < state.slots; slot++) {
                if (slot > 0) {
                    json.append(',');
                }
                String emoteId = state.at(pageIndex, slot);
                if (emoteId == null) {
                    json.append("null");
                } else {
                    json.append('"').append(EmoteWheelScreen.escape(emoteId)).append('"');
                }
            }
            json.append(']');
        }
        json.append("]}");
        ClientMenuNetwork.sendAction(MENU_ID, json.toString());
    }
}
