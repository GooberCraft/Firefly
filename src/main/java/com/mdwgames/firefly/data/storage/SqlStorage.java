package com.mdwgames.firefly.data.storage;

import com.mdwgames.firefly.data.PlayerPreferences;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HikariCP-pooled SQL backend shared by H2 and MySQL. H2 runs in MySQL compatibility mode
 * ({@code ;MODE=MySQL}) so a single SQL dialect drives both. All access is via
 * {@link PreparedStatement} with bound parameters — no SQL is built by string concatenation.
 */
public final class SqlStorage implements Storage {

    /** The DDL lives in src/main/resources/schema.sql so it's a single source of truth and users
     *  can run it to pre-create the table (least-privilege setups). */
    private static final String SCHEMA_RESOURCE = "/schema.sql";
    private static final String SELECT_ALL = "SELECT uuid, hidden, color, bypass FROM firefly_players";
    private static final String UPSERT =
            "INSERT INTO firefly_players (uuid, hidden, color, bypass) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE hidden = VALUES(hidden), color = VALUES(color), "
                    + "bypass = VALUES(bypass)";
    private static final String DELETE = "DELETE FROM firefly_players WHERE uuid = ?";

    private final HikariConfig config;
    private HikariDataSource dataSource;

    public SqlStorage(@NotNull final HikariConfig config) {
        this.config = config;
    }

    @Override
    public void init() throws SQLException, IOException {
        // Pool creation connects, so it runs here (on the storage worker), never at construction.
        this.dataSource = new HikariDataSource(config);
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute(loadSchema());
        }
    }

    /** Reads the bundled schema.sql (single CREATE TABLE statement) from the classpath. */
    private static String loadSchema() throws IOException {
        try (InputStream in = SqlStorage.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IOException("Bundled " + SCHEMA_RESOURCE + " not found on the classpath");
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (sql.endsWith(";")) {
                sql = sql.substring(0, sql.length() - 1); // single statement — drop the trailing ';'
            }
            return sql;
        }
    }

    @Override
    public @NotNull Map<UUID, PlayerPreferences> loadAll() throws SQLException {
        final Map<UUID, PlayerPreferences> out = new HashMap<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                final UUID uuid = parseUuid(rs.getString("uuid"));
                if (uuid == null) {
                    continue;
                }
                final boolean hidden = rs.getBoolean("hidden");
                final int colorVal = rs.getInt("color");
                final Integer color = rs.wasNull() ? null : (colorVal & 0xFFFFFF);
                final boolean bypassVal = rs.getBoolean("bypass");
                final Boolean bypass = rs.wasNull() ? null : bypassVal;
                final PlayerPreferences prefs = new PlayerPreferences(hidden, color, bypass);
                if (!prefs.isDefault()) {
                    out.put(uuid, prefs);
                }
            }
        }
        return out;
    }

    @Override
    public void save(@NotNull final Map<UUID, PlayerPreferences> changed) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement upsert = c.prepareStatement(UPSERT);
                 PreparedStatement delete = c.prepareStatement(DELETE)) {
                for (final Map.Entry<UUID, PlayerPreferences> e : changed.entrySet()) {
                    final String id = e.getKey().toString();
                    final PlayerPreferences p = e.getValue();
                    if (p.isDefault()) {
                        delete.setString(1, id);
                        delete.addBatch();
                    } else {
                        upsert.setString(1, id);
                        upsert.setBoolean(2, p.hidden());
                        upsert.setObject(3, p.colorRgb() == null ? null : (p.colorRgb() & 0xFFFFFF), Types.INTEGER);
                        upsert.setObject(4, p.bypass(), Types.BOOLEAN);
                        upsert.addBatch();
                    }
                }
                upsert.executeBatch();
                delete.executeBatch();
                c.commit();
            } catch (final SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private static UUID parseUuid(final String raw) {
        try {
            return UUID.fromString(raw);
        } catch (final IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
