package com.heartvault.vault;

import java.util.UUID;

public final class PlayerVault {
    private final UUID uuid;
    private double storedHearts;
    private int permanentHearts;
    private long updatedAtMillis;

    public PlayerVault(UUID uuid, double storedHearts, int permanentHearts, long updatedAtMillis) {
        this.uuid = uuid;
        this.storedHearts = storedHearts;
        this.permanentHearts = permanentHearts;
        this.updatedAtMillis = updatedAtMillis;
    }

    public UUID uuid() {
        return uuid;
    }

    public double storedHearts() {
        return storedHearts;
    }

    public void setStoredHearts(double storedHearts) {
        this.storedHearts = storedHearts;
        touch();
    }

    public int permanentHearts() {
        return permanentHearts;
    }

    public void setPermanentHearts(int permanentHearts) {
        this.permanentHearts = permanentHearts;
        touch();
    }

    public long updatedAtMillis() {
        return updatedAtMillis;
    }

    public void touch() {
        this.updatedAtMillis = System.currentTimeMillis();
    }
}

