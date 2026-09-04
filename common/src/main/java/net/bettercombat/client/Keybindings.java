package net.bettercombat.client;

import net.bettercombat.BetterCombatMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;

public class Keybindings {
    public static KeyMapping feintKeyBinding;
    public static KeyMapping toggleMineKeyBinding;
    public static KeyMapping emoteWheelKeyBinding;
    public static List<KeyMapping> all;

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(BetterCombatMod.ID, "main"));

    static {
        feintKeyBinding = new KeyMapping(
                "keybinds.bettercombat.feint",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY);

        toggleMineKeyBinding = new KeyMapping(
                "keybinds.bettercombat.toggle_mine_with_weapons",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY);




        // Unbound out of the box, deliberately. Every free letter here is already claimed by a
        // shader or map mod, and a binding that quietly loses that race is indistinguishable from a
        // broken feature - so the player picks the key, and `/emote wheel` works meanwhile.
        emoteWheelKeyBinding = new KeyMapping(
                "keybinds.bettercombat.emote_wheel",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY);

        all = List.of(feintKeyBinding, toggleMineKeyBinding, emoteWheelKeyBinding);
    }
}
