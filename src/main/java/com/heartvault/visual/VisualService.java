package com.heartvault.visual;

import com.heartvault.config.ConfigManager;
import com.heartvault.service.CombatTagService;
import com.heartvault.service.VaultService;
import com.heartvault.text.Text;
import com.heartvault.vault.PlayerVault;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VisualService {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final VaultService vaultService;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private int taskId = -1;

    public VisualService(JavaPlugin plugin, ConfigManager configManager, VaultService vaultService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.vaultService = vaultService;
    }

    public void start() {
        stop();
        this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }

    private void tick() {
        boolean actionbar = configManager.config().getBoolean("visuals.actionbarEnabled", true);
        boolean bossbar = configManager.config().getBoolean("visuals.bossbarEnabled", true);

        if (!bossbar) {
            for (BossBar bar : bars.values()) {
                bar.removeAll();
            }
            bars.clear();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (bossbar) {
                BossBar bar = bars.computeIfAbsent(player.getUniqueId(), k -> {
                    BarColor c = BarColor.valueOf(configManager.config().getString("visuals.bossbarColor", "YELLOW"));
                    BarStyle s = BarStyle.valueOf(configManager.config().getString("visuals.bossbarStyle", "SOLID"));
                    BossBar bb = Bukkit.createBossBar("", c, s);
                    bb.addPlayer(player);
                    return bb;
                });

                PlayerVault vault = vaultService.getOrNull(player.getUniqueId());
                double hearts = vault == null ? 0 : vault.storedHearts();
                double max = configManager.config().getDouble("storage.maxStoredHearts", 200.0);
                bar.setProgress(max <= 0 ? 0 : Math.max(0, Math.min(1, hearts / max)));
                bar.setTitle("HeartVault: " + trim1(hearts) + " / " + trim1(max));
            }

            if (actionbar) {
                PlayerVault vault = vaultService.getOrNull(player.getUniqueId());
                double hearts = vault == null ? 0 : vault.storedHearts();
                Component msg = Component.text("HV ", NamedTextColor.GOLD)
                        .append(Component.text(trim1(hearts) + "♥", NamedTextColor.YELLOW))
                        .append(Component.text(" | Abs ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(trim1(player.getAbsorptionAmount() / 2.0) + "♥", NamedTextColor.GOLD));
                player.sendActionBar(msg);
            }

            tickOverhealDecay(player);
        }
    }

    private void tickOverhealDecay(Player player) {
        if (!configManager.config().getBoolean("storage.overheal.enabled", true)) {
            return;
        }
        double vanillaMaxHearts = configManager.config().getDouble("storage.overheal.vanillaMaxHearts", 20.0);
        double decayHeartsPerSecond = configManager.config().getDouble("storage.overheal.decayPerSecond", 0.5);

        double totalHearts = (player.getHealth() + player.getAbsorptionAmount()) / 2.0;
        if (totalHearts <= vanillaMaxHearts + 1e-9) {
            return;
        }
        double reduceHearts = Math.min(decayHeartsPerSecond, player.getAbsorptionAmount() / 2.0);
        if (reduceHearts <= 0) {
            return;
        }
        player.setAbsorptionAmount(Math.max(0, player.getAbsorptionAmount() - reduceHearts * 2.0));
    }

    private static String trim1(double d) {
        if (Math.abs(d - Math.rint(d)) < 0.0001) {
            return String.valueOf((int) Math.rint(d));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", d);
    }
}

