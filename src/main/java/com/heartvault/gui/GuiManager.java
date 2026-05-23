package com.heartvault.gui;

import com.heartvault.config.ConfigManager;
import com.heartvault.cooldown.CooldownService;
import com.heartvault.item.ItemRegistry;
import com.heartvault.service.CombatTagService;
import com.heartvault.service.ExtractionService;
import com.heartvault.service.VaultService;
import com.heartvault.text.Text;
import com.heartvault.vault.PlayerVault;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class GuiManager implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ItemRegistry itemRegistry;
    private final VaultService vaultService;
    private final CooldownService cooldownService;
    private final CombatTagService combatTagService;
    private ExtractionService extractionService;

    public GuiManager(JavaPlugin plugin, ConfigManager configManager, ItemRegistry itemRegistry, VaultService vaultService, CooldownService cooldownService, CombatTagService combatTagService, Object unusedVisual) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRegistry = itemRegistry;
        this.vaultService = vaultService;
        this.cooldownService = cooldownService;
        this.combatTagService = combatTagService;
    }

    public void setExtractionService(ExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    public void openBank(Player player) {
        GuiHolder holder = new GuiHolder(GuiType.BANK, player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("HeartVault Bank", NamedTextColor.GOLD));
        holder.setInventory(inv);

        PlayerVault vault = vaultService.require(player.getUniqueId());
        double hearts = vault.storedHearts();
        double max = configManager.config().getDouble("storage.maxStoredHearts", 200.0);

        inv.setItem(11, button(Material.EMERALD, "Deposit Absorption", List.of(
                Component.text("Convert your current absorption hearts", NamedTextColor.GRAY),
                Component.text("into stored heart energy.", NamedTextColor.GRAY)
        )));
        inv.setItem(15, button(Material.REDSTONE, "Withdraw Absorption", List.of(
                Component.text("Withdraw stored heart energy", NamedTextColor.GRAY),
                Component.text("as absorption hearts.", NamedTextColor.GRAY),
                Component.text("Click: 2 hearts", NamedTextColor.DARK_GRAY),
                Component.text("Shift-Click: 10 hearts", NamedTextColor.DARK_GRAY)
        )));
        inv.setItem(13, button(Material.GOLDEN_APPLE, "Stored Heart Energy", List.of(
                Component.text("Bank: " + trim1(hearts) + " / " + trim1(max) + " hearts", NamedTextColor.GOLD),
                Component.text("Absorption: " + trim1(player.getAbsorptionAmount() / 2.0) + " hearts", NamedTextColor.YELLOW)
        )));
        inv.setItem(26, button(Material.BARRIER, "Close", List.of()));

        player.openInventory(inv);
        player.sendMessage(configManager.text().msg("bank.opened"));
    }

    public void openExtraction(Player player) {
        GuiHolder holder = new GuiHolder(GuiType.EXTRACT, player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Extract Effects", NamedTextColor.LIGHT_PURPLE));
        holder.setInventory(inv);

        List<PotionEffect> effects = new ArrayList<>(player.getActivePotionEffects());
        effects.sort(Comparator.comparing(e -> e.getType().getKey().toString()));

        int slot = 10;
        for (PotionEffect e : effects) {
            if (slot >= 44) {
                break;
            }
            String k = e.getType().getKey().toString();
            holder.slotMap().put(slot, k);
            holder.toggles().put(k, true);
            inv.setItem(slot, effectIcon(e, true));
            slot = nextSlot(slot);
        }

        if (configManager.config().getBoolean("storage.allowAbsorptionExtraction", true) && player.getAbsorptionAmount() > 0) {
            String k = "heartvault:absorption_hearts";
            holder.slotMap().put(46, k);
            holder.toggles().put(k, true);
            inv.setItem(46, button(Material.GOLD_NUGGET, "Absorption Hearts", List.of(
                    Component.text("Current: " + trim1(player.getAbsorptionAmount() / 2.0) + " hearts", NamedTextColor.GOLD),
                    Component.text("Click to toggle", NamedTextColor.DARK_GRAY)
            )));
        }

        inv.setItem(49, button(Material.NETHER_STAR, "Extract", List.of(
                Component.text("Consumes your extractor and", NamedTextColor.GRAY),
                Component.text("creates tradable effect items.", NamedTextColor.GRAY)
        )));
        inv.setItem(53, button(Material.BARRIER, "Close", List.of()));

        player.openInventory(inv);
        player.sendMessage(configManager.text().msg("extract.opened"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        if (!holder.owner().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        if (event.getClickedInventory() == null || event.getClickedInventory() != holder.getInventory()) {
            return;
        }

        if (holder.type() == GuiType.BANK) {
            handleBankClick(player, holder, event.getSlot(), event.getClick());
            return;
        }
        if (holder.type() == GuiType.EXTRACT) {
            handleExtractClick(player, holder, event.getSlot());
        }
    }

    private void handleBankClick(Player player, GuiHolder holder, int slot, ClickType clickType) {
        Text text = configManager.text();
        if (slot == 26) {
            player.closeInventory();
            return;
        }

        if (slot == 11) {
            long remaining = cooldownService.remainingSeconds(player.getUniqueId(), "deposit");
            if (remaining > 0) {
                player.sendMessage(text.msg("errors.cooldown", Text.p("seconds", String.valueOf(remaining))));
                return;
            }
            if (!cooldownService.tryUse(player.getUniqueId(), "deposit", configManager.config().getInt("cooldowns.depositSeconds", 1))) {
                return;
            }
            double hearts = player.getAbsorptionAmount() / 2.0;
            if (hearts <= 0) {
                player.sendMessage(text.msgNoPrefix("errors.noEffects"));
                return;
            }
            vaultService.addStoredHearts(player.getUniqueId(), hearts);
            player.setAbsorptionAmount(0);
            player.sendMessage(text.msg("bank.deposit", Text.p("hearts", trim1(hearts))));
            Bukkit.getScheduler().runTask(plugin, () -> openBank(player));
            return;
        }

        if (slot == 15) {
            long remaining = cooldownService.remainingSeconds(player.getUniqueId(), "withdraw");
            if (remaining > 0) {
                player.sendMessage(text.msg("errors.cooldown", Text.p("seconds", String.valueOf(remaining))));
                return;
            }
            if (!cooldownService.tryUse(player.getUniqueId(), "withdraw", configManager.config().getInt("cooldowns.withdrawSeconds", 1))) {
                return;
            }

            double amount = clickType.isShiftClick() ? 10.0 : 2.0;
            if (!vaultService.tryTakeStoredHearts(player.getUniqueId(), amount)) {
                player.sendMessage(text.msgNoPrefix("errors.notEnoughHearts"));
                return;
            }
            player.setAbsorptionAmount(player.getAbsorptionAmount() + amount * 2.0);
            player.sendMessage(text.msg("bank.withdraw", Text.p("hearts", trim1(amount))));
            Bukkit.getScheduler().runTask(plugin, () -> openBank(player));
        }
    }

    private void handleExtractClick(Player player, GuiHolder holder, int slot) {
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (slot == 49) {
            if (extractionService != null) {
                extractionService.extractFromGui(player, holder.toggles());
            }
            return;
        }
        String key = holder.slotMap().get(slot);
        if (key == null) {
            return;
        }
        boolean enabled = holder.toggles().getOrDefault(key, false);
        holder.toggles().put(key, !enabled);
        ItemStack current = holder.getInventory().getItem(slot);
        if (current != null && current.getType() != Material.AIR) {
            if ("heartvault:absorption_hearts".equals(key)) {
                holder.getInventory().setItem(slot, button(Material.GOLD_NUGGET, "Absorption Hearts", List.of(
                        Component.text("Current: " + trim1(player.getAbsorptionAmount() / 2.0) + " hearts", NamedTextColor.GOLD),
                        Component.text((!enabled ? "Selected" : "Deselected"), (!enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                )));
                return;
            }
            PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(org.bukkit.NamespacedKey.fromString(key));
            PotionEffect eff = type == null ? null : player.getPotionEffect(type);
            if (eff != null) {
                holder.getInventory().setItem(slot, effectIcon(eff, !enabled));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder holder) {
            if (holder.type() == GuiType.EXTRACT) {
                holder.slotMap().clear();
                holder.toggles().clear();
            }
        }
    }

    private ItemStack effectIcon(PotionEffect effect, boolean enabled) {
        Material m = Material.POTION;
        String name = prettify(effect.getType().getKey().getKey());
        int seconds = effect.getDuration() / 20;

        List<Component> lore = List.of(
                Component.text("Lv." + (effect.getAmplifier() + 1) + " • " + seconds + "s", NamedTextColor.GRAY),
                Component.text("Click to toggle", NamedTextColor.DARK_GRAY),
                Component.text(enabled ? "Selected" : "Deselected", enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
        );
        ItemStack item = button(m, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(enabled);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack button(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static int nextSlot(int slot) {
        if ((slot + 1) % 9 == 8) {
            return slot + 3;
        }
        return slot + 1;
    }

    private static String prettify(String k) {
        String[] parts = k.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private static String trim1(double d) {
        if (Math.abs(d - Math.rint(d)) < 0.0001) {
            return String.valueOf((int) Math.rint(d));
        }
        return String.format(Locale.ROOT, "%.1f", d);
    }
}

