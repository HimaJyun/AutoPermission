package jp.jyn.autopermission.config;

import jp.jyn.autopermission.AutoPermission;
import jp.jyn.jbukkitlib.config.parser.TimeParser;
import jp.jyn.jbukkitlib.util.PackagePrivate;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("ConstantConditions")
public class MainConfig {
    public final boolean checkVersion;

    public final NavigableMap<Long, String> permission;
    public final List<String> priority;

    public final DatabaseConfig database;

    public final int checkInterval; // secret option

    @PackagePrivate
    MainConfig(ConfigurationSection config) {
        checkVersion = config.getBoolean("checkVersion");

        if (config.contains("permission", true)) {
            NavigableMap<Long, String> tmpPermission = new TreeMap<>();
            ConfigurationSection section = config.getConfigurationSection("permission");
            for (String key : section.getKeys(false)) {
                tmpPermission.put(
                    TimeParser.parse(key, TimeUnit.MILLISECONDS),
                    section.getString(key)
                );
            }
            permission = Collections.unmodifiableNavigableMap(tmpPermission);
        } else {
            permission = Collections.emptyNavigableMap();
        }

        priority = (!config.contains("priority", true) ?
            Collections.emptyList() :
            Collections.unmodifiableList(
                new ArrayList<>(config.getStringList("priority"))
            )
        );

        database = new DatabaseConfig(config.getConfigurationSection("database"));

        checkInterval = config.getInt("checkInterval", 60);
    }

    public final static class DatabaseConfig {
        public final String url;
        public final String username;
        public final String password;
        public final String init; // secret option
        public final Properties properties = new Properties();

        public final int maximumPoolSize;
        public final int minimumIdle;
        public final long maxLifetime;
        public final long connectionTimeout;
        public final long idleTimeout;

        @SuppressWarnings("ResultOfMethodCallIgnored")
        private DatabaseConfig(ConfigurationSection config) {
            String type = config.getString("type", "").toLowerCase(Locale.ENGLISH);
            ConfigurationSection db = config.getConfigurationSection(type);

            switch (type) {
                case "sqlite":
                    File file = new File(AutoPermission.getInstance().getDataFolder(), db.getString("file"));
                    file.getParentFile().mkdirs();
                    url = "jdbc:sqlite:" + file.getPath();
                    break;
                case "mysql":
                    url = String.format(
                        "jdbc:mysql://%s/%s",
                        db.getString("host"),
                        db.getString("name")
                    );
                    break;
                default:
                    throw new IllegalArgumentException("Invalid value: database.type(config.yml)");
            }
            username = db.getString("username");
            password = db.getString("password");
            init = db.getString("init", "/* AutoPermission */SELECT 1");

            if (db.contains("properties")) {
                ConfigurationSection section = db.getConfigurationSection("properties");
                for (String key : section.getKeys(false)) {
                    properties.put(key, section.getString(key));
                }
            }

            maximumPoolSize = config.getInt("connectionPool.maximumPoolSize");
            minimumIdle = config.getInt("connectionPool.minimumIdle");
            maxLifetime = config.getLong("connectionPool.maxLifetime");
            connectionTimeout = config.getLong("connectionPool.connectionTimeout");
            idleTimeout = config.getLong("connectionPool.idleTimeout");
        }
    }
}
