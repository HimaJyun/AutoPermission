package jp.jyn.autopermission.config;

import jp.jyn.autopermission.AutoPermission;
import jp.jyn.jbukkitlib.config.YamlLoader;

public class ConfigLoader {
    private final YamlLoader mainLoader;
    private MainConfig mainConfig;

    private final YamlLoader messageLoader;
    private MessageConfig messageConfig;

    public ConfigLoader() {
        this.mainLoader = new YamlLoader(AutoPermission.getInstance(), "config.yml");
        this.messageLoader = new YamlLoader(AutoPermission.getInstance(), "message.yml");
    }

    public void reloadConfig() {
        mainLoader.saveDefaultConfig();
        messageLoader.saveDefaultConfig();
        if (mainConfig != null || messageConfig != null) {
            mainLoader.reloadConfig();
            messageLoader.reloadConfig();
        }

        mainConfig = new MainConfig(mainLoader.getConfig());
        messageConfig = new MessageConfig(messageLoader.getConfig());
    }

    public MainConfig getMainConfig() {
        return mainConfig;
    }

    public MessageConfig getMessageConfig() {
        return messageConfig;
    }
}
