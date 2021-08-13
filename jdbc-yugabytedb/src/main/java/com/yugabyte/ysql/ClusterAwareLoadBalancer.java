package com.yugabyte.ysql;

import org.postgresql.jdbc.PgConnection;
import org.postgresql.util.GT;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
  protected Map<String, String> hostPortMap = new HashMap<>();
  protected Map<String, String> hostPortMap_public = new HashMap<>();

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

  public String getPort(String host) {
    String port = hostPortMap.get(host);
    if (port == null) {
      port = hostPortMap_public.get(host);
    }
    return port;
  }

  public String getLeastLoadedServer(List<String> failedHosts) {
    int min = Integer.MAX_VALUE;
    ArrayList<String> minConnectionsHostList = new ArrayList<>();
    for (String h : hostToNumConnMap.keySet()) {
      if (failedHosts.contains(h)) continue;
      int currLoad = hostToNumConnMap.get(h);
      if (currLoad < min) {
        min = currLoad;
        minConnectionsHostList.clear();
        minConnectionsHostList.add(h);
      } else if (currLoad == min) {
        minConnectionsHostList.add(h);
      }
    }
    // Choose a random from the minimum list
    String chosenHost = null;
    if (minConnectionsHostList.size() > 0) {
      Random rand = new Random();
      chosenHost = minConnectionsHostList.get(rand.nextInt(minConnectionsHostList.size()));
    }
    LOGGER.log(Level.FINE, "Host chosen for new connection: " + chosenHost);
    return chosenHost;
  }

  public static int REFRESH_LIST_SECONDS = 300;

  public static boolean FORCE_REFRESH = false;

  public boolean needsRefresh() {
    if (FORCE_REFRESH) return true;
    long currentTimeInMillis = System.currentTimeMillis();
    long diff = (currentTimeInMillis - lastServerListFetchTime) / 1000;
    boolean firstTime = servers == null;
    return (firstTime || diff > REFRESH_LIST_SECONDS);
  }

  protected static String columnToUseForHost = null;

  protected ArrayList<String> getCurrentServers(Connection conn) throws SQLException {
    Statement st = conn.createStatement();
    ResultSet rs = st.executeQuery(GET_SERVERS_QUERY);
    ArrayList<String> currentPrivateIps = new ArrayList<>();
    ArrayList<String> currentPublicIps = new ArrayList<>();
    String hostConnectedTo = ((PgConnection)conn).getQueryExecutor().getHostSpec().getHost();
    InetAddress hostConnectedInetAddr;
    try {
      hostConnectedInetAddr = InetAddress.getByName(hostConnectedTo);
    } catch (UnknownHostException e) {
      throw new SQLException();
    }
    Boolean useHostColumn = null;
    boolean isIpv6Addresses = hostConnectedTo.contains(":");
    if (isIpv6Addresses) {
      hostConnectedTo = hostConnectedTo.replace("[", "").replace("]", "");
      try {
        hostConnectedInetAddr = InetAddress.getByName(hostConnectedTo);
      } catch (UnknownHostException e) {
        // This is totally unexpected. As the connection is already created on this host
        throw new PSQLException(GT.tr("Unexpected UnknownHostException for ${0} ", hostConnectedTo),
          PSQLState.UNKNOWN_STATE, e);
      }
    }
    while (rs.next()) {
      String host = rs.getString("host");
      String public_host = rs.getString("public_ip");
      String port = rs.getString("port");
      hostPortMap.put(host, port);
      hostPortMap_public.put(public_host, port);
      currentPrivateIps.add(host);
      currentPublicIps.add(public_host);
      InetAddress hostInetAddr;
      InetAddress publicHostInetAddr;
      try {
        hostInetAddr = (host != null
          && !host.isEmpty()) ? InetAddress.getByName(host) : null;
      } catch (UnknownHostException e) {
        // set the hostInet to null
        hostInetAddr = null;
      }
      try {
        publicHostInetAddr = (public_host != null
          && !public_host.isEmpty()) ? InetAddress.getByName(public_host) : null;
      } catch (UnknownHostException e) {
        // set the publicHostInetAddr to null
        publicHostInetAddr = null;
      }
      if (useHostColumn == null) {
        if (hostConnectedInetAddr.equals(hostInetAddr)) {
          useHostColumn = Boolean.TRUE;
        } else if (hostConnectedInetAddr.equals(publicHostInetAddr)) {
          useHostColumn = Boolean.FALSE;
        }
      }
    }
    return getPrivateOrPublicServers(useHostColumn, currentPrivateIps, currentPublicIps);
  }

  protected ArrayList<String> getPrivateOrPublicServers(
    Boolean useHostColumn, ArrayList<String> privateHosts, ArrayList<String> publicHosts) {
    if (useHostColumn == null) {
      LOGGER.log(Level.WARNING, "Either private or public should have been determined");
      return null;
    }
    ArrayList<String> currentHosts = useHostColumn ? privateHosts : publicHosts;
    LOGGER.log(Level.FINE, "List of servers got {0}", currentHosts);
    return currentHosts;
  }

  public synchronized boolean refresh(Connection conn) throws SQLException {
    if (!needsRefresh()) return true;
    // else clear server list
    long currTime = System.currentTimeMillis();
    servers = getCurrentServers(conn);
    if (servers == null) return false;
    lastServerListFetchTime = currTime;
    unreachableHosts.clear();
    for (String h : servers) {
      if (!hostToNumConnMap.containsKey(h)) {
        hostToNumConnMap.put(h, 0);
      }
    }
    return true;
  }

  public List<String> getServers() {
    return Collections.unmodifiableList(servers);
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
    hostToNumConnMap.remove(chosenHost);
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
