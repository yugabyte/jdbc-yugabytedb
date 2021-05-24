package org.postgresql.jdbc.yugabyte;

import java.sql.*;
import java.util.*;

public class UniformLoadDistributor {
  protected static final String GET_SERVERS_QUERY = "select * from yb_servers()";
  private static volatile UniformLoadDistributor instance;
  private long lastServerListFetchTime = 0L;
  private volatile ArrayList<String> servers = null;
  Map<String, Integer> hostToNumConnMap = new HashMap<>();
  Set<String> unreachableHosts = new HashSet<>();

  public static UniformLoadDistributor instance() {
    return instance;
  }

  public UniformLoadDistributor() {
  }

  public static UniformLoadDistributor getInstance() {
    if (instance == null) {
      synchronized (UniformLoadDistributor.class) {
        if (instance == null) {
          instance = new UniformLoadDistributor();
        }
      }
    }
    return instance;
  }

  public String getLeastLoadedServer(List<String> failedHosts) {
    String chosenHost = null;
    int min = Integer.MAX_VALUE;
    for (String h : hostToNumConnMap.keySet()) {
      if (failedHosts.contains(h)) continue;
      int currLoad = hostToNumConnMap.get(h);
      if (currLoad < min) {
        chosenHost = h;
        min = currLoad;
      }
    }
    return chosenHost;
  }

  public static int REFRESH_INTERVAL_SECONDS = 300;

  public boolean needsRefresh() {
    long currentTimeInMillis = System.currentTimeMillis();
    long diff = (currentTimeInMillis - lastServerListFetchTime) / 1000;
    boolean firstTime = this.servers == null;
    return (firstTime || diff > REFRESH_INTERVAL_SECONDS);
  }

  protected ArrayList<String> getCurrentServers(Connection conn) throws SQLException {
    Statement st = conn.createStatement();
    System.out.println("Executing select * from yb_servers()");
    ResultSet rs = st.executeQuery(GET_SERVERS_QUERY);
    ArrayList<String> currentServers = new ArrayList<>();
    while (rs.next()) {
      String host = rs.getString("host");
      currentServers.add(host);
    }
    System.out.println("List of servers got: " + currentServers);
    System.out.println();
    return currentServers;
  }

  public synchronized boolean refresh(Connection conn) throws SQLException {
    if (!needsRefresh()) return true;
    // else clear server list
    long currTime = System.currentTimeMillis();
    servers = getCurrentServers(conn);
    this.lastServerListFetchTime = currTime;
    unreachableHosts.clear();
    if (!this.servers.isEmpty()) {
      for (String h : this.servers) {
        if (!hostToNumConnMap.containsKey(h)) {
          hostToNumConnMap.put(h, 0);
        }
      }
    }
    return true;
  }

  public List<String> getServers() {
    return this.servers;
  }

  public synchronized void updateConnectionMap(String host, int incDec) {
    // System.out.println("UpdateCOnnMap called with count = " + incDec);
    Integer currentCount = hostToNumConnMap.get(host);
    if (currentCount == null && incDec > 0) {
      hostToNumConnMap.put(host, incDec);
    } else if (currentCount != null ) {
      hostToNumConnMap.put(host, currentCount + incDec);
    }
  }

  public Set<String> getUnreachableHosts() {
    return unreachableHosts;
  }

  public synchronized void updateFailedHosts(String chosenHost) {
    unreachableHosts.add(chosenHost);
    this.hostToNumConnMap.remove(chosenHost);
  }

  public void printHostToConnMap() {
    System.out.println("Current load");
    System.out.println("-------------------");
    for (Map.Entry<String, Integer> e : hostToNumConnMap.entrySet()) {
      System.out.println(e.getKey() + " - " + e.getValue());
    }
  }
}
