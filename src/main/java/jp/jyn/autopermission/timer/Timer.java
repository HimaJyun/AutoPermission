package jp.jyn.autopermission.timer;

import jp.jyn.autopermission.TimeRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Timer implements Runnable, Listener {
    private final Map<Player, Long> timer = new HashMap<>();
    private final Map<Player, Float> afk = new HashMap<>();

    private final TimeRepository repository;

    public Timer(TimeRepository repository) {
        this.repository = repository;
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        timer.put(player, System.currentTimeMillis());
        afk.put(player, player.getLocation().getYaw());

        repository.setLastLogin(
            e.getPlayer().getUniqueId(),
            Instant.ofEpochMilli(System.currentTimeMillis())
        );
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        Long old = timer.remove(e.getPlayer());
        Float yaw = afk.remove(e.getPlayer());
        if (old == null || yaw == null) {
            return;
        }

        long time = System.currentTimeMillis() - old;

        if (yaw == e.getPlayer().getLocation().getYaw()) {
            repository.addAfkTime(e.getPlayer().getUniqueId(), time);
        } else {
            repository.addPlayedTime(e.getPlayer().getUniqueId(), time);
        }
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers().toArray(new Player[0])) {
            long unix = System.currentTimeMillis();
            float yaw = player.getLocation().getYaw();

            Long old = timer.put(player, unix);
            Float oldYaw = afk.put(player, yaw);
            if (old == null || oldYaw == null) {
                continue;
            }

            long time = unix - old;
            if (yaw == oldYaw) {
                repository.addAfkTime(player.getUniqueId(), time);
            } else {
                repository.addPlayedTime(player.getUniqueId(), time);
            }
        }
    }
}
