package com.mdwgames.firefly.data.storage;

import com.zaxxer.hikari.HikariConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Builds the configured {@link Storage} backend. Config parsing and URL/credential assembly happen
 * here (on the main thread, no connections); the actual connect + schema run later in
 * {@link Storage#init()} on the storage worker thread. The URL/secret helpers are pure and static so
 * the security-sensitive logic (H2 file sanitization, MySQL flags, env-var secrets) is unit-tested.
 */
public final class StorageFactory {

    private StorageFactory() {
    }

    /** Reads {@code storage.*} and returns the backend; SQL backends are wrapped for YAML fallback. */
    public static @NotNull Storage create(@NotNull final Plugin plugin, @NotNull final FileConfiguration config) {
        final Logger log = plugin.getLogger();
        final File dataFolder = plugin.getDataFolder();
        final Supplier<Storage> yaml = () -> new YamlStorage(new File(dataFolder, "playerdata.yml"), log);
        final String type = config.getString("storage.type", "yaml").trim().toLowerCase(Locale.ROOT);
        try {
            switch (type) {
                case "yaml":
                    return yaml.get();
                case "h2": {
                    final String file = sanitizeH2File(config.getString("storage.h2.file", "players"));
                    final HikariConfig hc = new HikariConfig();
                    hc.setPoolName("Firefly-H2");
                    hc.setJdbcUrl(h2Url(dataFolder, file));
                    // All storage access is serialized on one worker thread, so a tiny fixed pool
                    // is all that's ever needed.
                    hc.setMaximumPoolSize(2);
                    hc.setMinimumIdle(2);
                    return new FallbackStorage(new SqlStorage(hc), yaml, log);
                }
                case "mysql":
                    return new FallbackStorage(new SqlStorage(mysqlConfig(config)), yaml, log);
                default:
                    log.warning("Unknown storage.type '" + type + "' — using yaml.");
                    return yaml.get();
            }
        } catch (final RuntimeException e) {
            log.severe("Invalid storage configuration (" + e.getMessage() + ") — using yaml.");
            return yaml.get();
        }
    }

    private static HikariConfig mysqlConfig(final FileConfiguration config) {
        final String host = config.getString("storage.mysql.host", "localhost");
        final int port = config.getInt("storage.mysql.port", 3306);
        final String database = config.getString("storage.mysql.database", "firefly");
        final String ssl = sslMode(config.getString("storage.mysql.ssl", "preferred"));

        final HikariConfig hc = new HikariConfig();
        hc.setPoolName("Firefly-MySQL");
        hc.setJdbcUrl(mysqlUrl(host, port, database, ssl));
        hc.setUsername(config.getString("storage.mysql.username", "firefly"));
        hc.setPassword(resolveSecret(config.getString("storage.mysql.password", ""), System::getenv));

        // Firefly serializes all DB access on a single worker thread, so the pool only ever needs
        // one connection in use; a small fixed pool (2) is the optimal default.
        final int maxPool = Math.max(1, config.getInt("storage.mysql.pool.maximum-pool-size", 2));
        hc.setMaximumPoolSize(maxPool);
        hc.setMinimumIdle(Math.min(maxPool, config.getInt("storage.mysql.pool.minimum-idle", maxPool)));
        hc.setConnectionTimeout(config.getLong("storage.mysql.pool.connection-timeout-ms", 10_000L));
        hc.setMaxLifetime(config.getLong("storage.mysql.pool.max-lifetime-ms", 1_800_000L));
        hc.setKeepaliveTime(config.getLong("storage.mysql.pool.keepalive-ms", 0L));
        return hc;
    }

    // ========== Pure, unit-tested helpers ==========

    /**
     * Validates the configured H2 file name to a simple, safe token. Rejecting separators and
     * {@code ..} prevents an H2 JDBC-URL parameter-injection RCE (e.g. {@code ;INIT=RUNSCRIPT FROM}).
     */
    public static @NotNull String sanitizeH2File(final String file) {
        if (file == null || !file.matches("[A-Za-z0-9._-]+") || file.contains("..")) {
            throw new IllegalArgumentException(
                    "storage.h2.file must be a simple name (letters, digits, '.', '_', '-'): " + file);
        }
        return file;
    }

    /** Embedded, file-mode H2 in MySQL-compatibility mode — never a TCP server. */
    public static @NotNull String h2Url(@NotNull final File dataFolder, @NotNull final String file) {
        return "jdbc:h2:file:" + new File(dataFolder, file).getAbsolutePath()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
    }

    /** MySQL URL with the secure + HikariCP-recommended flags baked in (no free-form properties). */
    public static @NotNull String mysqlUrl(@NotNull final String host, final int port,
                                           @NotNull final String database, @NotNull final String sslMode) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?sslMode=" + sslMode
                + "&allowPublicKeyRetrieval=false"
                + "&useUnicode=true&characterEncoding=UTF-8"
                + "&cachePrepStmts=true&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048"
                + "&useServerPrepStmts=true&rewriteBatchedStatements=true";
    }

    /** Maps the friendly {@code ssl} config value to a MySQL Connector/J {@code sslMode}. */
    public static @NotNull String sslMode(final String value) {
        if (value == null) {
            return "PREFERRED";
        }
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "disabled":
                return "DISABLED";
            case "required":
                return "REQUIRED";
            case "verify-ca":
            case "verify_ca":
                return "VERIFY_CA";
            case "verify-identity":
            case "verify_identity":
                return "VERIFY_IDENTITY";
            case "preferred":
            default:
                return "PREFERRED";
        }
    }

    /**
     * Resolves a secret that may be a {@code ${ENV_VAR}} reference, so the password can live in an
     * environment variable instead of the world-readable config. A plain value is returned as-is.
     */
    public static @NotNull String resolveSecret(final String raw, @NotNull final Function<String, String> env) {
        if (raw == null) {
            return "";
        }
        if (raw.length() > 3 && raw.startsWith("${") && raw.endsWith("}")) {
            final String name = raw.substring(2, raw.length() - 1);
            final String value = env.apply(name);
            return value == null ? "" : value;
        }
        return raw;
    }
}
