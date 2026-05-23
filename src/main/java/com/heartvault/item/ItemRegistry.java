package com.heartvault.item;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.heartvault.config.ConfigManager;
import com.heartvault.item.model.StoredEffectEntry;
import com.heartvault.item.model.StoredPayload;
import com.heartvault.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ItemRegistry {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Keys keys;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public ItemRegistry(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.keys = new Keys(plugin);
    }

    public Keys keys() {
        return keys;
    }

    public Gson gson() {
        return gson;
    }

    public ItemStack createExtractor(HvItemType extractorType) {
        String basePath = switch (extractorType) {
            case EXTRACTOR_HEART_SYRINGE -> "items.extractor.heartSyringe";
            case EXTRACTOR_SOUL_FLASK -> "items.extractor.soulFlask";
            default -> throw new IllegalArgumentException("Not an extractor: " + extractorType);
        };

        Material material = Material.matchMaterial(configManager.config().getString(basePath + ".material", "STICK"));
        if (material == null) {
            material = Material.STICK;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        Text text = configManager.text();
        meta.displayName(text.raw(configManager.config().getString(basePath + ".displayName", "HeartVault Item")));

        int cmd = configManager.config().getInt(basePath + ".customModelData", 0);
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.itemType, PersistentDataType.STRING, extractorType.name());

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createStoredItem(String displayId, StoredPayload payload) {
        String basePath = "items.stored." + displayId;
        Material material = Material.matchMaterial(configManager.config().getString(basePath + ".material", "PAPER"));
        if (material == null) {
            material = Material.PAPER;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        Text text = configManager.text();
        meta.displayName(text.raw(configManager.config().getString(basePath + ".displayName", "<gold>Stored Effect</gold>")));

        int cmd = configManager.config().getInt(basePath + ".customModelData", 0);
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        List<Component> lore = new ArrayList<>();
        if (payload.hearts > 0) {
            lore.add(Component.text("Hearts: ", NamedTextColor.GRAY).append(Component.text(trim1(payload.hearts), NamedTextColor.GOLD)));
        }
        for (StoredEffectEntry e : payload.effects) {
            PotionEffectType type = resolveEffect(e.effectKey);
            String name = type == null ? e.effectKey : prettify(type.getKey().getKey());
            int seconds = Math.max(0, e.durationTicks / 20);
            lore.add(Component.text(name + " ", NamedTextColor.GRAY)
                    .append(Component.text("Lv." + (e.amplifier + 1), NamedTextColor.AQUA))
                    .append(Component.text(" ", NamedTextColor.GRAY))
                    .append(Component.text(seconds + "s", NamedTextColor.GOLD)));
        }
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.itemType, PersistentDataType.STRING, payload.effects.isEmpty() && payload.hearts > 0 ? HvItemType.STORED_HEARTS.name() : HvItemType.STORED_EFFECT.name());
        payload.id = displayId;
        pdc.set(keys.payload, PersistentDataType.STRING, gson.toJson(payload));

        item.setItemMeta(meta);
        return item;
    }

    public Optional<ParsedItem> parse(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String type = pdc.get(keys.itemType, PersistentDataType.STRING);
        if (type == null) {
            return Optional.empty();
        }
        HvItemType itemType;
        try {
            itemType = HvItemType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String json = pdc.get(keys.payload, PersistentDataType.STRING);
        StoredPayload payload = json == null ? null : gson.fromJson(json, StoredPayload.class);
        return Optional.of(new ParsedItem(itemType, payload));
    }

    public void updatePayload(ItemStack item, StoredPayload payload) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.payload, PersistentDataType.STRING, gson.toJson(payload));

        List<Component> lore = new ArrayList<>();
        if (payload.hearts > 0) {
            lore.add(Component.text("Hearts: ", NamedTextColor.GRAY).append(Component.text(trim1(payload.hearts), NamedTextColor.GOLD)));
        }
        for (StoredEffectEntry e : payload.effects) {
            PotionEffectType type = resolveEffect(e.effectKey);
            String name = type == null ? e.effectKey : prettify(type.getKey().getKey());
            int seconds = Math.max(0, e.durationTicks / 20);
            lore.add(Component.text(name + " ", NamedTextColor.GRAY)
                    .append(Component.text("Lv." + (e.amplifier + 1), NamedTextColor.AQUA))
                    .append(Component.text(" ", NamedTextColor.GRAY))
                    .append(Component.text(seconds + "s", NamedTextColor.GOLD)));
        }
        meta.lore(lore);

        item.setItemMeta(meta);
    }

    private PotionEffectType resolveEffect(String key) {
        if (key == null) {
            return null;
        }
        NamespacedKey nsk = NamespacedKey.fromString(key);
        if (nsk == null) {
            return null;
        }
        return Registry.POTION_EFFECT_TYPE.get(nsk);
    }

    private static String prettify(String k) {
        String[] parts = k.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private static String trim1(double d) {
        if (Math.abs(d - Math.rint(d)) < 0.0001) {
            return String.valueOf((int) Math.rint(d));
        }
        return String.format(Locale.ROOT, "%.1f", d);
    }

    public record ParsedItem(HvItemType type, StoredPayload payload) {
    }
}

