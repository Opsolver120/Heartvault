package com.heartvault.item.model;

import java.util.ArrayList;
import java.util.List;

public final class StoredPayload {
    public String id;
    public List<StoredEffectEntry> effects = new ArrayList<>();
    public double hearts;

    public StoredPayload() {
    }
}

