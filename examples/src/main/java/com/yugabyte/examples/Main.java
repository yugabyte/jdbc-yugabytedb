package com.yugabyte.examples;

import com.yugabyte.ysql.YBClusterAwareDataSource;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Properties;

public class Main {

    private static int num_rows = 250000;
    private static int max_retries = 5;

    public static void main(String[] args) throws SQLException {

        // Initialize - create a sample table.
        createTable();

        // Run the workload.
        try (YBClusterAwareDataSource ds = getDataSource()) {
            doWrites(ds);
            verifyRows(ds);
        }
    }

    private static void createTable() throws SQLException {
        // Can use JDBC URLs and Driver as usual (in a PostgreSQL compatible way).
        String jdbcUrl = "jdbc:postgresql://localhost:5433/yugabyte";

        // User/password are only required if cluster is started with the `ysql_enable_auth` option.
        Properties props = new Properties();
        props.setProperty("user", "yugabyte");
        props.setProperty("password", "yugabyte");

        // Create a simple key-value table.
        try (Connection connection = DriverManager.getConnection(jdbcUrl, props);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS keyvalue");
            statement.execute("CREATE TABLE keyvalue (key varchar PRIMARY KEY, val varchar)");
        }
    }

    private static YBClusterAwareDataSource getDataSource() {
        YBClusterAwareDataSource ds = new YBClusterAwareDataSource();

        // Configure the data source. Note: Most settings below are the default values and are set
        // here for illustration purposes only.
        ds.setInitialHost("localhost");
        ds.setDatabase("yugabyte");
        ds.setUser("yugabyte");
        ds.setPassword("yugabyte");
        ds.setPort(5433);
        ds.setMaxPoolSizePerNode(1); // default 8 - reducing to 1 here.
        ds.setConnectionTimeoutMs(2000); // default 10s - reducing to 2s here.

        // Optionally initialize the datasource.
        // Otherwise it will be automatically initialized the first time `getConnection` is called.
        ds.initialize();

        return ds;
    }

    private static void doWrites(DataSource ds) throws SQLException {
        // Write num_rows rows.
        long start = System.currentTimeMillis();
        for (int i = 1; i <= num_rows; i++) {
            doWrite(ds, i);
            if (i % 10000 == 0) {
                System.out.printf("Wrote %d out of %d rows\n", i, num_rows);
            }
        }
        System.out.printf("Finished writing %d rows in %d millis\n", num_rows, System.currentTimeMillis() - start);
    }

    private static void doWrite(DataSource ds, int i) throws SQLException {
        int retry_count = 0;
        while (retry_count < max_retries) {
            try (Connection connection = ds.getConnection();
                 // Driver will reuse locally cached prepared statement if it already exists.
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO keyvalue VALUES (?, ?)")) {
                statement.setString(1, "k" + i);
                statement.setString(2, "v" + i);
                statement.execute();
            } catch (SQLTransientConnectionException e) {
                System.out.printf("Retrying %d due to %s - %s\n", i, e.getClass().toString(), e.getMessage());
                retry_count += 1;
                continue;
            } catch (SQLException e) {
                System.out.printf("Got error %s, %s. Skipping %d", e.getClass().toString(), e.getMessage(), i);
            }
            return;
        }
    }

    private static void verifyRows(DataSource ds) throws SQLException {
        // Read each row once.
        long start = System.currentTimeMillis();
        for (int i = 1; i <= num_rows; i++) {
            try (Connection connection = ds.getConnection();
                 // Driver will reuse locally cached prepared statement if it already exists.
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT val FROM keyvalue WHERE key = ?")) {
                statement.setString(1, "k" + i);
                try (ResultSet rs = statement.executeQuery()) {
                    // Check that the results is the expected one.
                    if (!rs.next())
                        System.out.println(
                                "ERROR: Invalid result during read phase: Expected 1 row found 0");

                    if (!rs.getString("val").equals("v" + i))
                        System.out.printf(
                                "ERROR: Invalid result during read phase: Expected value '%s' found '%s'\n",
                                "v" + i,
                                rs.getString("val"));

                    if (rs.next())
                        System.out.println(
                                "ERROR: Invalid result during read phase: Expected 1 row found at least 2");
                }
                if (i % 10000 == 0) {
                    System.out.printf("Verified %d out of %d rows\n", i, num_rows);
                }
            }
        }
        System.out.printf("Finished verifying %d rows in %d millis\n", num_rows, System.currentTimeMillis() - start);
    }
}