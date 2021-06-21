package com.yugabyte.examples;

import com.yugabyte.ysql.LoadBalanceProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public class UniformLoadBalanceExample {
  protected static String controlUrl = "";
  protected static HikariDataSource hikariDataSource;
  protected static Scanner scanner = new Scanner(System.in);
  protected static List<Connection> borrowConnections = new ArrayList<>();

  public static void main(String[] args) {
    try {
      Boolean verbose = args[0].equals("1");
      Boolean interactive = args[1].equals("1");
      String numConnections = "6";
      String controlHost = "127.0.0.1";
      String controlPort = "5433";

      controlUrl = "jdbc:postgresql://" + controlHost
        + ":" + controlPort + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=true";

      System.out.println("Setting up the connection pool having 6 connections.......");
      Thread.sleep(1000);

      testUsingHikariPool("uniform_load_balance", "true", "simple",
        controlHost, controlPort, numConnections, verbose, interactive);
    } catch (InterruptedException e) {
      Thread.interrupted();
      e.printStackTrace();
    }
  }

  protected static void testUsingHikariPool(String poolName, String lbpropvalue, String lookupKey,
                                            String hostName, String port, String numConnections, Boolean verbose, Boolean interactive) {
    try {
      String ds_yb = "com.yugabyte.ysql.YBClusterAwareDataSource";

      Properties poolProperties = new Properties();
      poolProperties.setProperty("poolName", poolName);
      poolProperties.setProperty("dataSourceClassName", ds_yb);
      poolProperties.setProperty("maximumPoolSize", numConnections);
      poolProperties.setProperty("connectionTimeout", "1000000");
      poolProperties.setProperty("autoCommit", "true");
      poolProperties.setProperty("dataSource.serverName", hostName);
      poolProperties.setProperty("dataSource.portNumber", port);
      poolProperties.setProperty("dataSource.databaseName", "yugabyte");
      poolProperties.setProperty("dataSource.user", "yugabyte");
      poolProperties.setProperty("dataSource.password", "yugabyte");
      poolProperties.setProperty("dataSource.loadBalance", "true");
      poolProperties.setProperty("dataSource.additionalEndpoints",
        "127.0.0.2:5433,127.0.0.3:5433");
      if (!lbpropvalue.equals("true")) {
        poolProperties.setProperty("dataSource.topologyKeys", lookupKey);
      }

      HikariConfig hikariConfig = new HikariConfig(poolProperties);
      hikariConfig.validate();
      hikariDataSource = new HikariDataSource(hikariConfig);

      //creating a table
      Connection connection = hikariDataSource.getConnection();
      performTableCreation(connection);
      connection.close();

      //running multiple threads concurrently
      runSqlQueriesOnMultipleThreads();

      LoadBalanceProperties.CONNECTION_MANAGER_MAP.get(lookupKey).printHostToConnMap();
      System.out.println();

      if (interactive) {
        System.out.println("You can verify the connections on the server side using your browser");
        System.out.println("For example, you can visit \"127.0.0.1:13000/rpcz\"" + " and similarly for others...");
      }

      continueScript("add_node");

      //it will pause this java app till adding a node is done in cluster by shell script, for user-interaction if required based on the options provided while executing the script
      pauseApp(".jdbc_example_app_checker");

      makeSomeNewConnections(7);

      LoadBalanceProperties.CONNECTION_MANAGER_MAP.get(lookupKey).printHostToConnMap();
      System.out.println();

      if (interactive) {
        System.out.println("You can verify the connections on the server side using your browser");
        System.out.println("For example, you can visit \"127.0.0.1:13000/rpcz\"" + " and similarly for others...");
      }

      continueScript("stop_node");

      //it will pause this java app till stopping a node is done in cluster by shell script and for user-interaction if required based on the options provided while executing the script
      pauseApp(".jdbc_example_app_checker2");

      makeSomeNewConnections(4);

      LoadBalanceProperties.CONNECTION_MANAGER_MAP.get(lookupKey).printHostToConnMap();
      System.out.println();

      if (interactive) {
        System.out.println("You can verify the connections on the server side using your browser");
        System.out.println("For example, you can visit \"127.0.0.1:13000/rpcz\"" + " and similarly for others...");
      }

      continueScript("perform_cleanup");

      //it will pause this java app for user-interaction if required based on the options provided while executing the script
      pauseApp(".jdbc_example_app_checker3");
      System.out.println("Closing the java app...");
      hikariDataSource.close();
    } catch (SQLException throwables) {
      throwables.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  protected static void performTableCreation(Connection connection) {
    try {
      Statement statement = connection.createStatement();
      statement.execute("DROP TABLE IF EXISTS AGENTS");
      String query = "CREATE TABLE AGENTS  ( AGENT_CODE VARCHAR(6) NOT NULL PRIMARY KEY, AGENT_NAME VARCHAR(40), " +
        "WORKING_AREA VARCHAR(35), COMMISSION numeric(10,2), PHONE_NO VARCHAR(15))";
      statement.execute(query);

      statement.executeUpdate("INSERT INTO AGENTS VALUES ('A007', 'Ramasundar', 'Bangalore', '0.15', '077-25814763')");
      statement.executeUpdate("INSERT INTO AGENTS VALUES ('A003', 'Alex ', 'London', '0.13', '075-12458969')");
      statement.executeUpdate("INSERT INTO AGENTS VALUES ('A008', 'Alford', 'New York', '0.12', '044-25874365')");
      statement.executeUpdate("INSERT INTO AGENTS VALUES ('A011', 'Ravi Kumar', 'Bangalore', '0.15', '077-45625874')");
      statement.executeUpdate("INSERT INTO AGENTS VALUES ('A010', 'Santakumar', 'Chennai', '0.14', '007-22388644')");
      statement.executeUpdate("INSERT INTO AGENTS VALUES ('A012', 'Lucida', 'San Jose', '0.12', '044-52981425')");
      statement.executeUpdate("INSERT INTO AGENTS VALUES ('A005', 'Anderson', 'Brisban', '0.13', '045-21447739' )");
      statement.executeUpdate("INSERT INTO AGENTS VALUES ('A001', 'Subbarao', 'Bangalore', '0.14', '077-12346674')");
    } catch (SQLException throwables) {
      System.out.println("Exception occured at createTable function");
      throwables.printStackTrace();
    }
  }

  protected static void runSqlQueriesOnMultipleThreads() throws InterruptedException {
    int nthreads = 6;
    Thread[] threads = new Thread[nthreads];
    for (int i = 0; i < nthreads; i++) {
      threads[i] = new Thread(new UniformLoadBalanceExample.ConcurrentQueriesClass());
    }

    for (int i = 0; i < nthreads; i++) {
      threads[i].start();
    }

    for (int i = 0; i < nthreads; i++) {
      threads[i].join();
    }
  }

  protected static String[] sqlQueries = new String[]{
    "Select AGENT_NAME, COMMISSION from AGENTS",
    "Select max(COMMISSION) from AGENTS",
    "Select PHONE_NO from AGENTS",
    "Select WORKING_AREA from AGENTS"
  };

  protected static void runSomeSqlQueries(Connection connection) {
    try {
      for (int i = 0; i < sqlQueries.length; i++) {
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(sqlQueries[i]);
        int cnt = 0;
        while (rs.next()) {
          cnt += 1;
        }
        statement.close();
      }
    } catch (SQLException throwables) {
      System.out.println("Exception occured at runSqlQueries function");
      throwables.printStackTrace();
    }
  }

  static class ConcurrentQueriesClass implements Runnable {
    @Override
    public void run() {
      try {
        Connection connection = hikariDataSource.getConnection();
        for (int i = 1; i <= 1000; i++) {
          runSomeSqlQueries(connection);
        }
      } catch (SQLException throwables) {
        throwables.printStackTrace();
      }
    }
  }


  protected static void makeSomeNewConnections(int new_connections) {
    System.out.println("Creating " + new_connections + " new connections....");
    try {
      for (int i = 1; i <= new_connections; i++) {
        Connection connection = DriverManager.getConnection(controlUrl);
        runSomeSqlQueries(connection);
        borrowConnections.add(connection);
      }
    } catch (SQLException throwables) {
      throwables.printStackTrace();
    }
  }

  protected static void continueScript(String flagValue) {
    FileWriter fileWriter = null;
    try {
      fileWriter = new FileWriter(".notify_shell_script");
      fileWriter.write(flagValue);
      fileWriter.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  protected static void pauseApp(String s) {
    try {
      System.in.read();
    } catch (IOException e) {
      e.printStackTrace();
    }
    File file = new File(s);
    while (file.exists() == false) ;
  }
}
