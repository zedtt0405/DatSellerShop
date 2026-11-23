package com.DatShop;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Prepares {@code smart_fashion_shop_concurrency_test} before the Spring Test DataSource exists.
 * Never touches {@code smart_fashion_shop}. Never uses {@code createDatabaseIfNotExist=true}.
 */
final class ConcurrencyTestDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String TEST_DATABASE = "smart_fashion_shop_concurrency_test";
    static final String PROTECTED_DATABASE = "smart_fashion_shop";

    private static final Pattern JDBC_MYSQL = Pattern.compile("^(jdbc:mysql://[^/]+/)([^/?]+)(\\?.*)?$");
    private static final String SAFE_FAILURE =
            "Concurrency test database is not safe to use. Check local MySQL credentials; secrets are not logged.";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        String sourceUrl = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password", "");
        if (sourceUrl == null || sourceUrl.isBlank() || username == null || username.isBlank()) {
            throw new IllegalStateException(SAFE_FAILURE);
        }

        if (PROTECTED_DATABASE.equals(TEST_DATABASE)) {
            throw new IllegalStateException("Refusing to initialize a non-test database");
        }

        JdbcMysqlUrl parsed = parseMysqlUrl(sourceUrl);
        String testUrl = parsed.serverPrefix() + TEST_DATABASE + parsed.queryWithoutCreateDatabase();
        if (PROTECTED_DATABASE.equals(databaseName(testUrl))
                || !TEST_DATABASE.equals(databaseName(testUrl))
                || containsCreateDatabaseIfNotExist(testUrl)) {
            throw new IllegalStateException("Concurrency test JDBC URL is not safe");
        }

        prepareIsolatedDatabase(parsed.serverPrefix() + parsed.queryWithoutCreateDatabase(), username, password, testUrl);

        environment.getPropertySources().addFirst(new MapPropertySource("concurrency-test-db", Map.of(
                "spring.datasource.url", testUrl,
                "spring.jpa.hibernate.ddl-auto", "validate")));
    }

    private static void prepareIsolatedDatabase(String serverUrl, String username, String password, String testUrl) {
        Path schema = schemaFile("01_schema.sql");
        Path seed = schemaFile("02_seed_roles.sql");
        assertNoProtectedUse(schema);
        assertNoProtectedUse(seed);

        try (Connection admin = DriverManager.getConnection(serverUrl, username, password);
                Statement statement = admin.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + TEST_DATABASE + "`");
            statement.execute(
                    "CREATE DATABASE `" + TEST_DATABASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (Exception ex) {
            throw new IllegalStateException(SAFE_FAILURE);
        }

        try (Connection connection = DriverManager.getConnection(testUrl, username, password)) {
            String actual;
            try (Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery("SELECT DATABASE()")) {
                if (!rs.next()) {
                    throw new IllegalStateException(SAFE_FAILURE);
                }
                actual = rs.getString(1);
            }
            if (!TEST_DATABASE.equals(actual)) {
                throw new IllegalStateException("SELECT DATABASE() did not return the concurrency test database");
            }
            ScriptUtils.executeSqlScript(connection, encoded(schema));
            ScriptUtils.executeSqlScript(connection, encoded(seed));
            try (Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM roles")) {
                if (!rs.next() || rs.getInt(1) < 3) {
                    throw new IllegalStateException("Role seed did not create CUSTOMER/STAFF/ADMIN");
                }
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(SAFE_FAILURE);
        }
    }

    private static EncodedResource encoded(Path path) {
        return new EncodedResource(new FileSystemResource(path), StandardCharsets.UTF_8);
    }

    private static Path schemaFile(String name) {
        Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve("database").resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Cannot locate database/" + name + " for concurrency tests");
    }

    private static void assertNoProtectedUse(Path sqlFile) {
        String sql;
        try {
            sql = Files.readString(sqlFile, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read " + sqlFile.getFileName());
        }
        String compact = sql.replace('\n', ' ').replace('\r', ' ').toLowerCase(Locale.ROOT);
        if (compact.matches(".*\\buse\\s+smart_fashion_shop\\b.*")) {
            throw new IllegalStateException(sqlFile.getFileName() + " must not switch to " + PROTECTED_DATABASE);
        }
    }

    static String databaseName(String jdbcUrl) {
        return parseMysqlUrl(jdbcUrl).database();
    }

    static boolean containsCreateDatabaseIfNotExist(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).contains("createdatabaseifnotexist");
    }

    private static JdbcMysqlUrl parseMysqlUrl(String jdbcUrl) {
        Matcher matcher = JDBC_MYSQL.matcher(jdbcUrl.trim());
        if (!matcher.matches()) {
            throw new IllegalStateException("Unsupported JDBC URL for concurrency tests");
        }
        String database = matcher.group(2);
        String query = matcher.group(3) == null ? "" : matcher.group(3);
        return new JdbcMysqlUrl(matcher.group(1), database, stripCreateDatabase(query));
    }

    private static String stripCreateDatabase(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String stripped = query.replaceAll("(?i)([&?])createDatabaseIfNotExist=true&", "$1")
                .replaceAll("(?i)[&?]createDatabaseIfNotExist=true", "")
                .replace("?&", "?");
        if ("?".equals(stripped)) {
            return "";
        }
        return stripped;
    }

    private record JdbcMysqlUrl(String serverPrefix, String database, String queryWithoutCreateDatabase) {
    }
}
