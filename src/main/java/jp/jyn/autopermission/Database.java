package jp.jyn.autopermission;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jp.jyn.autopermission.config.MainConfig;
import jp.jyn.jbukkitlib.util.PackagePrivate;
import jp.jyn.jbukkitlib.uuid.UUIDBytes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Logger;

public class Database {
    private final HikariDataSource hikari;

    @PackagePrivate
    Database(MainConfig.DatabaseConfig config) {
        HikariConfig hc = new HikariConfig();

        hc.setJdbcUrl(config.url);
        hc.setPoolName("autopermission-hikari");
        hc.setAutoCommit(true);
        hc.setConnectionInitSql(config.init);
        hc.setDataSourceProperties(config.properties);

        if (config.maximumPoolSize > 0) {
            hc.setMaximumPoolSize(config.maximumPoolSize);
        }
        if (config.minimumIdle > 0) {
            hc.setMinimumIdle(config.minimumIdle);
        }
        if (config.maxLifetime > 0) {
            hc.setMaxLifetime(config.maxLifetime);
        }
        if (config.connectionTimeout > 0) {
            hc.setConnectionTimeout(config.connectionTimeout);
        }
        if (config.idleTimeout > 0) {
            hc.setIdleTimeout(config.idleTimeout);
        }

        Logger logger = AutoPermission.getInstance().getLogger();
        if (config.url.startsWith("jdbc:sqlite:")) {
            logger.info("Use SQLite");
            hikari = new HikariDataSource(hc);

            checkVersion();
            sqliteTable();
        } else if (config.url.startsWith("jdbc:mysql:")) {
            logger.info("Use MySQL");
            hc.setUsername(config.username);
            hc.setPassword(config.password);
            hikari = new HikariDataSource(hc);

            checkVersion();
            mysqlTable();
        } else {
            throw new IllegalArgumentException("Unknown jdbc");
        }
    }

    @PackagePrivate
    void close() {
        if (hikari != null) {
            hikari.close();
        }
    }

    private void checkVersion() {
        try (Connection connection = hikari.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `meta` (`key` TEXT, `value` TEXT)");

            try (ResultSet r = statement.executeQuery("SELECT `value` FROM `meta` WHERE `key`='version'")) {
                if (r.next()) {
                    if (!r.getString(1).equals("1")) {
                        throw new RuntimeException("An incompatible change was made (database can not be downgraded)");
                    }
                } else {
                    statement.executeUpdate("INSERT INTO `meta` VALUES('version','1')");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void sqliteTable() {
        try (Connection connection = hikari.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `id_uuid` (" +
                    "   `id`   INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT," +
                    "   `uuid` BLOB    NOT NULL UNIQUE" +
                    ")"
            );

            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `login` (" +
                    "   `id`    INTEGER NOT NULL PRIMARY KEY," +
                    "   `first` INTEGER NOT NULL," +
                    "   `last`  INTEGER NOT NULL" +
                    ")"
            );

            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `time` (" +
                    "   `id`     INTEGER NOT NULL PRIMARY KEY," +
                    "   `played` INTEGER NOT NULL," +
                    "   `afk`    INTEGER NOT NULL" +
                    ")"
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void mysqlTable() {
        try (Connection connection = hikari.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `id_uuid` (" +
                    "   `id`   INT UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT," +
                    "   `uuid` BINARY(16)   NOT NULL UNIQUE KEY" +
                    ")"
            );

            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `login` (" +
                    "   `id`    INT UNSIGNED NOT NULL PRIMARY KEY," +
                    "   `first` BIGINT       NOT NULL," +
                    "   `last`  BIGINT       NOT NULL" +
                    ")"
            );

            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `time` (" +
                    "   `id`     INT UNSIGNED NOT NULL PRIMARY KEY," +
                    "   `played` BIGINT       NOT NULL," +
                    "   `afk`    BIGINT       NOT NULL" +
                    ")"
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- id_uuid -----------
    // | id (int) | uuid (byte[16]) |
    // ------------------------------
    public int getId(UUID uuid) {
        try (Connection connection = hikari.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement(
                "SELECT `id` FROM `id_uuid` WHERE `uuid`=?"
            )) {
                byte[] bUUID = UUIDBytes.toBytes(uuid);
                // select
                select.setBytes(1, bUUID);
                try (ResultSet r = select.executeQuery()) {
                    if (r.next()) {
                        connection.commit();
                        return r.getInt(1);
                    }
                }

                // insert id
                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO `id_uuid` (`uuid`) VALUES (?)"
                )) {
                    insert.setBytes(1, bUUID);
                    insert.executeUpdate();
                }

                try (ResultSet r = select.executeQuery()) {
                    if (r.next()) {
                        connection.commit();
                        return r.getInt(1);
                    }
                }

                // 自動採番に失敗、何かおかしい
                connection.rollback();
                throw new RuntimeException("ID could not be issued.");
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ----------------- login -----------------
    // | id (int) | first (long) | last (long) |
    // -----------------------------------------
    public long getLastLogin(int id) {
        return selectLongWhereId("SELECT `last` FROM `login` WHERE `id`=?", id);
    }

    public long getFirstLogin(int id) {
        return selectLongWhereId("SELECT `first` FROM `login` WHERE `id`=?", id);
    }

    public void updateLogin(int id, long login) {
        upsert(
            "UPDATE `login` SET `last`=? WHERE `id`=?",
            statement -> {
                statement.setLong(1, login);
                statement.setInt(2, id);
            },
            "INSERT INTO `login` VALUES (?,?,?)",
            statement -> {
                statement.setInt(1, id);
                // レコード非存在 = 初ログインと仮定されるので、初回ログイン=最終ログインとなる
                statement.setLong(2, login);
                statement.setLong(3, login);
            }
        );
    }

    // ------------------ time -----------------
    // | id (int) | played (long) | afk (long) |
    // -----------------------------------------
    public long getTotalTime(int id) {
        return selectLongWhereId("SELECT `played`+`afk` FROM `time` WHERE `id`=?", id);
    }

    public long getPlayedTime(int id) {
        return selectLongWhereId("SELECT `played` FROM `time` WHERE `id`=?", id);
    }

    public long getAfkTime(int id) {
        return selectLongWhereId("SELECT `afk` FROM `time` WHERE `id`=?", id);
    }

    public void addPlayedTime(int id, long played) {
        upsert(
            "UPDATE `time` SET `played`=`played`+? WHERE `id`=?",
            statement -> {
                statement.setLong(1, played);
                statement.setInt(2, id);
            },
            "INSERT INTO `time` VALUES (?,?,?)",
            statement -> {
                statement.setInt(1, id);
                statement.setLong(2, played);
                statement.setLong(3, 0);
            }
        );
    }

    public void addAfkTime(int id, long afk) {
        upsert(
            "UPDATE `time` SET `afk`=`afk`+? WHERE `id`=?",
            statement -> {
                statement.setLong(1, afk);
                statement.setInt(2, id);
            },
            "INSERT INTO `time` VALUES (?,?,?)",
            statement -> {
                statement.setInt(1, id);
                statement.setLong(2, 0);
                statement.setLong(3, afk);
            }
        );
    }

    private long selectLongWhereId(String sql, int id) {
        try (Connection connection = hikari.getConnection();
             PreparedStatement select = connection.prepareStatement(sql)) {
            select.setInt(1, id);
            try (ResultSet r = select.executeQuery()) {
                if (r.next()) {
                    return r.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return -1;
    }

    private void upsert(String update, PreparedParameter updateParameter, String insert, PreparedParameter insertParameter) {
        try (Connection connection = hikari.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(update)) {
                    updateParameter.set(statement);
                    if (statement.executeUpdate() != 0) { // 影響行数アリ -> レコード存在
                        connection.commit();
                        return;
                    }
                }

                // ここまで来た = レコード非存在
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    insertParameter.set(statement);

                    if (statement.executeUpdate() != 0) { // 影響行あり
                        connection.commit();
                        return;
                    }
                }

                // ここまで来た = 何もinsertできてない = おかしい
                connection.rollback();
                throw new RuntimeException("Nothing was updated.");
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private interface PreparedParameter {
        void set(PreparedStatement statement) throws SQLException;
    }
}
