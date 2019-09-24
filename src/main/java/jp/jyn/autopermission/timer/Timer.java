package jp.jyn.autopermission.timer;

import jp.jyn.autopermission.TimeRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Timer implements Runnable, Listener {
    private final Map<Player, Long> timer = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final TimeRepository repository;

    public Timer(TimeRepository repository) {
        this.repository = repository;
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        timer.put(e.getPlayer(), System.currentTimeMillis());
        executor.submit(() -> repository.setLastLogin(
            e.getPlayer().getUniqueId(),
            Instant.ofEpochMilli(System.currentTimeMillis())
        ));
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        Long old = timer.remove(e.getPlayer());
        if (old == null) {
            return;
        }

        long time = System.currentTimeMillis() - old;
        repository.addPlayedTime(e.getPlayer().getUniqueId(), time);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers().toArray(new Player[0])) {
            long unix = System.currentTimeMillis();
            Long old = timer.put(player, unix);
            if (old == null) {
                continue;
            }

            long time = unix - old;
            repository.addPlayedTime(player.getUniqueId(), time);
        }
    }
}
