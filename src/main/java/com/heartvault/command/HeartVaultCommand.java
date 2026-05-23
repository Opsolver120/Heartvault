package com.heartvault.command;

import com.heartvault.config.ConfigManager;
import com.heartvault.gui.GuiManager;
import com.heartvault.item.HvItemType;
import com.heartvault.item.ItemRegistry;
import com.heartvault.item.model.StoredPayload;
import com.heartvault.service.ExtractionService;
import com.heartvault.service.VaultService;
import com.heartvault.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HeartVaultCommand implements CommandExecutor, TabCompleter {
    private final ConfigManager configManager;
    private final ItemRegistry itemRegistry;
    private final VaultService vaultService;
    private final ExtractionService extractionService;
    private final GuiManager guiManager;

    public HeartVaultCommand(ConfigManager configManager, ItemRegistry itemRegistry, VaultService vaultService, ExtractionService extractionService, GuiManager guiManager) {
        this.configManager = configManager;
        this.itemRegistry = itemRegistry;
        this.vaultService = vaultService;
        this.extractionService = extractionService;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Text text = configManager.text();

        if (args.length == 0) {
            sender.sendMessage(Component.text("HeartVault", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("/hv extract, /hv bank, /hv deposit <hearts>, /hv withdraw <hearts>", NamedTextColor.GRAY));
            if (sender.hasPermission("heartvault.admin")) {
                sender.sendMessage(Component.text("/hv give <player> <heartSyringe|soulFlask>", NamedTextColor.DARK_GRAY));
                sender.sendMessage(Component.text("/hv reload", NamedTextColor.DARK_GRAY));
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "extract" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(text.msgNoPrefix("errors.playerOnly"));
                    return true;
                }
                if (!player.hasPermission("heartvault.extract")) {
                    player.sendMessage(text.msgNoPrefix("errors.noPermission"));
                    return true;
                }
                guiManager.openExtraction(player);
                return true;
            }
            case "bank" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(text.msgNoPrefix("errors.playerOnly"));
                    return true;
                }
                if (!player.hasPermission("heartvault.bank")) {
                    player.sendMessage(text.msgNoPrefix("errors.noPermission"));
                    return true;
                }
                guiManager.openBank(player);
                return true;
            }
            case "deposit" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(text.msgNoPrefix("errors.playerOnly"));
                    return true;
                }
                if (!player.hasPermission("heartvault.bank")) {
                    player.sendMessage(text.msgNoPrefix("errors.noPermission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /hv deposit <hearts>", NamedTextColor.RED));
                    return true;
                }
                double hearts;
                try {
                    hearts = Double.parseDouble(args[1]);
                } catch (Exception e) {
                    player.sendMessage(text.msgNoPrefix("errors.invalidNumber"));
                    return true;
                }
                hearts = Math.max(0, hearts);
                double available = player.getAbsorptionAmount() / 2.0;
                double take = Math.min(available, hearts);
                if (take <= 0) {
                    player.sendMessage(text.msgNoPrefix("errors.noEffects"));
                    return true;
                }
                vaultService.addStoredHearts(player.getUniqueId(), take);
                player.setAbsorptionAmount(Math.max(0, player.getAbsorptionAmount() - take * 2.0));
                player.sendMessage(text.msg("bank.deposit", Text.p("hearts", trim1(take))));
                return true;
            }
            case "withdraw" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(text.msgNoPrefix("errors.playerOnly"));
                    return true;
                }
                if (!player.hasPermission("heartvault.bank")) {
                    player.sendMessage(text.msgNoPrefix("errors.noPermission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /hv withdraw <hearts>", NamedTextColor.RED));
                    return true;
                }
                double hearts;
                try {
                    hearts = Double.parseDouble(args[1]);
                } catch (Exception e) {
                    player.sendMessage(text.msgNoPrefix("errors.invalidNumber"));
                    return true;
                }
                hearts = Math.max(0, hearts);
                if (!vaultService.tryTakeStoredHearts(player.getUniqueId(), hearts)) {
                    player.sendMessage(text.msgNoPrefix("errors.notEnoughHearts"));
                    return true;
                }
                player.setAbsorptionAmount(player.getAbsorptionAmount() + hearts * 2.0);
                player.sendMessage(text.msg("bank.withdraw", Text.p("hearts", trim1(hearts))));
                return true;
            }
            case "apply" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(text.msgNoPrefix("errors.playerOnly"));
                    return true;
                }
                if (!extractionService.tryApplyItemInHand(player)) {
                    player.sendMessage(Component.text("Hold a stored HeartVault item.", NamedTextColor.RED));
                }
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("heartvault.admin.reload")) {
                    sender.sendMessage(text.msgNoPrefix("errors.noPermission"));
                    return true;
                }
                configManager.reload();
                sender.sendMessage(Component.text("HeartVault reloaded.", NamedTextColor.GREEN));
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("heartvault.admin.give")) {
                    sender.sendMessage(text.msgNoPrefix("errors.noPermission"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /hv give <player> <heartSyringe|soulFlask>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                String itemId = args[2].toLowerCase(Locale.ROOT);
                ItemStack out;
                if ("heartsyringe".equals(itemId)) {
                    out = itemRegistry.createExtractor(HvItemType.EXTRACTOR_HEART_SYRINGE);
                } else if ("soulflask".equals(itemId)) {
                    out = itemRegistry.createExtractor(HvItemType.EXTRACTOR_SOUL_FLASK);
                } else if ("goldenheartcapsule".equals(itemId)) {
                    StoredPayload payload = new StoredPayload();
                    payload.hearts = 4.0;
                    out = itemRegistry.createStoredItem("goldenHeartCapsule", payload);
                } else {
                    sender.sendMessage(Component.text("Unknown item id.", NamedTextColor.RED));
                    return true;
                }
                target.getInventory().addItem(out);
                sender.sendMessage(Component.text("Given.", NamedTextColor.GREEN));
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> s = new ArrayList<>();
            s.add("extract");
            s.add("apply");
            s.add("bank");
            s.add("deposit");
            s.add("withdraw");
            if (sender.hasPermission("heartvault.admin")) {
                s.add("give");
                s.add("reload");
            }
            return filter(s, args[0]);
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            List<String> s = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                s.add(p.getName());
            }
            return filter(s, args[1]);
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            return filter(List.of("heartSyringe", "soulFlask", "goldenHeartCapsule"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> all, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : all) {
            if (s.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(s);
            }
        }
        return out;
    }

    private static String trim1(double d) {
        if (Math.abs(d - Math.rint(d)) < 0.0001) {
            return String.valueOf((int) Math.rint(d));
        }
        return String.format(Locale.ROOT, "%.1f", d);
    }
}
