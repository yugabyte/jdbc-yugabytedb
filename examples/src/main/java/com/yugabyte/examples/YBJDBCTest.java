import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import com.yugabyte.ysql.ClusterAwareLoadBalancer;
import org.postgresql.jdbc.PgConnection;
import com.yugabyte.ysql.LoadBalanceProperties;

import java.io.IOException;
import java.sql.*;
import java.util.*;

public class YBJDBCTest {
  private static String which_host_query = "select inet_server_port( ), inet_server_addr( )";

//  static Logger logger = Logger.getLogger("org.postgresql.Driver");
//
//  private static void testIP() {
//    try {
//      String loggerproperties =
//        "handlers = java.util.logging.ConsoleHandler\n" +
//        "java.util.logging.ConsoleHandler.level = FINE\n" +
//        "java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter\n";
//      InputStream stream = new ByteArrayInputStream(loggerproperties.getBytes(StandardCharsets.UTF_8));
//      LogManager.getLogManager().readConfiguration(stream);
//      logger.addHandler(new ConsoleHandler());
//      logger.log(Level.INFO, "Hello world");
//      // Properties props = new Properties();
//      // props.load(stream);
//      // System.out.println(props);
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
//  }

  public static void main(String[] argv) {
    // testURLAndPropStripping();
    // testIP();
    //
    // System.exit(0);
    try {
      System.out.println("TEST");
      String controlhost = argv[0];
      String controlport = argv[1];
      String numConnectionsToBeMade = argv[2];
      String numThreads = argv[3];
      System.out.println();
      System.out.println("Control host = " + controlhost);
      System.out.println("Control port = " + controlport);
      System.out.println("Num connections to be made = " + numConnectionsToBeMade);
      System.out.println();
      String controlurl = "jdbc:postgresql://" + controlhost
        + ":" + controlport + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=true";
      // testWithHikariPool(controlurl, controlhost, Integer.valueOf(numConnectionsToBeMade));
      // System.exit(0);
      String controlurl_placement = "jdbc:postgresql://" + controlhost
        + ":" + controlport + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=true&topology-keys=datacenter1.rack1";
      if (!(Integer.valueOf(numThreads) > 0)) {
        testConnBalance(numConnectionsToBeMade, controlurl, "simple");
        // testConnBalance(numConnectionsToBeMade, controlurl_placement, "datacenter1.rack1");
      }
      ClusterAwareLoadBalancer.REFRESH_LIST_SECONDS = 2;
      // testParallelConns(numThreads, controlurl);
    } catch (SQLException throwables) {
      throwables.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void testWithHikariPool(String url, String hostName, int numConnections) throws SQLException, InterruptedException {
    String ds_yb = "com.yugabyte.ysql.YBSimpleDataSource";
    String ds_pg = "org.postgresql.ds.PGSimpleDataSource";
    url.replace("&load-balance=true", "");
    Properties poolProperties = new Properties();
    // poolProperties.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource");
    poolProperties.setProperty("dataSourceClassName", ds_yb);
    poolProperties.setProperty("maximumPoolSize", "30");
    poolProperties.setProperty("connectionTimeout", "1000000");
    poolProperties.setProperty("autoCommit", "true");
    poolProperties.setProperty("dataSource.serverName", hostName);
    poolProperties.setProperty("dataSource.portNumber", "5433");
    poolProperties.setProperty("dataSource.databaseName", "yugabyte");
    poolProperties.setProperty("dataSource.user", "yugabyte");
    poolProperties.setProperty("dataSource.password", "yugabyte");
    poolProperties.setProperty("dataSource.loadBalance", "true");
    poolProperties.setProperty("poolName", "loadbalanced");

    HikariConfig config = new HikariConfig(poolProperties);
    config.validate();
    HikariDataSource ds = new HikariDataSource(config);
    List<Connection> allBorrowed = new ArrayList<>();
    try {
      for(int i=0; i<numConnections; i++) {
        allBorrowed.add(ds.getConnection());
      }
      // runQueriesOnRandomConnections(allBorrowed, "simple");
      ClusterAwareLoadBalancer cacm = LoadBalanceProperties.CONNECTION_MANAGER_MAP.get("simple");
      cacm.printHostToConnMap();
    } catch (HikariPool.PoolInitializationException e) {
      e.printStackTrace();
      System.out.println("Cannot start connection pool for host " + hostName + ". " + e.getMessage());
    } finally {
      for(int i=0; i<numConnections; i++) {
        if (i <= allBorrowed.size()) {
          allBorrowed.get(i).close();
        }
      }
      ds.close();
    }
  }

  private static void testURLAndPropStripping() {
    String controlhost = "127.0.0.1";
    int controlport = 5433;
    String controlurl1 = "jdbc:postgresql://" + controlhost
      + ":" + controlport + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=true";

    String controlurl2 = "jdbc:postgresql://" + controlhost
      + ":" + controlport + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=false";

    String controlurl3 = "jdbc:postgresql://" + controlhost
      + ":" + controlport + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=false&xyz=false";

    String controlurl4 = "jdbc:postgresql://" + controlhost
      + ":" + controlport + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=placements:cloud1.reg1.zone1&xyz=false";

    String controlurl5 = "jdbc:postgresql://" + controlhost
      + ":" + controlport + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=placements:cloud1.reg1.zone1,cloud2.reg2.zone2&xyz=false";

    String controlurl6 = "jdbc:postgresql://" + controlhost
      + ":" + controlport + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=placements:cloud1.reg1.zone1";

    String controlurl7 = "jdbc:postgresql://" + controlhost
      + ":" + controlport + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=placements:cloud1.reg1.zone1,cloud2.reg2.zone2";

    String controlurl8 = "jdbc:postgresql://" + controlhost
      + ":" + controlport + "/yugabyte?user=yugabyte&password=yugabyte";

    List<String> urls = new ArrayList<>();
    urls.add(controlurl1);
    urls.add(controlurl2);
    urls.add(controlurl3);
    urls.add(controlurl4);
    urls.add(controlurl5);
    urls.add(controlurl6);
    urls.add(controlurl7);
    urls.add(controlurl8);

    for (int i=0; i<urls.size(); i++) {
      System.out.println("------------------------------------------------");
      System.out.println("URL number " + i);
      LoadBalanceProperties lbProps = new LoadBalanceProperties(urls.get(i), new Properties());
      System.out.println("original = " + lbProps.getOriginalURL());
      System.out.println("YBURL    = " + lbProps.getStrippedURL());
      System.out.println("Has LB   = " + lbProps.hasLoadBalance());
      System.out.println("Place    = " + lbProps.getPlacements()) ;
      System.out.println("------------------------------------------------");
    }
    System.exit(0);
  }

  static class ConnMakeDropRunSanity implements Runnable {

    final String connUrl;
    final int numItrs;
    public ConnMakeDropRunSanity(String connUrl, int numItrs) {
      this.connUrl = connUrl;
      this.numItrs = Integer.valueOf(numItrs);
    }

    @Override
    public void run() {
      int i = 0;
      ClusterAwareLoadBalancer cacm = ClusterAwareLoadBalancer.getInstance();
      while (i < numItrs) {
        try {
          i++;
          Connection conn = DriverManager.getConnection(connUrl);
          Statement st = conn.createStatement();
          int execItr = 0;
          while (execItr < 10) {
            execItr++;
            ResultSet rs = st.executeQuery("select * from pg_available_extensions");
            int cnt = 0;
            while(rs.next()) {
              cnt++;
            }
            System.out.println(Thread.currentThread().getId() + " cnt: " + cnt);
            System.out.println("Calling refresh");
            cacm.refresh(conn);
          }
          conn.close();
        } catch (SQLException se) {
          System.out.println("Got exception on thread: " + Thread.currentThread().getId());
          se.printStackTrace();
        }
      }
    }
  }

  private static void testParallelConns(String numThreads, String controlurl) throws InterruptedException {
    int nthreads = Integer.valueOf(numThreads);
    Thread[] threads = new Thread[nthreads];
    for (int i=0; i<nthreads; i++) {
      threads[i] = new Thread(new ConnMakeDropRunSanity(controlurl, 1000));
    }
    for (int i=0; i<nthreads; i++) {
      threads[i].start();
    }
    for (int i=0; i<nthreads; i++) {
      threads[i].join();
    }
  }

  private static void testConnBalance(String numConnectionsToBeMade,
      String controlurl, String cacmString) throws SQLException, InterruptedException, IOException {
    makeMoreConnectionsAndRunBasicQueries(numConnectionsToBeMade, controlurl, cacmString);
    System.out.println("Going to sleep...Press enter to wake me up");
    System.in.read();
    System.out.println("woke up");
    makeMoreConnectionsAndRunBasicQueries("8", controlurl, cacmString);
    System.out.println("Going to sleep again...Press enter to wake me up");
    System.in.read();
    makeMoreConnectionsAndRunBasicQueries("4", controlurl, cacmString);
    for (Connection c : allWorkerConns) c.close();
    ClusterAwareLoadBalancer cacm = LoadBalanceProperties.CONNECTION_MANAGER_MAP.get(cacmString);
    cacm.printHostToConnMap();
  }

  private static void runConnQuery(String connName, Connection conn) throws SQLException {
    Statement st = conn.createStatement();
    ResultSet rs = st.executeQuery(which_host_query);
    if (!rs.next()) {
      throw new RuntimeException("which conn query returned nothing");
    }
    String host = rs.getString(2);
    System.out.println("Connection: " + connName + " is connected to host " + host);
  }

  private static List<Connection> allWorkerConns = new ArrayList<>();

  private static void makeMoreConnectionsAndRunBasicQueries(
    String connectionsToBeMade, String controlUrl, String cacmString) throws SQLException, InterruptedException {
    int numConnections = Integer.valueOf(connectionsToBeMade);
    for (int i = 0; i < numConnections; i++) {
      Connection conn = DriverManager.getConnection(controlUrl);
      allWorkerConns.add(conn);
      conn.setAutoCommit(true);
      String connName = "Conn[" + i + "]";
      runConnQuery(connName, conn);
    }
    printHostToConnectionCounts(allWorkerConns);
    runQueriesOnRandomConnections(allWorkerConns, cacmString);
  }

  // connection state
  private static void printHostToConnectionCounts(List<Connection> allWorkerConns) {
    HashMap<String, Integer> map = new HashMap<>();
    for (Connection conn : allWorkerConns) {
      PgConnection pconn = (PgConnection) conn;
      String host = pconn.getQueryExecutor().getHostSpec().getHost();
      Integer existingCount = map.get(host);
      Integer count = 0;
      if (existingCount == null) {
        count = 1;
      } else {
        count = existingCount + 1;
      }
      map.put(host, count);
    }
    System.out.println("Host to number of connections       ");
    System.out.println("====================================");
    for (Map.Entry<String, Integer> e : map.entrySet()) {
      System.out.println(e.getKey() + "  -  " + e.getValue());
    }
  }

  private static void runQueriesOnRandomConnections(List<Connection> allWorkerConns, String cacmstring) throws SQLException {
    List<String> liveservers = LoadBalanceProperties.CONNECTION_MANAGER_MAP.get(cacmstring).getServers();
    List<Connection> availableConnections = new ArrayList<>();
    for (Connection c : allWorkerConns) {
      String host = ((PgConnection)c).getQueryExecutor().getHostSpec().getHost();
      if (liveservers != null && liveservers.contains(host)) availableConnections.add(c);
    }
    if (availableConnections.isEmpty()) return;
    for (int i = 0; i < 10; i++) {
      Collections.shuffle(availableConnections);
      Connection conn = availableConnections.get(0);
      Statement st = conn.createStatement();
      st.execute("select * from pg_available_extensions");
      ResultSet rs = st.getResultSet();
      int count = 0;
      while (rs.next()) {
        count++;
      }
      rs.close();
      System.out.println("Extension query on connection - " +
        ((PgConnection) conn).getQueryExecutor().getHostSpec().getHost() +
        " returned num extensions = " + count);
    }
  }
}
