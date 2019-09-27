package jp.jyn.autopermission;

import jp.jyn.autopermission.config.ConfigLoader;
import jp.jyn.autopermission.config.MainConfig;
import jp.jyn.autopermission.config.MessageConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;

public class AutoPermission extends JavaPlugin {
    private static AutoPermission instance = null;

    private TimeRepository repository;

    // LIFO
    private final Deque<Runnable> destructor = new ArrayDeque<>();

    @Override
    public void onEnable() {
        instance = this;

        ConfigLoader config = new ConfigLoader();
        config.reloadConfig();
        MainConfig mainConfig = config.getMainConfig();
        MessageConfig messageConfig = config.getMessageConfig();

        // Database
        Database database = new Database(mainConfig.database);
        destructor.addFirst(database::close);

        repository = new TimeRepository(messageConfig, database);

        Timer timer = new Timer(mainConfig, messageConfig, repository);
        destructor.addFirst(timer::stop);
    }

    @Override
    public void onDisable() {
        while (!destructor.isEmpty()) {
            destructor.removeFirst().run();
        }
    }

    public TimeRepository getRepository() {
        return repository;
    }

    public static AutoPermission getInstance() {
        return instance;
    }
}
