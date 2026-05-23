package com.heartvault.vault;

import java.util.UUID;

public interface VaultRepository {
    PlayerVault load(UUID uuid);

    void save(PlayerVault vault);
}

