package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RegressionDatabaseGuardTest {

    @Test
    void protectedDatabaseIsRejectedBeforeAnyConnection() {
        String url = "jdbc:mysql://127.0.0.1:1/smart_fashion_shop?connectTimeout=1000&useSSL=false";
        long started = System.nanoTime();

        assertThatThrownBy(() -> RegressionDatabaseGuard.assertUrlSafe(url, "validate"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Refusing protected database smart_fashion_shop before opening a connection");

        assertThat(System.nanoTime() - started).isLessThan(200_000_000L);
    }

    @Test
    void emptyUrlCreateFlagWrongDdlAndOtherDatabasesAreRejectedBeforeConnection() {
        assertThatThrownBy(() -> RegressionDatabaseGuard.assertUrlSafe("  ", "validate"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty JDBC URL");
        assertThatThrownBy(() -> RegressionDatabaseGuard.assertUrlSafe(
                        "jdbc:mysql://127.0.0.1:1/smart_fashion_shop_regression_test?createDatabaseIfNotExist=true",
                        "validate"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("can create a database");
        assertThatThrownBy(() -> RegressionDatabaseGuard.assertUrlSafe(
                        "jdbc:mysql://127.0.0.1:1/smart_fashion_shop_regression_test",
                        "update"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ddl-auto");
        assertThatThrownBy(() -> RegressionDatabaseGuard.assertUrlSafe(
                        "jdbc:mysql://127.0.0.1:1/smart_fashion_shop_d4_test",
                        "validate"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("whitelist");
    }

    @Test
    void regressionAndConcurrencyNamesPassTheUrlCheck() {
        assertThat(RegressionDatabaseGuard.assertUrlSafe(
                        "jdbc:mysql://127.0.0.1:3306/smart_fashion_shop_regression_test?useSSL=false",
                        "validate"))
                .isEqualTo(RegressionDatabaseGuard.REGRESSION_DATABASE);
        assertThat(RegressionDatabaseGuard.assertUrlSafe(
                        "jdbc:mysql://127.0.0.1:3306/smart_fashion_shop_concurrency_test?useSSL=false",
                        " validate "))
                .isEqualTo(RegressionDatabaseGuard.CONCURRENCY_DATABASE);
    }
}
