package net.bettercombat.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.mojang.logging.LogUtils;
import net.bettercombat.BetterCombatMod;
import net.bettercombat.Platform;
import net.bettercombat.api.AttributesContainer;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.api.WeaponAttributesHelper;
import net.bettercombat.api.component.BetterCombatDataComponents;
import net.bettercombat.network.Packets;
import net.bettercombat.utils.CompressionHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeaponRegistry {
    static final Logger LOGGER = LogUtils.getLogger();
    // Actual attributes to weapon assignments
    static Map<Identifier, WeaponAttributes> registrations = new HashMap();
    static Map<Identifier, AttributesContainer> containers = new HashMap();

    /**
     * Prefix marking a `custom_model_data` string entry as an explicit weapon preset id,
     * for example `bc_preset=nightfantasy:shadow_katana`.
     */
    public static final String PRESET_MARKER = "bc_preset=";

    public static void register(Identifier itemId, WeaponAttributes attributes) {
        registrations.put(itemId, attributes);
    }

    static WeaponAttributes getAttributes(Identifier itemId) {
        return registrations.get(itemId);
    }

    public static WeaponAttributes getAttributes(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
//        var attributes = WeaponAttributesHelper.readFromNBT(itemStack);
//        if (attributes != null) {
//            return attributes;
//        }

        // Server-driven presets, for servers without the mod installed (Paper and friends).
        // `custom_data`, where Bukkit keeps its persistent data container, is registered
        // `.persistent()` only and is never sent to clients, so a plugin cannot key weapons by it.
        // `custom_model_data` and `item_model` are both network-synchronized, so they are the only
        // per-item signal such a server can actually address items by.
        var marked = attributesFromPresetMarker(itemStack);
        if (marked != null) {
            return marked;
        }
        var itemModel = itemStack.get(DataComponents.ITEM_MODEL);
        if (itemModel != null) {
            var container = containers.get(itemModel);
            if (container != null && container.attributes() != null) {
                return container.attributes();
            }
        }

        var component = itemStack.get(BetterCombatDataComponents.WEAPON_PRESET_ID);
        if (component != null) {
            var container = containers.get(component);
            if (container != null) {
                return container.attributes();
            }
        }
        Item item = itemStack.getItem();
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return WeaponRegistry.getAttributes(id);
    }

    /**
     * Resolves an explicit preset id from the `custom_model_data` strings, if the item carries one.
     * Takes priority over `item_model` so a server can override individual items that would
     * otherwise share a model.
     */
    @Nullable
    private static WeaponAttributes attributesFromPresetMarker(ItemStack itemStack) {
        var customModelData = itemStack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (customModelData == null) {
            return null;
        }
        for (var value : customModelData.strings()) {
            if (value == null || !value.startsWith(PRESET_MARKER)) {
                continue;
            }
            var id = Identifier.tryParse(value.substring(PRESET_MARKER.length()));
            if (id == null) {
                continue;
            }
            var container = containers.get(id);
            if (container != null && container.attributes() != null) {
                return container.attributes();
            }
        }
        return null;
    }

    // LOADING

    public static void loadAttributes(ResourceManager resourceManager) {
        loadContainers(resourceManager);

        // Resolving parents
        containers.forEach( (itemId, container) -> {
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                return;
            }
            resolveAndRegisterAttributes(itemId, container);
        });
    }

    private static void loadContainers(ResourceManager resourceManager) {
        Map<Identifier, AttributesContainer> containers = new HashMap();
        var logging = BetterCombatMod.config.weapon_registry_logging;
        // Reading all attribute files
        for (var entry : resourceManager.listResources("weapon_attributes", fileName -> fileName.getPath().endsWith(".json")).entrySet()) {
            var identifier = entry.getKey();
            var resource = entry.getValue();
            try {
                // System.out.println("Checking resource: " + identifier);
                JsonReader reader = new JsonReader(new InputStreamReader(resource.open()));
                AttributesContainer container = WeaponAttributesHelper.decode(reader);
                var id = identifier
                        .toString().replace("weapon_attributes/", "");
                id = id.substring(0, id.lastIndexOf('.'));
                containers.put(Identifier.parse(id), container);
                if (logging) {
                    System.out.println("Loaded container: " + id);
                }
            } catch (Exception e) {
                System.err.println("Failed to parse: " + identifier);
                e.printStackTrace();
            }
        }

        // Do not remove this
        WeaponRegistry.containers = containers;
        // The following container resolution will use these containers

        Map<Identifier, AttributesContainer> resolvedContainers = new HashMap();
        for (var entry : containers.entrySet()) {
            var id = entry.getKey();
            var container = entry.getValue();
            if (container.parent() != null) {
                var resolvedAttributes = resolveAttributes(id, container);
                if (resolvedAttributes != null) {
                    container = new AttributesContainer(null, resolvedAttributes);
                }
            }
            resolvedContainers.put(id, container);
        }

        WeaponRegistry.containers = resolvedContainers;
    }

    public static WeaponAttributes resolveAttributes(Identifier itemId, AttributesContainer container) {
        try {
            ArrayList<WeaponAttributes> resolutionChain = new ArrayList();
            AttributesContainer current = container;
            while (current != null) {
                resolutionChain.add(0, current.attributes());
                if (current.parent() != null) {
                    current = containers.get(Identifier.parse(current.parent()));
                } else {
                    current = null;
                }
            }

            var empty = WeaponAttributes.empty();
            var resolvedAttributes = resolutionChain
                    .stream()
                    .reduce(empty, (a, b) -> {
                        if (b == null) { // I'm not sure why null can enter as `b`
                            return a;
                        }
                        return WeaponAttributesHelper.override(a, b);
                    });

            WeaponAttributesHelper.validate(resolvedAttributes);
            return resolvedAttributes;
        } catch (Exception e) {
            LOGGER.error("Failed to resolve weapon attributes for: " + itemId + ". Reason: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static void resolveAndRegisterAttributes(Identifier itemId, AttributesContainer container) {
        var resolvedAttributes = resolveAttributes(itemId, container);
        if (resolvedAttributes != null) {
            register(itemId, resolvedAttributes);
        }
    }

    // NETWORK SYNC

    private static Encoded encodedRegistrations = new Encoded(true, List.of());
    public record Encoded(boolean compressed, List<String> chunks) {}
    private static final int CHUNK_SIZE = 10000;
    private static final Gson gson = new GsonBuilder().create();
    public static class SyncFormat {
        public Map<String, AttributesContainer> attributes = new HashMap<>();
        public Map<String, WeaponAttributes> registrations = new HashMap<>();
    }

    public static void encodeRegistry() {
        var compressed = BetterCombatMod.config.weapon_registry_compression;
        List<String> chunks = new ArrayList<>();
        var syncContent = new SyncFormat();
        containers.forEach((key, value) -> {
            syncContent.attributes.put(key.toString(), value);
        });
        registrations.forEach((key, value) -> {
            syncContent.registrations.put(key.toString(), value);
        });

        var json = gson.toJson(syncContent);
        if (compressed) {
            json = CompressionHelper.gzipCompress(json);
        }
        if (BetterCombatMod.config.weapon_registry_logging) {
            LOGGER.info("Weapon Attribute assignments loaded: " + json);
        }
        for (int i = 0; i < json.length(); i += CHUNK_SIZE) {
            chunks.add(json.substring(i, Math.min(json.length(), i + CHUNK_SIZE)));
        }

        encodedRegistrations = new Encoded(compressed, chunks);

        var referencePacket = new Packets.WeaponRegistrySync(compressed, chunks);
        var buffer = Platform.createByteBuffer();
        referencePacket.write(buffer);
        LOGGER.info("Encoded Weapon Attribute registry size (with package overhead): " + buffer.readableBytes()
                + " bytes (in " + chunks.size() + " string chunks with the size of "  + CHUNK_SIZE + ")");
    }

    public static void decodeRegistry(Packets.WeaponRegistrySync syncPacket) {
        var compressed = syncPacket.compressed();
        String json = "";
        for (var chunk : syncPacket.chunks()) {
            json = json.concat(chunk);
        }
        if (compressed) {
            json = CompressionHelper.gzipDecompress(json);
        }
        LOGGER.info("Decoded Weapon Attribute registry in " + syncPacket.chunks().size() + " string chunks");
        if (BetterCombatMod.config.weapon_registry_logging) {
            LOGGER.info("Weapon Attribute registry received: " + json);
        }

        SyncFormat sync = gson.fromJson(json, SyncFormat.class);
        containers.clear();
        sync.attributes.forEach((key, value) -> {
            containers.put(Identifier.parse(key), value);
        });
        registrations.clear();
        sync.registrations.forEach((key, value) -> {
            registrations.put(Identifier.parse(key), value);
        });
    }

    public static Encoded getEncodedRegistry() {
        return encodedRegistrations;
    }
}
