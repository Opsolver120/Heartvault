package com.heartvault.service;

import com.heartvault.config.ConfigManager;
import com.heartvault.db.Database;
import com.heartvault.vault.PlayerVault;
import com.heartvault.vault.SqlVaultRepository;
import com.heartvault.vault.VaultRepository;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VaultService implements Listener {
    private static final UUID PERM_HEARTS_MODIFIER = UUID.fromString("0e6e8fbf-3f56-4c41-9dfe-ff2775e4d5f2");

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final VaultRepository repository;
    private final Map<UUID, PlayerVault> cache = new ConcurrentHashMap<>();

    public VaultService(JavaPlugin plugin, ConfigManager configManager, Database database) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.repository = new SqlVaultRepository(database);
    }

    public PlayerVault getOrNull(UUID uuid) {
        return cache.get(uuid);
    }

    public PlayerVault require(UUID uuid) {
        PlayerVault v = cache.get(uuid);
        if (v == null) {
            v = repository.load(uuid);
            cache.put(uuid, v);
        }
        return v;
    }

    public void addStoredHearts(UUID uuid, double delta) {
        PlayerVault vault = require(uuid);
        double max = configManager.config().getDouble("storage.maxStoredHearts", 200.0);
        vault.setStoredHearts(clamp(vault.storedHearts() + delta, 0, max));
    }

    public boolean tryTakeStoredHearts(UUID uuid, double amount) {
        PlayerVault vault = require(uuid);
        if (vault.storedHearts() + 1e-9 < amount) {
            return false;
        }
        vault.setStoredHearts(vault.storedHearts() - amount);
        return true;
    }

    public void setPermanentHearts(UUID uuid, int hearts) {
        PlayerVault vault = require(uuid);
        vault.setPermanentHearts(Math.max(0, hearts));
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            applyPermanentHearts(player, vault.permanentHearts());
        }
    }

    public void shutdown() {
        for (PlayerVault vault : cache.values()) {
            try {
                repository.save(vault);
            } catch (Exception ignored) {
            }
        }
        cache.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerVault vault = repository.load(player.getUniqueId());
            cache.put(player.getUniqueId(), vault);
            player.getScheduler().run(plugin, task -> applyPermanentHearts(player, vault.permanentHearts()), () -> {});
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerVault vault = cache.remove(event.getPlayer().getUniqueId());
        if (vault != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.save(vault));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!configManager.config().getBoolean("storage.persistentBank", true)) {
            PlayerVault vault = cache.get(event.getEntity().getUniqueId());
            if (vault != null) {
                vault.setStoredHearts(0);
            }
        }
    }

    private void applyPermanentHearts(Player player, int hearts) {
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr == null) {
            return;
        }
        for (AttributeModifier modifier : attr.getModifiers()) {
            if (PERM_HEARTS_MODIFIER.equals(modifier.getUniqueId())) {
                attr.removeModifier(modifier);
                break;
            }
        }
        if (hearts > 0) {
            attr.addModifier(new AttributeModifier(PERM_HEARTS_MODIFIER, "heartvault_perm_hearts", hearts * 2.0, AttributeModifier.Operation.ADD_NUMBER));
        }
        double max = attr.getValue();
        if (player.getHealth() > max) {
            player.setHealth(max);
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}

