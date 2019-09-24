package jp.jyn.autopermission;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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

    public Instant getLastLogin(UUID uuid) {
        long unix = database.getLastLogin(getId(uuid));
        if (unix == -1) {
            return null;
        }
        return Instant.ofEpochMilli(unix);
    }

    public void setLastLogin(UUID uuid, Instant unixtime) {
        database.updateLogin(getId(uuid), unixtime.toEpochMilli());
    }

    /*public long getTotalTime(Player player) {

    }

    public long getTotalTime(Player player, TimeUnit unit) {
        return unit.convert(getTotalTime(player), TimeUnit.MILLISECONDS);
    }*/

    public long getAfkTime(UUID uuid) {
        long time = database.getAfkTime(getId(uuid));
        if (time == -1) {
            return 0;
        }
        return time;
    }

    public long getAfkTime(UUID uuid, TimeUnit unit) {
        return unit.convert(getAfkTime(uuid), TimeUnit.MILLISECONDS);
    }

    public void addAfkTime(UUID uuid, long time) {
        database.addAfkTime(getId(uuid), time);
    }

    public long getPlayedTime(UUID uuid) {
        long time = database.getPlayedTime(getId(uuid));
        if (time == -1) {
            return 0;
        }
        return time;
    }

    public long getPlayedTime(UUID uuid, TimeUnit unit) {
        return unit.convert(getPlayedTime(uuid), TimeUnit.MILLISECONDS);
    }

    public void addPlayedTime(UUID uuid, long time) {
        database.addPlayedTime(getId(uuid), time);
    }

}
