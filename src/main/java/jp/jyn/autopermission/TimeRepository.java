package jp.jyn.autopermission;

import jp.jyn.autopermission.config.MessageConfig;
import jp.jyn.jbukkitlib.config.parser.template.variable.StringVariable;
import jp.jyn.jbukkitlib.config.parser.template.variable.TemplateVariable;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("WeakerAccess")
public class TimeRepository {
    private final MessageConfig message;
    private final Database database;
    private final Map<UUID, Integer> id = new ConcurrentHashMap<>();

    public TimeRepository(MessageConfig message, Database database) {
        this.message = message;
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

    public void setLastLogin(UUID uuid, Instant time) {
        database.updateLogin(getId(uuid), time.toEpochMilli());
    }

    public void setLastLogin(OfflinePlayer player, Instant time) {
        this.setLastLogin(player.getUniqueId(), time);
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

    public String format(long time, TimeUnit unit) {
        return this.format(time, unit, StringVariable.init());
    }

    public String format(long time, TimeUnit unit, TemplateVariable variable) {
        StringBuilder builder = new StringBuilder();
        long m = unit.toMinutes(time);
        long d = m / (24 * 60); // 87917 / (24 * 60) = 61
        m -= d * (24 * 60); // 87917 - (61*24*60) = 77
        long h = m / 60; // 77 / 60 = 1
        m -= h * 60; // 77 - (1*60) = 17
        // 61 day 1 hour 17 minute

        if (d != 0) {
            builder.append(message.formatDay.toString(variable.put("value", d)));
        }
        if (h != 0) {
            if (builder.length() != 0) {
                builder.append(message.formatSeparator);
            }
            builder.append(message.formatHour.toString(variable.put("value", h)));
        }
        if (builder.length() != 0) {
            builder.append(message.formatSeparator);
        }
        builder.append(message.formatMinute.toString(variable.put("value", m)));
        return builder.toString();
    }
}
