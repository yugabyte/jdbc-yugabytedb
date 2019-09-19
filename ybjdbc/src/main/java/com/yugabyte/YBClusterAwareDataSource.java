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

package com.yugabyte;

import org.postgresql.PGProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

public class YBClusterAwareDataSource implements DataSource {
  private static final Logger LOGGER = LoggerFactory.getLogger(YBClusterAwareDataSource.class);

  private final static int DEFAULT_MAX_POOL_SIZE_PER_NODE = 6;
  private final static String DEFAULT_DATA_SOURCE =  "org.postgresql.ds.PGSimpleDataSource";
  private final static String DEFAULT_HOST = "localhost";
  private final static int DEFAULT_PORT = 5433;
  private final static String DEFAULT_DBNAME = "postgres";
  private final static String DEFAULT_USER = "postgres";
  private final static String DEFAULT_PASSWORD = "";

  private YBClusterManager clusterManager;

  public YBClusterAwareDataSource(String jdbcUrl) {
    this(jdbcUrl, DEFAULT_MAX_POOL_SIZE_PER_NODE);
  }

  public YBClusterAwareDataSource(String jdbcUrl, Integer maxPoolSizePerNode) {
    Properties properties = org.postgresql.Driver.parseURL(jdbcUrl, getDefaultProperties());
    String host = PGProperty.PG_HOST.get(properties);
    String port = PGProperty.PG_PORT.get(properties);
    String dbname = PGProperty.PG_DBNAME.get(properties);
    String user = PGProperty.USER.get(properties);
    String password = PGProperty.PASSWORD.get(properties);

    Properties poolProperties = new Properties();
    poolProperties.setProperty("dataSourceClassName", DEFAULT_DATA_SOURCE);
    poolProperties.setProperty("maximumPoolSize", maxPoolSizePerNode.toString());
    poolProperties.setProperty("dataSource.portNumber", port);
    poolProperties.setProperty("dataSource.databaseName", dbname);
    poolProperties.setProperty("dataSource.user", user);
    poolProperties.setProperty("dataSource.password", password);

    clusterManager = new YBClusterManager(host, poolProperties);
  }

  private static Properties getDefaultProperties() {
    Properties props = new Properties();
    PGProperty.PG_HOST.set(props, DEFAULT_HOST);
    PGProperty.PG_PORT.set(props, DEFAULT_PORT);
    PGProperty.PG_DBNAME.set(props, DEFAULT_DBNAME);
    PGProperty.USER.set(props, DEFAULT_USER);
    PGProperty.PASSWORD.set(props, DEFAULT_PASSWORD);
    return props;
  }

  @Override
  public Connection getConnection() throws SQLException {
    return clusterManager.getPool().getConnection();
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
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

}
