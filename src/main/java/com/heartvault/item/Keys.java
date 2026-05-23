package com.heartvault.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class Keys {
    public final NamespacedKey itemType;
    public final NamespacedKey payload;
    public final NamespacedKey amount;
    public final NamespacedKey extractorTier;

    public Keys(Plugin plugin) {
        this.itemType = new NamespacedKey(plugin, "hv_type");
        this.payload = new NamespacedKey(plugin, "hv_payload");
        this.amount = new NamespacedKey(plugin, "hv_amount");
        this.extractorTier = new NamespacedKey(plugin, "hv_tier");
    }
}

