package com.yugabyte.ysql;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClusterAwareLoadBalancer {
  protected static final String GET_SERVERS_QUERY = "select * from yb_servers()";
  protected static final Logger LOGGER = Logger.getLogger("org.postgresql.Driver");

  private static volatile ClusterAwareLoadBalancer instance;
  private long lastServerListFetchTime = 0L;
  private volatile ArrayList<String> servers = null;
  Map<String, Integer> hostToNumConnMap = new HashMap<>();
  Set<String> unreachableHosts = new HashSet<>();

  public static ClusterAwareLoadBalancer instance() {
    return instance;
  }

  public ClusterAwareLoadBalancer() {
  }

  public static ClusterAwareLoadBalancer getInstance() {
    if (instance == null) {
      synchronized (ClusterAwareLoadBalancer.class) {
        if (instance == null) {
          instance = new ClusterAwareLoadBalancer();
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

  public static int REFRESH_LIST_SECONDS = 10;

  public static boolean FORCE_REFRESH = false;

  public boolean needsRefresh() {
    if (FORCE_REFRESH) return true;
    long currentTimeInMillis = System.currentTimeMillis();
    long diff = (currentTimeInMillis - lastServerListFetchTime) / 1000;
    boolean firstTime = this.servers == null;
    return (firstTime || diff > REFRESH_LIST_SECONDS);
  }

  protected ArrayList<String> getCurrentServers(Connection conn) throws SQLException {
    Statement st = conn.createStatement();
    LOGGER.log(Level.FINE, "Getting the list of servers");
    ResultSet rs = st.executeQuery(GET_SERVERS_QUERY);
    ArrayList<String> currentServers = new ArrayList<>();
    while (rs.next()) {
      String host = rs.getString("host");
      currentServers.add(host);
    }
    LOGGER.log(Level.FINE, "List of servers got {0}", currentServers);
    // System.out.println("List of servers got: " + currentServers);
    // System.out.println();
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
    LOGGER.log(Level.FINE, "updating connection count for {0} by {1}",
      new String[]{host, String.valueOf(incDec)});
    Integer currentCount = hostToNumConnMap.get(host);
    if (currentCount == 0 && incDec < 0) return;
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

  protected String loadBalancingNodes() {
    return "all";
  }

  public void printHostToConnMap() {
    System.out.println("Current load on " + loadBalancingNodes() + " servers");
    System.out.println("-------------------");
    for (Map.Entry<String, Integer> e : hostToNumConnMap.entrySet()) {
      System.out.println(e.getKey() + " - " + e.getValue());
    }
  }
}
