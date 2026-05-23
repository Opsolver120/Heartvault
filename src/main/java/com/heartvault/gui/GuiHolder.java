package com.heartvault.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GuiHolder implements InventoryHolder {
    private final GuiType type;
    private Inventory inventory;
    private final UUID owner;
    private final Map<Integer, String> slotMap = new HashMap<>();
    private final Map<String, Boolean> toggles = new HashMap<>();

    public GuiHolder(GuiType type, UUID owner) {
        this.type = type;
        this.owner = owner;
    }

    public GuiType type() {
        return type;
    }

    public UUID owner() {
        return owner;
    }

    public Map<Integer, String> slotMap() {
        return slotMap;
    }

    public Map<String, Boolean> toggles() {
        return toggles;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}

