import org.postgresql.jdbc.ClusterAwareConnectionManager;
import org.postgresql.jdbc.PgConnection;

import java.io.IOException;
import java.sql.*;
import java.util.*;

public class YBJDBCTest {
  private static String which_host_query = "select inet_server_port( ), inet_server_addr( )";

  public static void main(String[] argv) {
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
      if (!(Integer.valueOf(numThreads) > 0)) {
        testConnBalance(numConnectionsToBeMade, controlurl);
      }
      ClusterAwareConnectionManager.REFRESH_INTERVAL_SECONDS = 2;
      testParallelConns(numThreads, controlurl);
    } catch (SQLException throwables) {
      throwables.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
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
      ClusterAwareConnectionManager cacm = ClusterAwareConnectionManager.getInstance();
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
                                      String controlurl) throws SQLException, InterruptedException, IOException {
    makeMoreConnectionsAndRunBasicQueries(numConnectionsToBeMade, controlurl);
    System.out.println("Going to sleep...Press enter to wake me up");
    System.in.read();
    System.out.println("woke up");
    makeMoreConnectionsAndRunBasicQueries("7", controlurl);
    System.out.println("Going to sleep again...Press enter to wake me up");
    System.in.read();
    makeMoreConnectionsAndRunBasicQueries("9", controlurl);
    for (Connection c : allWorkerConns) c.close();
    ClusterAwareConnectionManager cacm = ClusterAwareConnectionManager.getInstance();
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
    String connectionsToBeMade, String controlUrl) throws SQLException, InterruptedException {
    int numConnections = Integer.valueOf(connectionsToBeMade);
    for (int i = 0; i < numConnections; i++) {
      Connection conn = DriverManager.getConnection(controlUrl);
      allWorkerConns.add(conn);
      conn.setAutoCommit(true);
      String connName = "Conn[" + i + "]";
      runConnQuery(connName, conn);
    }
    printHostToConnectionCounts(allWorkerConns);
    runQueriesOnRandomConnections(allWorkerConns);
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

  private static void runQueriesOnRandomConnections(List<Connection> allWorkerConns) throws SQLException {
    List<String> liveservers = ClusterAwareConnectionManager.instance().getServers();
    List<Connection> availableConnections = new ArrayList<>();
    for (Connection c : allWorkerConns) {
      String host = ((PgConnection)c).getQueryExecutor().getHostSpec().getHost();
      if (liveservers.contains(host)) availableConnections.add(c);
    }
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
