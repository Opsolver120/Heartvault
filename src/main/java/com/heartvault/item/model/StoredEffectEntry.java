package com.heartvault.item.model;

public final class StoredEffectEntry {
    public String effectKey;
    public int durationTicks;
    public int amplifier;
    public boolean ambient;
    public boolean particles;
    public boolean icon;

    public StoredEffectEntry() {
    }

    public StoredEffectEntry(String effectKey, int durationTicks, int amplifier, boolean ambient, boolean particles, boolean icon) {
        this.effectKey = effectKey;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
        this.ambient = ambient;
        this.particles = particles;
        this.icon = icon;
    }
}

