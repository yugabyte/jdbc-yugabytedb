package com.yugabyte.examples;

import com.yugabyte.ysql.LoadBalanceProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public class TopologyAwareLoadBalanceExample {
  private static String controlUrlWithPlacement;
  private static HikariDataSource hikariDataSource;
  static Scanner scanner = new Scanner(System.in);
  static List<Connection> borrowConnections = new ArrayList<>();

  public static void main(String[] args) {
    try {

      Boolean VERBOSE = (args[0].equals("1") ? true : false);
      Boolean INTERACTIVE = (args[1].equals("1") ? true : false);
      String numConnections = "6";
      String controlHost = "127.0.0.1";
      String controlPort = "5433";

      controlUrlWithPlacement = "jdbc:postgresql://" + controlHost
        + ":" + controlPort + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=true&topology-keys=region1.zone1";

      System.out.println("Setting up the connection pool having 6 connections.......");

      Thread.sleep(1000);

      testUsingHikariPool("topology_aware_load_balance", "region1.zone1", "region1.zone1",
        controlHost, controlPort, numConnections, VERBOSE, INTERACTIVE);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static void testUsingHikariPool(String poolName, String lbpropvalue, String lookupKey,
                                          String hostName, String port, String numConnections, Boolean VERBOSE, Boolean INTERACTIVE) {
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
      if (!lbpropvalue.equals("true")) {
        poolProperties.setProperty("dataSource.topologyKeys", lookupKey);
      }

      HikariConfig hikariConfig = new HikariConfig(poolProperties);
      hikariConfig.validate();
      hikariDataSource = new HikariDataSource(hikariConfig);

      //creating a table
      Connection connection = hikariDataSource.getConnection();
      createTableStatements(connection);
      connection.close();

      //running multiple threads concurrently
      runSqlQueriesOnMultipleThreads();

      LoadBalanceProperties.CONNECTION_MANAGER_MAP.get(lookupKey).printHostToConnMap();

      if(INTERACTIVE) {
        System.out.println("You can verify the connections on the server side using your browser");
        System.out.println("For example, you can visit \"127.0.0.1:13000/rpcz\"" + " and similarly for others...");
      }

      continueScript("flag1");
      pauseApp(".jdbc_example_app_checker");

      makeSomeNewConnections(7);
      LoadBalanceProperties.CONNECTION_MANAGER_MAP.get(lookupKey).printHostToConnMap();


      continueScript("flag2");
      pauseApp(".jdbc_example_app_checker2");

//      LoadBalanceProperties.CONNECTION_MANAGER_MAP.get(lookupKey).printHostToConnMap(); //for debugging

      makeSomeNewConnections(4);
      LoadBalanceProperties.CONNECTION_MANAGER_MAP.get(lookupKey).printHostToConnMap();

      if (INTERACTIVE) {
        System.out.println("You can verify the connections on the server side using your browser");
        System.out.println("For example, you can visit \"127.0.0.1:13000/rpcz\"" + " and similarly for others...");
      }

      continueScript("flag3");
      pauseApp(".jdbc_example_app_checker3");

      System.out.println("Closing the java app...");
      hikariDataSource.close();
      Thread.sleep(1000);
    } catch (SQLException throwables) {
      throwables.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static void createTableStatements(Connection connection) {
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

  private static void runSqlQueriesOnMultipleThreads() throws InterruptedException {
    int nthreads = 6;
    Thread[] threads = new Thread[nthreads];
    for (int i = 0; i < nthreads; i++) {
      threads[i] = new Thread(new ConcurrentQueriesClass());
    }
    for (int i = 0; i < nthreads; i++) {
      threads[i].start();
    }

    for (int i = 0; i < nthreads; i++) {
      threads[i].join();
    }
  }

  private static String [] sqlQueries = new String[] {
    "Select AGENT_NAME, COMMISSION from AGENTS",
    "Select max(COMMISSION) from AGENTS",
    "Select PHONE_NO from AGENTS",
    "Select WORKING_AREA from AGENTS"
  };

  private static void runSomeSqlQueries(Connection connection) {
    try {
      for(int i=0; i<sqlQueries.length; i++) {
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(sqlQueries[i]);
        int cnt =0;
        while (rs.next()) {
          cnt += 1;
        }
      }
    }catch (SQLException throwables) {
      System.out.println("Exception occured at runSqlQueries function");
      throwables.printStackTrace();
    }
  }

  static class ConcurrentQueriesClass implements  Runnable {
    @Override
    public void run() {
      try {
        Connection connection = hikariDataSource.getConnection();
        for (int i = 1; i <= 1000; i++) {
          runSomeSqlQueries(connection);
        }
      }
      catch (SQLException throwables) {
        System.out.println("Exception occured at ConcurrentQueriesClass, SQLStateCode" + throwables.getSQLState());
        throwables.printStackTrace();
      }
      catch (Exception e) {
      }
    }
  }


  static void makeSomeNewConnections(int new_connections) {
    System.out.println("Creating " + new_connections + " new connections....");
    try {
      for(int i=1; i<=new_connections; i++) {
        Connection connection = DriverManager.getConnection(controlUrlWithPlacement);
        runSomeSqlQueries(connection);
        borrowConnections.add(connection);
      }

    } catch (SQLException throwables) {
      throwables.printStackTrace();
    }
  }

  private static void continueScript(String flagValue) {
    FileWriter fileWriter = null;
    try {
      fileWriter = new FileWriter(".notify_shell_script");
      fileWriter.write(flagValue);
      fileWriter.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  static void pauseApp(String s) {
    try {
      System.in.read();
    } catch (IOException e) {
      e.printStackTrace();
    }
    File file = new File(s);
    while (file.exists()==false) ;
  }
}
