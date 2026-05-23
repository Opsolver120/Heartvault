package com.heartvault.db;

import com.heartvault.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

public final class Database {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private HikariDataSource dataSource;

    public Database(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void start() {
        String type = configManager.config().getString("database.type", "sqlite");
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("HeartVault");
        hikari.setMaximumPoolSize(10);
        hikari.setMinimumIdle(2);
        hikari.setConnectionTimeout(10_000);
        hikari.setLeakDetectionThreshold(0);

        if ("mysql".equalsIgnoreCase(type)) {
            String host = configManager.config().getString("database.mysql.host");
            int port = configManager.config().getInt("database.mysql.port");
            String database = configManager.config().getString("database.mysql.database");
            boolean useSSL = configManager.config().getBoolean("database.mysql.useSSL", false);
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL + "&useUnicode=true&characterEncoding=utf8";
            hikari.setJdbcUrl(url);
            hikari.setUsername(configManager.config().getString("database.mysql.username"));
            hikari.setPassword(configManager.config().getString("database.mysql.password"));
            Map<String, Object> props = configManager.config().getConfigurationSection("database.mysql.properties") == null
                    ? Map.of()
                    : configManager.config().getConfigurationSection("database.mysql.properties").getValues(false);
            for (Map.Entry<String, Object> e : props.entrySet()) {
                hikari.addDataSourceProperty(e.getKey(), String.valueOf(e.getValue()));
            }
        } else {
            File file = new File(plugin.getDataFolder(), configManager.config().getString("database.sqlite.file", "heartvault.db"));
            hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        }

        this.dataSource = new HikariDataSource(hikari);
        initSchema();
    }

    private void initSchema() {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS hv_player (
                      uuid TEXT PRIMARY KEY,
                      stored_hearts REAL NOT NULL DEFAULT 0,
                      permanent_hearts INTEGER NOT NULL DEFAULT 0,
                      updated_at INTEGER NOT NULL
                    )
                    """);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public HikariDataSource dataSource() {
        return dataSource;
    }

    public void stop() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}

