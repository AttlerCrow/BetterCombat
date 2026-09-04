package net.bettercombat.client.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The shapes the emote screens are sent.
 *
 * <p>Field names are the wire contract - GSON matches them to the JSON the server builds - so
 * renaming one silently empties that half of the screen rather than failing to compile.
 *
 * <p>Every field is treated as possibly absent. These objects are filled by reflection from a
 * payload composed elsewhere, so GSON will happily hand back an instance with nulls throughout if
 * the two sides ever drift; {@link #normalise()} turns that into an empty screen instead of a crash
 * a hundred lines later.
 */
public final class MenuData {

    private MenuData() {
    }

    /** What the wheel needs: its shape, what is on it, and only those emotes. */
    public static final class WheelState {
        public int pages;
        public int slots;
        public List<List<String>> wheel;
        public Map<String, Entry> catalog;

        public void normalise() {
            if (pages <= 0) {
                pages = 1;
            }
            if (slots <= 0) {
                slots = 8;
            }
            wheel = normaliseWheel(wheel, pages, slots);
            if (catalog == null) {
                catalog = Map.of();
            }
        }

        /** @return the emote in that slot, or null when it is empty */
        public String at(int page, int slot) {
            return MenuData.at(wheel, page, slot);
        }

        public Entry entry(String emoteId) {
            Entry found = emoteId == null ? null : catalog.get(emoteId);
            return found == null ? Entry.UNKNOWN : found;
        }
    }

    /** One emote as the wheel draws it. */
    public static final class Entry {
        public static final Entry UNKNOWN = new Entry();

        public String name;
        public String icon;
        public boolean unlocked;

        public String displayName() {
            return name == null || name.isBlank() ? "?" : name;
        }
    }

    /** What the editor needs: everything. */
    public static final class EditorState {
        public int pages;
        public int slots;
        public List<List<String>> wheel;
        public List<Emote> emotes;
        public List<String> categories;

        public void normalise() {
            if (pages <= 0) {
                pages = 1;
            }
            if (slots <= 0) {
                slots = 8;
            }
            wheel = normaliseWheel(wheel, pages, slots);
            if (emotes == null) {
                emotes = List.of();
            }
            if (categories == null) {
                categories = List.of();
            }
        }

        public String at(int page, int slot) {
            return MenuData.at(wheel, page, slot);
        }

        public Emote find(String emoteId) {
            if (emoteId == null) {
                return null;
            }
            for (Emote emote : emotes) {
                if (emoteId.equals(emote.id)) {
                    return emote;
                }
            }
            return null;
        }

        /** Puts an emote in a slot, or empties it when {@code emoteId} is null. */
        public void put(int page, int slot, String emoteId) {
            if (page < 0 || page >= wheel.size()) {
                return;
            }
            List<String> slots = wheel.get(page);
            if (slot >= 0 && slot < slots.size()) {
                slots.set(slot, emoteId);
            }
        }
    }

    /** One emote as the editor draws it. */
    public static final class Emote {
        public String id;
        public String name;
        public String icon;
        public String category;
        public String description;
        public boolean unlocked;
        /** Whether it may go on the wheel at all - config's {@code wheel} tag. */
        public boolean wheel;

        public String displayName() {
            return name == null || name.isBlank() ? String.valueOf(id) : name;
        }

        public String categoryOrDefault() {
            return category == null || category.isBlank() ? "general" : category;
        }

        /** Draggable only when the player owns it and it is allowed on a wheel. */
        public boolean placeable() {
            return unlocked && wheel;
        }
    }

    private static String at(List<List<String>> wheel, int page, int slot) {
        if (wheel == null || page < 0 || page >= wheel.size()) {
            return null;
        }
        List<String> slots = wheel.get(page);
        return slot < 0 || slot >= slots.size() ? null : slots.get(slot);
    }

    /**
     * Forces the grid to exactly {@code pages} by {@code slots}, mutable throughout.
     *
     * <p>Rebuilt rather than trusted, for two reasons. GSON gives back immutable-ish lists that the
     * editor then has to write into, and a payload one slot short would otherwise turn every later
     * index into an out-of-bounds at drag time rather than at parse time.
     */
    private static List<List<String>> normaliseWheel(List<List<String>> source, int pages, int slots) {
        List<List<String>> grid = new ArrayList<>(pages);
        for (int page = 0; page < pages; page++) {
            List<String> row = new ArrayList<>(slots);
            for (int slot = 0; slot < slots; slot++) {
                row.add(at(source, page, slot));
            }
            grid.add(row);
        }
        return grid;
    }
}
