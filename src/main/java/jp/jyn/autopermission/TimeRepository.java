package jp.jyn.autopermission;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("WeakerAccess")
public class TimeRepository {
    private final Database database;
    private final Map<UUID, Integer> id = new ConcurrentHashMap<>();

    public TimeRepository(Database database) {
        this.database = database;
    }

    private int getId(UUID uuid) {
        return id.computeIfAbsent(uuid, database::getId);
    }

    public Instant getFirstLogin(UUID uuid) {
        long unix = database.getFirstLogin(getId(uuid));
        if (unix == -1) {
            return null;
        }
        return Instant.ofEpochMilli(unix);
    }

    public Instant getFirstLogin(Player player) {
        return this.getFirstLogin(player.getUniqueId());
    }

    public Instant getLastLogin(UUID uuid) {
        long unix = database.getLastLogin(getId(uuid));
        if (unix == -1) {
            return null;
        }
        return Instant.ofEpochMilli(unix);
    }

    public Instant getLastLogin(OfflinePlayer player) {
        return this.getLastLogin(player.getUniqueId());
    }

    public void setLastLogin(UUID uuid, Instant unixtime) {
        database.updateLogin(getId(uuid), unixtime.toEpochMilli());
    }

    public long getTotalTime(UUID uuid, TimeUnit unit) {
        return unit.convert(
            database.getTotalTime(getId(uuid)),
            TimeUnit.MILLISECONDS
        );
    }

    public long getTotalTime(OfflinePlayer player, TimeUnit unit) {
        return this.getTotalTime(player.getUniqueId(), unit);
    }

    public long getAfkTime(UUID uuid, TimeUnit unit) {
        return unit.convert(
            database.getAfkTime(getId(uuid)),
            TimeUnit.MILLISECONDS
        );
    }

    public long getAfkTime(OfflinePlayer player, TimeUnit unit) {
        return this.getAfkTime(player.getUniqueId(), unit);
    }

    public void addAfkTime(UUID uuid, long time, TimeUnit unit) {
        database.addAfkTime(getId(uuid), unit.toMillis(time));
    }

    public void addAfkTime(OfflinePlayer player, long time, TimeUnit unit) {
        this.addAfkTime(player.getUniqueId(), time, unit);
    }

    public long getPlayedTime(UUID uuid, TimeUnit unit) {
        return unit.convert(
            database.getPlayedTime(getId(uuid)),
            TimeUnit.MILLISECONDS
        );
    }

    public long getPlayedTime(OfflinePlayer player, TimeUnit unit) {
        return this.getPlayedTime(player.getUniqueId(), unit);
    }

    public void addPlayedTime(UUID uuid, long time, TimeUnit unit) {
        database.addPlayedTime(getId(uuid), unit.toMillis(time));
    }

    public void addPlayedTime(OfflinePlayer player, long time, TimeUnit unit) {
        this.addPlayedTime(player.getUniqueId(), time, unit);
    }
}
