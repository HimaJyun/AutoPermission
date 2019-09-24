package jp.jyn.autopermission;

import jp.jyn.autopermission.config.ConfigLoader;
import jp.jyn.autopermission.config.MainConfig;
import jp.jyn.autopermission.timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

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

        // Database
        Database database = new Database(mainConfig.database);
        destructor.addFirst(database::close);

        repository = new TimeRepository(database);

        Timer timer = new Timer(repository);
        Bukkit.getPluginManager().registerEvents(timer, this);
        destructor.addFirst(() -> HandlerList.unregisterAll(this));
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, timer, 0, mainConfig.checkInterval * 20);
        destructor.addFirst(task::cancel);

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
