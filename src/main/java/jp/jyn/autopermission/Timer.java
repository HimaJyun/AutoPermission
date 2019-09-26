package jp.jyn.autopermission;

import jp.jyn.autopermission.config.MainConfig;
import jp.jyn.jbukkitlib.util.MapBuilder;
import jp.jyn.jbukkitlib.util.PackagePrivate;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class Timer implements Runnable, Listener {
    private final Map<Player, Long> timer = new HashMap<>();
    private final Map<Player, Float> afk = new HashMap<>();

    private final NavigableMap<Long, String> groups;
    private final Map<String, LinkedItem<String>> priority;
    private final TimeRepository repository;
    private final Permission permission;

    @PackagePrivate
    Timer(MainConfig config, TimeRepository repository) {
        this.groups = config.permission;
        this.repository = repository;

        priority = MapBuilder.initUnmodifiableMap(new HashMap<>(), m -> {
            LinkedItem<String> prev = null;
            for (String s : config.priority) {
                prev = new LinkedItem<>(s, prev);
                m.put(s, prev);
            }
        });

        RegisteredServiceProvider<Permission> rsp = Bukkit.getServer().getServicesManager().getRegistration(Permission.class);
        permission = Objects.requireNonNull(rsp).getProvider();
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

        checkPermission(player);
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
            repository.addAfkTime(e.getPlayer(), time, TimeUnit.MILLISECONDS);
        } else {
            repository.addPlayedTime(e.getPlayer(), time, TimeUnit.MILLISECONDS);
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
                repository.addAfkTime(player, time, TimeUnit.MILLISECONDS);
            } else {
                repository.addPlayedTime(player, time, TimeUnit.MILLISECONDS);
            }
            checkPermission(player);
        }
    }

    private void checkPermission(Player player) {
        long played = repository.getPlayedTime(player, TimeUnit.MILLISECONDS);
        Map.Entry<Long, String> passed = groups.floorEntry(played);
        if (passed == null) {
            return;
        }

        // 既に加入していたら何もしない
        if (permission.playerInGroup(null, player, passed.getValue())) {
            return;
        }

        // ランクが定義されてなければエラー、ちゃんと設定してね！
        Logger logger = AutoPermission.getInstance().getLogger();
        LinkedItem<String> item = priority.get(passed.getValue());
        if (item == null) {
            logger.severe(passed.getValue() + " is not set to the priority of the group. (config.yml)");
            return;
        }

        String[] group = permission.getPlayerGroups(null, player);
        // 到達済みグループより高位のグループがあれば何もしない
        for (String s : group) {
            LinkedItem<String> g = priority.get(s);
            // 判定不能
            if (g == null) {
                logger.severe(s + " is not set to the priority of the group. (config.yml)");
                continue;
            }

            if (item.link < g.link) {
                return;
            }
        }

        // 新しいランクに加入して古いランクをすべて離脱
        permission.playerAddGroup(null, player, item.value);
        LinkedItem<String> prev = item.prev;
        while (prev != null) {
            permission.playerRemoveGroup(null, player, prev.value);
            prev = prev.prev;
        }
    }

    // 連結数カウント付き片方向連結リスト
    private final static class LinkedItem<V> {
        private final V value;
        private final LinkedItem<V> prev;
        private final int link;

        private LinkedItem(V value, LinkedItem<V> prev) {
            this.value = value;
            this.prev = prev;

            // カウントするだけなので前のカウント+1すればいい
            this.link = prev == null ? 0 : prev.link + 1;
        }
    }
}
