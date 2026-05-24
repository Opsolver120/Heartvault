package com.heartvault.service;

import com.heartvault.config.ConfigManager;
import com.heartvault.cooldown.CooldownService;
import com.heartvault.gui.GuiManager;
import com.heartvault.item.HvItemType;
import com.heartvault.item.ItemRegistry;
import com.heartvault.item.ItemRegistry.ParsedItem;
import com.heartvault.item.model.StoredEffectEntry;
import com.heartvault.item.model.StoredPayload;
import com.heartvault.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ExtractionService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ItemRegistry itemRegistry;
    private final VaultService vaultService;
    private final CooldownService cooldownService;
    private final CombatTagService combatTagService;
    private final GuiManager guiManager;
    private final Object unusedVisual;

    public ExtractionService(JavaPlugin plugin, ConfigManager configManager, ItemRegistry itemRegistry, VaultService vaultService, CooldownService cooldownService, CombatTagService combatTagService, GuiManager guiManager, Object unusedVisual) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRegistry = itemRegistry;
        this.vaultService = vaultService;
        this.cooldownService = cooldownService;
        this.combatTagService = combatTagService;
        this.guiManager = guiManager;
        this.unusedVisual = unusedVisual;
        this.guiManager.setExtractionService(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == null) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        ParsedItem parsed = itemRegistry.parse(item).orElse(null);
        if (parsed == null) {
            return;
        }

        if (parsed.type() == HvItemType.EXTRACTOR_HEART_SYRINGE || parsed.type() == HvItemType.EXTRACTOR_SOUL_FLASK) {
            event.setCancelled(true);
            if (!player.hasPermission("heartvault.extract")) {
                player.sendMessage(configManager.text().msgNoPrefix("errors.noPermission"));
                return;
            }
            if (configManager.config().getBoolean("combat.blockExtractWhileCombatTagged", false) && combatTagService.isInCombat(player)) {
                player.sendMessage(configManager.text().msg("errors.inCombat"));
                return;
            }
            guiManager.openExtraction(player);
            return;
        }

        if (parsed.type() == HvItemType.STORED_EFFECT || parsed.type() == HvItemType.STORED_HEARTS) {
            event.setCancelled(true);
            if (!player.hasPermission("heartvault.apply")) {
                player.sendMessage(configManager.text().msgNoPrefix("errors.noPermission"));
                return;
            }
            if (configManager.config().getBoolean("combat.blockApplyWhileCombatTagged", true) && combatTagService.isInCombat(player)) {
                player.sendMessage(configManager.text().msg("errors.inCombat"));
                return;
            }
            long remaining = cooldownService.remainingSeconds(player.getUniqueId(), "apply");
            if (remaining > 0) {
                player.sendMessage(configManager.text().msg("errors.cooldown", Text.p("seconds", String.valueOf(remaining))));
                return;
            }
            if (!cooldownService.tryUse(player.getUniqueId(), "apply", configManager.config().getInt("cooldowns.applySeconds", 2))) {
                return;
            }
            applyStoredItem(player, item, parsed);
            consumeHand(player, event.getHand());
        }
    }

    public void extractFromGui(Player player, Map<String, Boolean> toggles) {
        if (!player.hasPermission("heartvault.extract")) {
            player.sendMessage(configManager.text().msgNoPrefix("errors.noPermission"));
            return;
        }
        if (configManager.config().getBoolean("combat.blockExtractWhileCombatTagged", false) && combatTagService.isInCombat(player)) {
            player.sendMessage(configManager.text().msg("errors.inCombat"));
            return;
        }
        long remaining = cooldownService.remainingSeconds(player.getUniqueId(), "extract");
        if (remaining > 0) {
            player.sendMessage(configManager.text().msg("errors.cooldown", Text.p("seconds", String.valueOf(remaining))));
            return;
        }
        if (!cooldownService.tryUse(player.getUniqueId(), "extract", configManager.config().getInt("cooldowns.extractSeconds", 12))) {
            return;
        }

        EquipmentSlot hand = findExtractorHand(player);
        if (hand == null) {
            player.sendMessage(Component.text("Hold a HeartVault extractor in your hand.", NamedTextColor.RED));
            return;
        }

        int extracted = extractSelected(player, toggles);
        if (extracted <= 0) {
            player.sendMessage(configManager.text().msgNoPrefix("errors.noEffects"));
            return;
        }
        consumeHand(player, hand);
        player.closeInventory();
        player.sendMessage(configManager.text().msg("extract.done", Text.p("count", String.valueOf(extracted))));
        if (configManager.config().getBoolean("visuals.soundsEnabled", true)) {
            player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.4f);
        }
    }

    private int extractSelected(Player player, Map<String, Boolean> toggles) {
        int maxEffects = configManager.config().getInt("storage.maxStoredEffects", 256);
        int maxDurationTicks = configManager.config().getInt("storage.maxEffectDurationSeconds", 36000) * 20;
        int maxAmplifier = configManager.config().getInt("storage.maxEffectAmplifier", 4);

        int extractedCount = 0;
        for (Map.Entry<String, Boolean> e : toggles.entrySet()) {
            if (!Boolean.TRUE.equals(e.getValue())) {
                continue;
            }
            String key = e.getKey();
            if ("heartvault:absorption_hearts".equals(key)) {
                double hearts = player.getAbsorptionAmount() / 2.0;
                if (hearts > 0) {
                    StoredPayload payload = new StoredPayload();
                    payload.hearts = hearts;
                    ItemStack out = itemRegistry.createStoredItem("goldenHeartCapsule", payload);
                    InventoryMerge.giveOrMerge(player, out, itemRegistry);
                    player.setAbsorptionAmount(0);
                    extractedCount++;
                }
                continue;
            }

            PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.fromString(key));
            if (type == null) {
                continue;
            }
            PotionEffect current = player.getPotionEffect(type);
            if (current == null) {
                continue;
            }

            StoredEffectEntry entry = new StoredEffectEntry(
                    type.getKey().toString(),
                    Math.min(maxDurationTicks, current.getDuration()),
                    Math.min(maxAmplifier, current.getAmplifier()),
                    current.isAmbient(),
                    current.hasParticles(),
                    current.hasIcon()
            );

            StoredPayload payload = new StoredPayload();
            payload.effects.add(entry);

            String id = idFor(type);
            ItemStack out = itemRegistry.createStoredItem(id, payload);
            InventoryMerge.giveOrMerge(player, out, itemRegistry);

            player.removePotionEffect(type);
            extractedCount++;
            if (extractedCount >= maxEffects) {
                break;
            }
        }
        return extractedCount;
    }

    private void applyStoredItem(Player player, ItemStack item, ParsedItem parsed) {
        Text text = configManager.text();
        StoredPayload payload = parsed.payload();
        if (payload == null) {
            return;
        }

        int maxDurationTicks = configManager.config().getInt("storage.maxEffectDurationSeconds", 36000) * 20;

        if (payload.hearts > 0) {
            player.setAbsorptionAmount(player.getAbsorptionAmount() + payload.hearts * 2.0);
            player.sendMessage(text.msg("apply.done", Text.p("name", "Golden Hearts")));
            if (configManager.config().getBoolean("visuals.soundsEnabled", true)) {
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.6f);
            }
        }

        for (StoredEffectEntry e : payload.effects) {
            PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.fromString(e.effectKey));
            if (type == null) {
                continue;
            }
            PotionEffect current = player.getPotionEffect(type);
            int newAmplifier = Math.max(current == null ? 0 : current.getAmplifier(), e.amplifier);
            int newDuration;
            if (current != null && current.getAmplifier() == newAmplifier) {
                newDuration = current.getDuration() + e.durationTicks;
            } else {
                newDuration = Math.max(current == null ? 0 : current.getDuration(), e.durationTicks);
            }
            newDuration = Math.min(maxDurationTicks, newDuration);
            player.addPotionEffect(new PotionEffect(type, newDuration, newAmplifier, e.ambient, e.particles, e.icon), true);
            player.sendMessage(text.msg("apply.done", Text.p("name", prettify(type.getKey().getKey()))));
        }
    }

    private static void consumeHand(Player player, EquipmentSlot hand) {
        ItemStack current = hand == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (current == null || current.getType().isAir()) {
            return;
        }
        if (current.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
        } else {
            current.setAmount(current.getAmount() - 1);
        }
    }

    private EquipmentSlot findExtractorHand(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (itemRegistry.parse(main).map(ParsedItem::type).filter(t -> t == HvItemType.EXTRACTOR_HEART_SYRINGE || t == HvItemType.EXTRACTOR_SOUL_FLASK).isPresent()) {
            return EquipmentSlot.HAND;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (itemRegistry.parse(off).map(ParsedItem::type).filter(t -> t == HvItemType.EXTRACTOR_HEART_SYRINGE || t == HvItemType.EXTRACTOR_SOUL_FLASK).isPresent()) {
            return EquipmentSlot.OFF_HAND;
        }
        return null;
    }

    private static String idFor(PotionEffectType type) {
        if (type.equals(PotionEffectType.REGENERATION)) {
            return "bloodRegenOrb";
        }
        if (type.equals(PotionEffectType.FIRE_RESISTANCE)) {
            return "infernoEssence";
        }
        if (type.equals(PotionEffectType.RESISTANCE)) {
            return "resistanceTotem";
        }
        if (type.equals(PotionEffectType.SPEED)) {
            return "speedSerum";
        }
        if (type.equals(PotionEffectType.ABSORPTION)) {
            return "goldenHeartCapsule";
        }
        return "voidShield";
    }

    public boolean tryApplyItemInHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        ParsedItem parsed = itemRegistry.parse(hand).orElse(null);
        if (parsed == null || (parsed.type() != HvItemType.STORED_EFFECT && parsed.type() != HvItemType.STORED_HEARTS)) {
            return false;
        }
        if (!player.hasPermission("heartvault.apply")) {
            player.sendMessage(configManager.text().msgNoPrefix("errors.noPermission"));
            return true;
        }
        if (configManager.config().getBoolean("combat.blockApplyWhileCombatTagged", true) && combatTagService.isInCombat(player)) {
            player.sendMessage(configManager.text().msg("errors.inCombat"));
            return true;
        }
        long remaining = cooldownService.remainingSeconds(player.getUniqueId(), "apply");
        if (remaining > 0) {
            player.sendMessage(configManager.text().msg("errors.cooldown", Text.p("seconds", String.valueOf(remaining))));
            return true;
        }
        if (!cooldownService.tryUse(player.getUniqueId(), "apply", configManager.config().getInt("cooldowns.applySeconds", 2))) {
            return true;
        }
        applyStoredItem(player, hand, parsed);
        consumeHand(player, EquipmentSlot.HAND);
        return true;
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

    static final class InventoryMerge {
        static void giveOrMerge(Player player, ItemStack fresh, ItemRegistry itemRegistry) {
            ParsedItem parsedFresh = itemRegistry.parse(fresh).orElse(null);
            if (parsedFresh == null || parsedFresh.payload() == null) {
                player.getInventory().addItem(fresh);
                return;
            }
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack existing = player.getInventory().getItem(i);
                if (existing == null || existing.getType().isAir()) {
                    continue;
                }
                ParsedItem parsedExisting = itemRegistry.parse(existing).orElse(null);
                if (parsedExisting == null || parsedExisting.payload() == null) {
                    continue;
                }
                if (parsedExisting.type() != parsedFresh.type()) {
                    continue;
                }
                if (!Objects.equals(parsedExisting.payload().id, parsedFresh.payload().id)) {
                    continue;
                }
                if (parsedExisting.payload().effects.size() != parsedFresh.payload().effects.size()) {
                    continue;
                }
                boolean canMerge = true;
                for (int idx = 0; idx < parsedExisting.payload().effects.size(); idx++) {
                    StoredEffectEntry a = parsedExisting.payload().effects.get(idx);
                    StoredEffectEntry b = parsedFresh.payload().effects.get(idx);
                    if (!Objects.equals(a.effectKey, b.effectKey) || a.amplifier != b.amplifier) {
                        canMerge = false;
                        break;
                    }
                }
                if (!canMerge) {
                    continue;
                }
                parsedExisting.payload().hearts += parsedFresh.payload().hearts;
                for (int idx = 0; idx < parsedExisting.payload().effects.size(); idx++) {
                    parsedExisting.payload().effects.get(idx).durationTicks += parsedFresh.payload().effects.get(idx).durationTicks;
                }
                itemRegistry.updatePayload(existing, parsedExisting.payload());
                if (player.getInventory().getItem(i) != null) {
                    return;
                }
            }
            player.getInventory().addItem(fresh);
        }
    }
}
