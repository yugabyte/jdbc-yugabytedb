// Copyright (c) YugaByte, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the License
// is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
// or implied.  See the License for the specific language governing permissions and limitations
// under the License.
//

package com.yugabyte.ysql;

import org.postgresql.PGProperty;
import org.postgresql.ds.PGSimpleDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

public class YBClusterAwareDataSource implements DataSource, AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(YBClusterAwareDataSource.class);

  private final static String DEFAULT_DATA_SOURCE =  "org.postgresql.ds.PGSimpleDataSource";

  private final Object lock = new Object();
  private volatile boolean initialized = false;

  private YBClusterManager clusterManager;

  // Connection config.
  private String initialHost = "localhost";
  private String database = "yugabyte";
  private int port = 5433;
  private String user = "yugabyte";
  private String password = "yugabyte";

  // Pool config.
  private int maxPoolSizePerNode = 8;
  private int connectionTimeoutMs = 10000; // 10 seconds.
  private boolean autoCommit = true;

  public YBClusterAwareDataSource() { }

  public YBClusterAwareDataSource(String jdbcUrl) {
    Properties properties = org.postgresql.Driver.parseURL(jdbcUrl, null /* defaults */);
    if (PGProperty.PG_HOST.isPresent(properties)) {
      initialHost = PGProperty.PG_HOST.get(properties);
    }
    if (PGProperty.PG_PORT.isPresent(properties)) {
      port = Integer.parseInt(PGProperty.PG_PORT.get(properties));
    }
    if (PGProperty.PG_DBNAME.isPresent(properties)) {
      database = PGProperty.PG_DBNAME.get(properties);
    }
    if (PGProperty.USER.isPresent(properties)) {
      user = PGProperty.USER.get(properties);
    }
    if (PGProperty.PASSWORD.isPresent(properties)) {
      password = PGProperty.PASSWORD.get(properties);
    }
    initialize();
  }

  public void initialize() {
    synchronized (lock) {
      if (initialized)
        return;

      Properties poolProperties = new Properties();
      poolProperties.setProperty("dataSourceClassName", DEFAULT_DATA_SOURCE);
      poolProperties.setProperty("maximumPoolSize", String.valueOf(maxPoolSizePerNode));
      poolProperties.setProperty("connectionTimeout", String.valueOf(connectionTimeoutMs));
      poolProperties.setProperty("autoCommit", String.valueOf(autoCommit));
      poolProperties.setProperty("dataSource.portNumber", String.valueOf(port));
      poolProperties.setProperty("dataSource.databaseName", database);
      poolProperties.setProperty("dataSource.user", user);
      poolProperties.setProperty("dataSource.password", password);

      clusterManager = new YBClusterManager(initialHost, poolProperties);

      initialized = true;
    }
  }

  private void checkNotInitialized() {
    if (initialized) {
      throw new IllegalStateException(
        "Cannot set DataSource properties after DataSource has been used");
    }
  }

  public void setInitialHost(String initialHost) {
    checkNotInitialized();
    this.initialHost = initialHost;
  }

  public void setUser(String user) {
    checkNotInitialized();
    this.user = user;
  }

  public void setPort(int port) {
    checkNotInitialized();
    this.port = port;
  }

  public void setDatabase(String database) {
    checkNotInitialized();
    this.database = database;
  }

  public void setPassword(String password) {
    checkNotInitialized();
    this.password = password;
  }

  public void setMaxPoolSizePerNode(int maxPoolSizePerNode) {
    checkNotInitialized();
    this.maxPoolSizePerNode = maxPoolSizePerNode;
  }

  public void setAutoCommit(boolean autoCommit) {
    checkNotInitialized();
    this.autoCommit = autoCommit;
  }

  public void setConnectionTimeoutMs(int connectionTimeoutMs) {
    checkNotInitialized();
    this.connectionTimeoutMs = connectionTimeoutMs;
  }

  @Override
  public Connection getConnection() throws SQLException {
    if (!initialized) {
      initialize();
    }
    return clusterManager.getPool().getConnection();
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    if (!initialized) {
      initialize();
    }

    // If username is not-null, the user and password must match the set ones.
    if (username != null && (!username.equals(this.user) ||
                            (password == null && this.password != null) ||
                            (password != null && !password.equals(this.password)))) {
      throw new SQLFeatureNotSupportedException (
        "Can only get connections with the username and password this DataSource was initialized with");
    }
    return clusterManager.getPool().getConnection();
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    return null;
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    throw new SQLException("Cannot unwrap to " + iface.getName());
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return null;
  }

  @Override
  public void setLogWriter(PrintWriter out) throws SQLException {
    // NOOP.
  }

  @Override
  public void setLoginTimeout(int seconds) throws SQLException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    return Integer.parseInt(PGProperty.LOGIN_TIMEOUT.getDefaultValue());
  }

  @Override
  public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return null;
  }

  public void close() {
    if (initialized) {
      clusterManager.close();
    }
  }

}
