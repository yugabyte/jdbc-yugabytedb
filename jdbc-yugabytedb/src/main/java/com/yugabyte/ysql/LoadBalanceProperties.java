package com.yugabyte.ysql;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

public class LoadBalanceProperties {
  private static final String SIMPLE_LB = "simple";
  private static final String LOAD_BALANCE_PROPERTY_KEY = "load-balance";
  private static final String TOPOLOGY_AWARE_PROPERTY_KEY = "topology-keys";
  private static final String PROPERTY_SEP = "&";
  private static final String EQUALS = "=";

  private static final Logger LOGGER = Logger.getLogger("org.postgresql.Driver");
  /* Topology/Cluster aware key to load balancer mapping. For uniform policy
   load-balance 'simple' to be used as KEY and for targeted topologies,
    <placements> value specified will be used as key
   */
  public static final Map<String, ClusterAwareLoadBalancer> CONNECTION_MANAGER_MAP = new HashMap<>();

  private final String originalUrl;
  private final Properties originalProperties;
  private final Properties strippedProperties;
  private boolean hasLoadBalance;
  private final String ybURL;
  private String placements = null;

  public LoadBalanceProperties(String origUrl, Properties origProperties) {
    originalUrl = origUrl;
    originalProperties = origProperties;
    strippedProperties = (Properties) origProperties.clone();
    strippedProperties.remove(LOAD_BALANCE_PROPERTY_KEY);
    strippedProperties.remove(TOPOLOGY_AWARE_PROPERTY_KEY);
    ybURL = processURLAndProperties();
  }

  public String processURLAndProperties() {
    String[] url_parts = this.originalUrl.split(PROPERTY_SEP);
    StringBuilder sb = new StringBuilder();
    String load_balancer_key = LOAD_BALANCE_PROPERTY_KEY + EQUALS;
    String topology_key = TOPOLOGY_AWARE_PROPERTY_KEY + EQUALS;
    for (String part : url_parts) {
      if (sb.length() == 0) {
        sb.append(part);
        continue;
      }
      if (part.startsWith(load_balancer_key)) {
        String[] lb_parts = part.split(EQUALS);
        String propValue = lb_parts[1];
        if (propValue.equalsIgnoreCase("true")) {
          this.hasLoadBalance = true;
        }
      } else if (part.startsWith(topology_key)) {
        String[] lb_parts = part.split(EQUALS);
        placements = lb_parts[1];
      } else {
        sb.append('&');
        sb.append(part);
      }
    }
    // Check properties bag also
    if (originalProperties != null) {
      if (originalProperties.containsKey(LOAD_BALANCE_PROPERTY_KEY)) {
        String propValue = originalProperties.getProperty(LOAD_BALANCE_PROPERTY_KEY);
        if (propValue.equals("true")) {
          hasLoadBalance = true;
        }
      }
      if (originalProperties.containsKey(TOPOLOGY_AWARE_PROPERTY_KEY)) {
        String propValue = originalProperties.getProperty(TOPOLOGY_AWARE_PROPERTY_KEY);
        placements = propValue;
      }
    }
    return sb.toString();
  }

  public String getOriginalURL() {
    return originalUrl;
  }

  public Properties getOriginalProperties() {
    return originalProperties;
  }

  public Properties getStrippedProperties() {
    return strippedProperties;
  }

  public boolean hasLoadBalance() {
    return hasLoadBalance;
  }

  public String getPlacements() {
    return placements;
  }

  public String getStrippedURL() {
    return ybURL;
  }

  public ClusterAwareLoadBalancer getAppropriateLoadBalancer() {
    if (!hasLoadBalance) {
      throw new IllegalStateException(
        "This method is expected to be called only when load-balance is true");
    }
    ClusterAwareLoadBalancer ld = null;
    if (placements == null) {
      // return base class conn manager.
      ld = CONNECTION_MANAGER_MAP.get(SIMPLE_LB);
      if (ld == null) {
        synchronized (CONNECTION_MANAGER_MAP) {
          ld = CONNECTION_MANAGER_MAP.get(SIMPLE_LB);
          if (ld == null) {
            ld = ClusterAwareLoadBalancer.getInstance();
            CONNECTION_MANAGER_MAP.put(SIMPLE_LB, ld);
          }
        }
      }
    } else {
      ld = CONNECTION_MANAGER_MAP.get(placements);
      if (ld == null) {
        synchronized (CONNECTION_MANAGER_MAP) {
          ld = CONNECTION_MANAGER_MAP.get(placements);
          if (ld == null) {
            ld = new TopologyAwareLoadBalancer(placements);
            CONNECTION_MANAGER_MAP.put(placements, ld);
          }
        }
      }
    }
    return ld;
  }
}
