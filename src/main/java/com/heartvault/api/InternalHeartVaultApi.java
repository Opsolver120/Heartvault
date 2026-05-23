package com.heartvault.api;

import com.heartvault.gui.GuiManager;
import com.heartvault.item.ItemRegistry;
import com.heartvault.item.model.StoredPayload;
import com.heartvault.service.VaultService;
import com.heartvault.vault.PlayerVault;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

public final class InternalHeartVaultApi implements HeartVaultApi {
    private final VaultService vaultService;
    private final ItemRegistry itemRegistry;
    private final GuiManager guiManager;

    public InternalHeartVaultApi(VaultService vaultService, ItemRegistry itemRegistry, GuiManager guiManager) {
        this.vaultService = vaultService;
        this.itemRegistry = itemRegistry;
        this.guiManager = guiManager;
    }

    @Override
    public Optional<Double> getStoredHearts(UUID playerUuid) {
        PlayerVault v = vaultService.getOrNull(playerUuid);
        return v == null ? Optional.empty() : Optional.of(v.storedHearts());
    }

    @Override
    public void addStoredHearts(UUID playerUuid, double hearts) {
        vaultService.addStoredHearts(playerUuid, hearts);
    }

    @Override
    public boolean tryTakeStoredHearts(UUID playerUuid, double hearts) {
        return vaultService.tryTakeStoredHearts(playerUuid, hearts);
    }

    @Override
    public ItemStack createStoredItem(String displayId, StoredPayload payload) {
        return itemRegistry.createStoredItem(displayId, payload);
    }

    @Override
    public void openBank(Player player) {
        guiManager.openBank(player);
    }
}

