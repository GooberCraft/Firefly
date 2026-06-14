package com.mdwgames.firefly.data.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("StorageFactory (pure helpers)")
class StorageFactoryTest {

    @Test
    @DisplayName("H2 file name is sanitized to a safe token")
    void sanitizeH2File() {
        assertEquals("players", StorageFactory.sanitizeH2File("players"));
        assertEquals("my_db-1.x", StorageFactory.sanitizeH2File("my_db-1.x"));
        // Injection / traversal vectors are rejected.
        assertThrows(IllegalArgumentException.class, () -> StorageFactory.sanitizeH2File("a;INIT=RUNSCRIPT"));
        assertThrows(IllegalArgumentException.class, () -> StorageFactory.sanitizeH2File("../evil"));
        assertThrows(IllegalArgumentException.class, () -> StorageFactory.sanitizeH2File("a/b"));
        assertThrows(IllegalArgumentException.class, () -> StorageFactory.sanitizeH2File(".."));
        assertThrows(IllegalArgumentException.class, () -> StorageFactory.sanitizeH2File(""));
        assertThrows(IllegalArgumentException.class, () -> StorageFactory.sanitizeH2File(null));
    }

    @Test
    @DisplayName("H2 URL is embedded file mode in MySQL compatibility mode")
    void h2Url() {
        final String url = StorageFactory.h2Url(new File("/tmp/firefly"), "players");
        assertTrue(url.startsWith("jdbc:h2:file:"), url);
        assertTrue(url.contains("MODE=MySQL"), url);
        assertFalse(url.contains("AUTO_SERVER"));
    }

    @Test
    @DisplayName("MySQL URL carries the secure + recommended flags")
    void mysqlUrl() {
        final String url = StorageFactory.mysqlUrl("db.example", 3306, "firefly", "REQUIRED");
        assertTrue(url.startsWith("jdbc:mysql://db.example:3306/firefly?"), url);
        assertTrue(url.contains("sslMode=REQUIRED"), url);
        assertTrue(url.contains("allowPublicKeyRetrieval=false"), url);
        assertTrue(url.contains("characterEncoding=UTF-8"), url);
        assertTrue(url.contains("cachePrepStmts=true"), url);
        assertTrue(url.contains("useServerPrepStmts=true"), url);
        assertTrue(url.contains("rewriteBatchedStatements=true"), url);
    }

    @Test
    @DisplayName("ssl config maps to MySQL sslMode (default PREFERRED)")
    void sslMode() {
        assertEquals("DISABLED", StorageFactory.sslMode("disabled"));
        assertEquals("PREFERRED", StorageFactory.sslMode("preferred"));
        assertEquals("REQUIRED", StorageFactory.sslMode("required"));
        assertEquals("VERIFY_CA", StorageFactory.sslMode("verify-ca"));
        assertEquals("VERIFY_IDENTITY", StorageFactory.sslMode("verify-identity"));
        assertEquals("PREFERRED", StorageFactory.sslMode("nonsense"));
        assertEquals("PREFERRED", StorageFactory.sslMode(null));
    }

    @Test
    @DisplayName("secrets resolve from ${ENV_VAR} or pass through literally")
    void resolveSecret() {
        final Map<String, String> env = Map.of("DB_PW", "s3cret");
        assertEquals("s3cret", StorageFactory.resolveSecret("${DB_PW}", env::get));
        assertEquals("literal", StorageFactory.resolveSecret("literal", env::get));
        assertEquals("", StorageFactory.resolveSecret("${MISSING}", env::get));
        assertEquals("", StorageFactory.resolveSecret(null, env::get));
    }
}
