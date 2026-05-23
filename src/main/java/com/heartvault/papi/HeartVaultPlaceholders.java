package com.heartvault.papi;

import com.heartvault.service.VaultService;
import com.heartvault.vault.PlayerVault;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public final class HeartVaultPlaceholders extends PlaceholderExpansion {
    private final Plugin plugin;
    private final VaultService vaultService;

    public HeartVaultPlaceholders(Plugin plugin, VaultService vaultService) {
        this.plugin = plugin;
        this.vaultService = vaultService;
    }

    @Override
    public String getIdentifier() {
        return "heartvault";
    }

    @Override
    public String getAuthor() {
        return "HeartVault";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) {
            return "";
        }
        String p = params == null ? "" : params.toLowerCase(Locale.ROOT);
        PlayerVault vault = vaultService.getOrNull(player.getUniqueId());
        if (vault == null) {
            return "0";
        }
        return switch (p) {
            case "stored_hearts" -> trim1(vault.storedHearts());
            case "permanent_hearts" -> String.valueOf(vault.permanentHearts());
            default -> "";
        };
    }

    private static String trim1(double d) {
        if (Math.abs(d - Math.rint(d)) < 0.0001) {
            return String.valueOf((int) Math.rint(d));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", d);
    }
}

