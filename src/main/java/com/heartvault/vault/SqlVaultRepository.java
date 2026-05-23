package com.heartvault.vault;

import com.heartvault.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;

public final class SqlVaultRepository implements VaultRepository {
    private final Database database;

    public SqlVaultRepository(Database database) {
        this.database = database;
    }

    @Override
    public PlayerVault load(UUID uuid) {
        try (Connection c = database.dataSource().getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT stored_hearts, permanent_hearts, updated_at FROM hv_player WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        double hearts = rs.getDouble(1);
                        int perm = rs.getInt(2);
                        long updatedAt = rs.getLong(3);
                        return new PlayerVault(uuid, hearts, perm, updatedAt);
                    }
                }
            }

            try (PreparedStatement ps = c.prepareStatement("INSERT INTO hv_player (uuid, stored_hearts, permanent_hearts, updated_at) VALUES (?, 0, 0, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, Instant.now().toEpochMilli());
                ps.executeUpdate();
            }
            return new PlayerVault(uuid, 0, 0, System.currentTimeMillis());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void save(PlayerVault vault) {
        try (Connection c = database.dataSource().getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE hv_player SET stored_hearts = ?, permanent_hearts = ?, updated_at = ? WHERE uuid = ?")) {
            ps.setDouble(1, vault.storedHearts());
            ps.setInt(2, vault.permanentHearts());
            ps.setLong(3, vault.updatedAtMillis());
            ps.setString(4, vault.uuid().toString());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

