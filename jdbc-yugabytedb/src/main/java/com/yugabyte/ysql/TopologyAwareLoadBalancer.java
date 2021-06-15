package com.yugabyte.ysql;

import com.yugabyte.ysql.ClusterAwareLoadBalancer;

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
    populateMap();
  }

  protected String loadBalancingNodes() {
    return placements;
  }

  private void populateMap() {
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

  protected ArrayList<String> getCurrentServers(Connection conn) throws SQLException {
    Statement st = conn.createStatement();
    LOGGER.log(Level.FINE, "Getting the list of servers in: " + placements);
    ResultSet rs = st.executeQuery(ClusterAwareLoadBalancer.GET_SERVERS_QUERY);
    ArrayList<String> currentServers = new ArrayList<>();
    while (rs.next()) {
      String host = rs.getString("host");
      String region = rs.getString("region");
      String zone = rs.getString("zone");
      Set<String> zones = allowedPlacements.get(region);
      if (zones != null && (zones.isEmpty() || zones.contains(zone))) {
        currentServers.add(host);
      }
    }
    LOGGER.log(Level.FINE, "List of servers got {0}", currentServers);
//    System.out.println("List of servers got: " + currentServers);
//    System.out.println();
    return currentServers;
  }
}
