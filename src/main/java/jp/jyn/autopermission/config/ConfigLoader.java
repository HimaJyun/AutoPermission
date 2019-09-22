package jp.jyn.autopermission.config;

import jp.jyn.autopermission.AutoPermission;
import jp.jyn.jbukkitlib.config.YamlLoader;

public class ConfigLoader {
    private final YamlLoader mainLoader;
    private MainConfig mainConfig;

    public ConfigLoader() {
        this.mainLoader = new YamlLoader(AutoPermission.getInstance(), "config.yml");
    }

    public void reloadConfig() {
        mainLoader.saveDefaultConfig();
        if (mainConfig != null) {
            mainLoader.reloadConfig();
        }

        mainConfig = new MainConfig(mainLoader.getConfig());
    }

    public MainConfig getMainConfig() {
        return mainConfig;
    }
}
