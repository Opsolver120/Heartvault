package com.heartvault.api;

import com.heartvault.item.model.StoredPayload;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

public interface HeartVaultApi {
    Optional<Double> getStoredHearts(UUID playerUuid);

    void addStoredHearts(UUID playerUuid, double hearts);

    boolean tryTakeStoredHearts(UUID playerUuid, double hearts);

    ItemStack createStoredItem(String displayId, StoredPayload payload);

    void openBank(Player player);
}

