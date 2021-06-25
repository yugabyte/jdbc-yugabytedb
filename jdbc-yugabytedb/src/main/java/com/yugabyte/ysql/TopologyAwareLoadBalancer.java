package com.yugabyte.ysql;

import org.postgresql.jdbc.PgConnection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.logging.Level;

public class TopologyAwareLoadBalancer extends ClusterAwareLoadBalancer {
  private final String placements;
  private final Map<String, Set<String>> allowedPlacements;

  public TopologyAwareLoadBalancer(String placementvalues) {
    placements = placementvalues;
    allowedPlacements = new HashMap<>();
    populatePlacementMap();
  }

  protected String loadBalancingNodes() {
    return placements;
  }

  private void populatePlacementMap() {
    String[] placementstrings = placements.split(",");
    for (String pl : placementstrings) {
      String[] regionandzone = pl.split("\\.");
      if (regionandzone.length == 1) {
        // all zones are allowed. Empty hash set means all zones
        allowedPlacements.put(regionandzone[0], new HashSet<>());
      } else {
        Set<String> set = allowedPlacements.get(regionandzone[0]);
        if (set != null && !set.isEmpty()) {
          set.add(regionandzone[1]);
        } else if (set == null) {
          HashSet<String> zoneset = new HashSet<>();
          zoneset.add(regionandzone[1]);
          allowedPlacements.put(regionandzone[0], zoneset);
        }
      }
    }
  }

  @Override
  protected ArrayList<String> getCurrentServers(Connection conn) throws SQLException {
    Statement st = conn.createStatement();
    LOGGER.log(Level.FINE, "Getting the list of servers in: " + placements);
    ResultSet rs = st.executeQuery(ClusterAwareLoadBalancer.GET_SERVERS_QUERY);
    ArrayList<String> currentPrivateIps = new ArrayList<>();
    ArrayList<String> currentPublicIps = new ArrayList<>();
    String hostConnectedTo = ((PgConnection)conn).getQueryExecutor().getHostSpec().getHost();
    Boolean useHostColumn = null;
    while (rs.next()) {
      String host = rs.getString("host");
      String publicIp = rs.getString("public_ip");
      String region = rs.getString("region");
      String zone = rs.getString("zone");
      Set<String> zones = allowedPlacements.get(region);
      if (hostConnectedTo.equals(host)) {
        useHostColumn = Boolean.TRUE;
      } else if (hostConnectedTo.equals(publicIp)) {
        useHostColumn = Boolean.FALSE;
      }
      if (zones != null && (zones.isEmpty() || zones.contains(zone))) {
        currentPrivateIps.add(host);
        currentPublicIps.add(publicIp);
      }
    }
    return getPrivateOrPublicServers(useHostColumn, currentPrivateIps, currentPublicIps);
  }
}
