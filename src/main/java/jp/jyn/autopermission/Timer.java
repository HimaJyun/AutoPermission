package jp.jyn.autopermission;

import jp.jyn.autopermission.config.MainConfig;
import jp.jyn.autopermission.config.MessageConfig;
import jp.jyn.jbukkitlib.config.parser.template.variable.StringVariable;
import jp.jyn.jbukkitlib.config.parser.template.variable.TemplateVariable;
import jp.jyn.jbukkitlib.util.MapBuilder;
import jp.jyn.jbukkitlib.util.PackagePrivate;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class Timer implements Runnable, Listener {
    private final static TimeUnit UNIT = TimeUnit.MILLISECONDS;

    private final Plugin plugin;
    private final Logger logger;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ThreadLocal<TemplateVariable> variable = ThreadLocal.withInitial(StringVariable::init);
    private final Map<Player, Long> timer = new HashMap<>();
    private final Map<Player, Float> afk = new HashMap<>();

    private final MessageConfig message;
    private final TimeRepository repository;
    private final Permission permission;
    private final NavigableMap<Long, String> groups;
    private final Map<String, LinkedItem<String>> priority;
    private final BukkitTask task;

    @PackagePrivate
    Timer(MainConfig config, MessageConfig message, TimeRepository repository) {
        this.plugin = AutoPermission.getInstance();
        this.logger = plugin.getLogger();

        this.message = message;
        this.repository = repository;
        this.groups = config.permission;

        this.priority = MapBuilder.initUnmodifiableMap(new HashMap<>(), m -> {
            LinkedItem<String> prev = null;
            for (String s : config.priority) {
                prev = new LinkedItem<>(s, prev);
                m.put(s, prev);
            }
        });

        this.permission = Optional.ofNullable(Bukkit.getServer().getServicesManager().getRegistration(Permission.class))
            .map(RegisteredServiceProvider::getProvider)
            .orElseThrow(() -> new RuntimeException("Failed to get Permission service provider."));

        executor.submit(() -> Thread.currentThread().setName("autopermission-timer"));

        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this, 0, config.checkInterval * 20);
    }

    @PackagePrivate
    void stop() {
        task.cancel();
        HandlerList.unregisterAll(this);
        this.run();
        executor.shutdown();
    }

    private long time() {
        return System.currentTimeMillis();
    }

    private float yaw(Player player) {
        return player.getLocation().getYaw();
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        timer.put(player, time());
        afk.put(player, yaw(player));

        executor.submit(() -> {
            // ラストログイン時間を設定
            repository.setLastLogin(player, Instant.now());
            // プレイ時間の取得
            long played = repository.getPlayedTime(player, UNIT);
            // メッセージ出力
            Bukkit.broadcastMessage((played == -1 ? message.joinNew : message.joinBroadcast)
                .toString(variable.get().clear()
                    .put("player", player.getName())
                    .put("time", repository.format(played, UNIT))
                )
            );

            // パーミッションチェックは同期処理
            Bukkit.getScheduler().runTask(plugin, () -> checkPermission(player, played));
        });
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        Long old = timer.remove(player);
        Float yaw = afk.remove(player);
        if (old == null || yaw == null) {
            return;
        }

        long time = time() - old;
        executor.submit(yaw == yaw(player) ?
            () -> repository.addAfkTime(player, time, UNIT) :
            () -> repository.addPlayedTime(player, time, UNIT)
        );
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            long unix = time();
            float yaw = yaw(player);

            Long oldTime = timer.put(player, unix);
            Float oldYaw = afk.put(player, yaw);
            if (oldTime == null || oldYaw == null) {
                continue;
            }

            long time = unix - oldTime;
            executor.submit(yaw == oldYaw ?
                () -> repository.addAfkTime(player, time, UNIT) :
                () -> {
                    repository.addPlayedTime(player, time, UNIT);
                    Bukkit.getScheduler().runTask(
                        plugin,
                        () -> checkPermission(player, repository.getPlayedTime(player, UNIT))
                    );
                }
            );
        }
    }

    private void checkPermission(Player player, long played) {
        Map.Entry<Long, String> passed = groups.floorEntry(played);
        if (passed == null) {
            return;
        }

        // 既に加入していたら何もしない
        if (permission.playerInGroup(null, player, passed.getValue())) {
            return;
        }

        // ランクが定義されてなければエラー、ちゃんと設定してね！
        LinkedItem<String> item = priority.get(passed.getValue());
        if (item == null) {
            logger.severe(passed.getValue() + " is not set to the priority of the group. (config.yml)");
            return;
        }

        String[] group = permission.getPlayerGroups(null, player);
        // 到達済みグループより高位のグループがあれば何もしない
        for (String s : group) {
            LinkedItem<String> g = priority.get(s);
            // 設定されてないグループは無視する
            if (g == null) {
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

        // メッセージ出力
        Bukkit.broadcastMessage(message.promote.toString(variable.get().clear()
            .put("player", player.getName())
            .put("group", passed.getValue())
            .put("time", repository.format(passed.getKey(), UNIT))
        ));
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
