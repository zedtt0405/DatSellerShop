package com.DatShop;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Fail-closed check that runs before the application context refreshes.
 * JDBC is opened only after the URL and ddl-auto checks accept the target.
 */
public final class RegressionDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String REGRESSION_DATABASE = "smart_fashion_shop_regression_test";
    static final String CONCURRENCY_DATABASE = "smart_fashion_shop_concurrency_test";
    static final String PROTECTED_DATABASE = "smart_fashion_shop";
    static final String ACCEPTED_MESSAGE = "RegressionDatabaseGuard accepted database=";

    private static final Set<String> ALLOWED = Set.of(REGRESSION_DATABASE, CONCURRENCY_DATABASE);
    private static final Pattern JDBC_MYSQL = Pattern.compile("^(jdbc:mysql://[^/]+/)([^/?]+)(\\?.*)?$");
    private static final Logger log = LoggerFactory.getLogger(RegressionDatabaseGuard.class);

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        String url = environment.getProperty("spring.datasource.url");
        String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto");
        String database = assertUrlSafe(url, ddlAuto);
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password", "");
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Datasource username is missing");
        }
        assertLiveDatabase(url, username, password, database);
        log.info("{}{}", ACCEPTED_MESSAGE, database);
    }

    static String assertUrlSafe(String url, String ddlAuto) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Refusing empty JDBC URL before opening a connection");
        }
        if (containsCreateDatabaseIfNotExist(url)) {
            throw new IllegalStateException("Refusing JDBC URL that can create a database before opening a connection");
        }
        if (ddlAuto == null || !"validate".equals(ddlAuto.trim())) {
            throw new IllegalStateException("Refusing ddl-auto other than validate before opening a connection");
        }
        String database = databaseName(url);
        if (PROTECTED_DATABASE.equals(database)) {
            throw new IllegalStateException(
                    "Refusing protected database smart_fashion_shop before opening a connection");
        }
        if (!ALLOWED.contains(database)) {
            throw new IllegalStateException(
                    "Refusing database outside the regression whitelist before opening a connection");
        }
        return database;
    }

    private static void assertLiveDatabase(String url, String username, String password, String expectedDatabase) {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT DATABASE()")) {
            if (!resultSet.next()) {
                throw new IllegalStateException("SELECT DATABASE() returned no row");
            }
            String actual = resultSet.getString(1);
            if (!expectedDatabase.equals(actual)) {
                throw new IllegalStateException("SELECT DATABASE() did not match the approved test database");
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Approved test database could not be verified", ex);
        }
    }

    private static String databaseName(String jdbcUrl) {
        Matcher matcher = JDBC_MYSQL.matcher(jdbcUrl.trim());
        if (!matcher.matches() || matcher.group(2).isBlank()) {
            throw new IllegalStateException("Refusing unrecognized JDBC URL before opening a connection");
        }
        return matcher.group(2);
    }

    private static boolean containsCreateDatabaseIfNotExist(String jdbcUrl) {
        return jdbcUrl.toLowerCase(Locale.ROOT).contains("createdatabaseifnotexist");
    }
}
