package jp.jyn.autopermission.config;

import jp.jyn.autopermission.AutoPermission;
import jp.jyn.jbukkitlib.util.PackagePrivate;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.Locale;
import java.util.Properties;

@SuppressWarnings("ConstantConditions")
public class MainConfig {
    public final boolean checkVersion;

    public final DatabaseConfig database;

    public final int checkInterval; // secret option

    @PackagePrivate
    MainConfig(ConfigurationSection config) {
        checkVersion = config.getBoolean("checkVersion");
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
            if ("sqlite".equalsIgnoreCase(type)) {
                File file = new File(AutoPermission.getInstance().getDataFolder(), config.getString("sqlite.file"));
                file.getParentFile().mkdirs();
                url = "jdbc:sqlite:" + file.getPath();
                username = null;
                password = null;
            } else if ("mysql".equalsIgnoreCase(type)) {
                url = String.format("jdbc:mysql://%s/%s", config.getString("mysql.host"), config.getString("mysql.name"));
                username = config.getString("mysql.username");
                password = config.getString("mysql.password");
            } else {
                throw new IllegalArgumentException("Invalid value: database.type(config.yml)");
            }
            init = config.getString(type + ".init", "/* AutoPermission */SELECT 1");

            String tmp = type + ".properties";
            if (config.contains(tmp)) {
                for (String key : config.getConfigurationSection(tmp).getKeys(false)) {
                    properties.put(key, config.getString(tmp + "." + key));
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
