package com.heartvault;

import com.heartvault.command.HeartVaultCommand;
import com.heartvault.config.ConfigManager;
import com.heartvault.cooldown.CooldownService;
import com.heartvault.db.Database;
import com.heartvault.api.HeartVaultProvider;
import com.heartvault.api.InternalHeartVaultApi;
import com.heartvault.gui.GuiManager;
import com.heartvault.item.ItemRegistry;
import com.heartvault.papi.HeartVaultPlaceholders;
import com.heartvault.service.CombatTagService;
import com.heartvault.service.ExtractionService;
import com.heartvault.service.VaultService;
import com.heartvault.visual.VisualService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class HeartVaultPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private Database database;
    private CooldownService cooldownService;
    private CombatTagService combatTagService;
    private ItemRegistry itemRegistry;
    private VaultService vaultService;
    private VisualService visualService;
    private GuiManager guiManager;
    private ExtractionService extractionService;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.reload();

        this.database = new Database(this, configManager);
        this.database.start();

        this.cooldownService = new CooldownService();
        this.combatTagService = new CombatTagService(this, configManager);
        this.itemRegistry = new ItemRegistry(this, configManager);
        this.vaultService = new VaultService(this, configManager, database);
        this.visualService = new VisualService(this, configManager, vaultService);
        this.guiManager = new GuiManager(this, configManager, itemRegistry, vaultService, cooldownService, combatTagService, visualService);
        this.extractionService = new ExtractionService(this, configManager, itemRegistry, vaultService, cooldownService, combatTagService, guiManager, visualService);

        HeartVaultProvider.set(new InternalHeartVaultApi(vaultService, itemRegistry, guiManager));

        Bukkit.getPluginManager().registerEvents(extractionService, this);
        Bukkit.getPluginManager().registerEvents(guiManager, this);
        Bukkit.getPluginManager().registerEvents(combatTagService, this);
        Bukkit.getPluginManager().registerEvents(vaultService, this);

        HeartVaultCommand command = new HeartVaultCommand(configManager, itemRegistry, vaultService, extractionService, guiManager);
        getCommand("heartvault").setExecutor(command);
        getCommand("heartvault").setTabCompleter(command);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new HeartVaultPlaceholders(this, vaultService).register();
        }

        visualService.start();
    }

    @Override
    public void onDisable() {
        if (visualService != null) {
            visualService.stop();
        }
        if (vaultService != null) {
            vaultService.shutdown();
        }
        if (database != null) {
            database.stop();
        }
    }

    public ConfigManager configManager() {
        return configManager;
    }
}
