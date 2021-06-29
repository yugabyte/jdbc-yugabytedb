package tmptest;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import com.yugabyte.ysql.ClusterAwareLoadBalancer;
import com.yugabyte.ysql.LoadBalanceProperties;

import java.io.IOException;
import java.sql.*;
import java.util.*;

public class DemoLoadBalance {

  public static void main(String[] argv) {
    try {
      System.out.println("\nLOAD BALANCER DEMO");
      String controlhost = argv[0];
      String controlport = argv[1];
      String numConnectionsToBeMade = argv[2];
      String numConnectionsForAffinity = argv[3];
      String additionalEndpoints = null;
      // "127.0.0.2:5433,127.0.0.3:5433,127.0.0.4:5433,127.0.0.5:5433,127.0.0.6:5433";
      if (argv.length > 4) {
        additionalEndpoints = argv[4];
      }
      System.out.println();
      System.out.println("server endpoint = " + controlhost + ":" + controlport);
      System.out.println("Initial number of connections to be made = " + numConnectionsToBeMade);
      System.out.println();
      Thread.sleep(2000);
      System.out.println("Setting up a Hikari Pool with uniform load balancing policy");
      Thread.sleep(2000);
      System.out.println();
      String controlurl = "jdbc:postgresql://" + controlhost
        + ":" + controlport + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=true";
      testWithHikariPool("uniform_load_pool", "true", "simple",
        controlurl, controlhost, Integer.valueOf(numConnectionsToBeMade), true, additionalEndpoints);
//      Thread.sleep(2000);
//      System.out.println();
//      System.out.println("Setting up another Hikari Pool with geo affinity load balancing policy for " + "region1.zone10");
//      System.in.read();
//      System.out.println();
//
//      testWithHikariPool("geoAffinity_load_pool", "region1.zone10",
//        "region1.zone10", controlurl, controlhost, Integer.valueOf(numConnectionsForAffinity), true);
//
//      System.out.println();
      System.out.println("END OF DEMO. THANK YOU.");
      System.out.println();
    } catch (SQLException throwables) {
      throwables.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
//    } catch (IOException e) {
//      e.printStackTrace();
    }
  }

  private static void testWithHikariPool(
    String name, String lbpropvalue, String lookupKey, String url, String hostName,
    int numConnections, boolean createTable, String additionalEndpoints) throws SQLException, InterruptedException {
    String ds_yb = "com.yugabyte.ysql.YBClusterAwareDataSource";
    Properties poolProperties = new Properties();
    poolProperties.setProperty("dataSourceClassName", ds_yb);
    poolProperties.setProperty("maximumPoolSize", String.valueOf(numConnections));
    poolProperties.setProperty("connectionTimeout", "1000000");
    poolProperties.setProperty("autoCommit", "true");
    poolProperties.setProperty("dataSource.serverName", hostName);
    poolProperties.setProperty("dataSource.portNumber", "5433");
    poolProperties.setProperty("dataSource.databaseName", "yugabyte");
    poolProperties.setProperty("dataSource.user", "yugabyte");
    poolProperties.setProperty("dataSource.password", "yugabyte");
    poolProperties.setProperty("dataSource.loadBalance", "true");
    if (additionalEndpoints != null) {
      poolProperties.setProperty("dataSource.additionalEndpoints", additionalEndpoints);
    }
    // "127.0.0.2:5433,127.0.0.3:5433,127.0.0.4:5433,127.0.0.5:5433,127.0.0.6:5433"
    if (!lbpropvalue.equals("true")) {
      poolProperties.setProperty("dataSource.topologyKeys", lookupKey);
    }

    poolProperties.setProperty("poolName", name);

    HikariConfig config = new HikariConfig(poolProperties);
    config.validate();
    HikariDataSource ds = new HikariDataSource(config);
    System.out.println();
    List<Connection> allBorrowed = new ArrayList<>();
    try {
      for (int i = 0; i < numConnections; i++) {
        Connection conn = ds.getConnection();
        if (createTable && i == 0) {
          System.out.println("Creating a table and populating some data");
          Thread.sleep(2000);
          createTableAndInsertSomeData(conn);
          System.out.println();
        }
        allBorrowed.add(conn);
      }
      System.out.println("Going to execute some queries on different connections of the pool");
      System.out.println();
      Thread.sleep(2000);
      runQueriesOnRandomConnections(allBorrowed, numConnections);
      ClusterAwareLoadBalancer ld = LoadBalanceProperties.CONNECTION_MANAGER_MAP.get(lookupKey);
      ld.printHostToConnMap();
      System.out.println();
      System.out.println("Check UI for connections and then press enter");
      System.in.read();
      if (lookupKey.equalsIgnoreCase("simple")) {
        System.out.println("Add some servers to check if new connection goes there");
        System.out.println("Start a few and press enter");
        System.in.read();
        System.out.println();
        System.out.println("Creating 6 more connections using direct JDBC API DriverManager.getConnection(...)");
        Thread.sleep(2000);
        System.out.println();
        for (int i = 0; i < 6; i++) {
          if (i == 0) {
            ClusterAwareLoadBalancer.FORCE_REFRESH = true;
          } else {
            ClusterAwareLoadBalancer.FORCE_REFRESH = false;
          }
          Connection conn = DriverManager.getConnection(url);
          allBorrowed.add(conn);
        }
        System.out.println();
        ld.printHostToConnMap();
        System.out.println();
        System.out.println("Check UI for connections and then press enter");
        System.in.read();
        System.out.println();
      }
    } catch (SQLException sqle) {
      System.out.println(sqle.getSQLState());
    } catch (HikariPool.PoolInitializationException | IOException e) {
      e.printStackTrace();
      System.out.println("Cannot start connection pool for host " + hostName + ". " + e.getMessage());
    } finally {
      for (int i = 0; i < allBorrowed.size(); i++) {
        allBorrowed.get(i).close();
      }
      System.out.println("Closing the pool: " + name);
      ds.close();
    }
  }

  private static void createTableAndInsertSomeData(Connection conn) throws SQLException {
    Statement st = conn.createStatement();
    try {
      st.execute("DROP TABLE IF EXISTS SOCCER_PLAYERS");
      st.execute("CREATE TABLE SOCCER_PLAYERS(" +
        "id int not null primary key, name varchar(20) not null," +
        " age int not null, role varchar(20) not null)");
      st.executeUpdate("insert into SOCCER_PLAYERS values (1, 'messi', 30, 'forward')");
      st.executeUpdate("insert into SOCCER_PLAYERS values (2, 'ronaldo', 31, 'forward')");
      st.executeUpdate("insert into SOCCER_PLAYERS values (3, 'salah', 29, 'mid-fielder')");
      st.executeUpdate("insert into SOCCER_PLAYERS values (4, 'karim', 24, 'defender')");
      st.executeUpdate("insert into SOCCER_PLAYERS values (5, 'benzema', 24, 'goal-keeper')");
    } catch (SQLException ex) {
      System.out.println("Exception: " + ex.getMessage());
      ex.printStackTrace();
    }
  }

  private static final String[] some_queries = new String[]{
    "select name, age from SOCCER_PLAYERS",
    "select max(age) from SOCCER_PLAYERS",
    "select min(age) from SOCCER_PLAYERS",
    "select name, role from SOCCER_PLAYERS where role = 'forward'",
    "select age, role from SOCCER_PLAYERS where role = 'forward'",
  };

  private static void runQueriesOnRandomConnections(
    List<Connection> allWorkerConns, int nc) throws SQLException {
    Random rand = new Random();
    for (int i = 0; i < 5; i++) {
      int r = rand.nextInt(nc);
      Connection conn = allWorkerConns.get(r);
      Statement st = conn.createStatement();
      int qr = rand.nextInt(5);
      String query = some_queries[qr];
      System.out.println("Executing query: " + query + " on connection: " + conn);
      st.execute(query);
      ResultSet rs = st.getResultSet();
      int count = 0;
      while (rs.next()) {
        count++;
      }
      rs.close();
      System.out.println("Num records obtained " + count);
      System.out.println();
    }
  }
}
