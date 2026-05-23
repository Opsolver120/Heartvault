package com.heartvault.service;

import com.heartvault.config.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatTagService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<UUID, Long> combatUntilMillis = new ConcurrentHashMap<>();

    public CombatTagService(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public boolean isInCombat(Player player) {
        Long until = combatUntilMillis.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public long remainingSeconds(Player player) {
        Long until = combatUntilMillis.get(player.getUniqueId());
        if (until == null) {
            return 0;
        }
        long remaining = until - System.currentTimeMillis();
        return Math.max(0, (remaining + 999) / 1000);
    }

    private void tag(Player player) {
        int seconds = configManager.config().getInt("combat.combatTagSeconds", 15);
        combatUntilMillis.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            tag(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            tag(player);
        }
    }
}

