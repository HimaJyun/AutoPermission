package jp.jyn.autopermission;

import jp.jyn.autopermission.config.ConfigLoader;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoPermission extends JavaPlugin {
    private static AutoPermission instance = null;

    @Override
    public void onEnable() {
        instance = this;

        ConfigLoader config = new ConfigLoader();
        config.reloadConfig();
    }

    public static AutoPermission getInstance() {
        return instance;
    }
}
