package com.sewlect.common.persistence;

import com.sewlect.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "PGVECTOR_EF_SEARCH=100",
        "PGVECTOR_ITERATIVE_SCAN=relaxed_order"
})
class PgvectorSessionSettingsTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void pooledConnection_hasHnswEfSearchSetByConnectionInitSql() throws SQLException {
        assertThat(show("hnsw.ef_search")).isEqualTo("100");
    }

    @Test
    void pooledConnection_hasHnswIterativeScanSetByConnectionInitSql() throws SQLException {
        assertThat(show("hnsw.iterative_scan")).isEqualTo("relaxed_order");
    }

    private String show(String setting) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SHOW " + setting)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
