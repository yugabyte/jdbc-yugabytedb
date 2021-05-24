package org.postgresql.jdbc.yugabyte;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class LoadBalanceProperties {
  /* Placement info to ConnectionManager Mapping. For simple load-balance SIMPLE_LB
   * to be used as KEY and for targeted placements, the targeted property value
   */
  private static final String SIMPLE_LB = "simple";
  public static Map<String, ClusterAwareConnectionManager> CONNECTION_MANAGER_MAP = new HashMap<>();
  private static final String LOAD_BALANCE_PROPERTY_KEY = "load-balance";
  private static final String PLACEMENTS_KEYWORD = "placements";
  private static final String PROPERTY_SEP = "&";
  private static final String PLACEMENT_VALUE_SEP = ",";
  private static final String PLACEMENT_KEY_SEP = ":";
  private static final String EQUALS = "=";

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
    ybURL = processURLAndProperties();
  }

  public String processURLAndProperties() {
    String[] url_parts = this.originalUrl.split(PROPERTY_SEP);
    StringBuilder sb = new StringBuilder();
    String load_balancer_key = LOAD_BALANCE_PROPERTY_KEY + EQUALS;
    for (String part : url_parts) {
      if (sb.length() == 0) {
        sb.append(part);
        continue;
      }
      if (part.startsWith(load_balancer_key)) {
        String load_balancer_with_placements = load_balancer_key + PLACEMENTS_KEYWORD + PLACEMENT_KEY_SEP;
        if (part.startsWith(load_balancer_with_placements)) {
          String[] placement_parts = part.split(PLACEMENT_KEY_SEP);
          if (placement_parts == null || placement_parts.length != 2) {
            throw new IllegalArgumentException("load balance part malformed: " + part);
          }
          if (this.placements == null) {
            this.placements = placement_parts[1];
            this.hasLoadBalance = true;
          } else {
            throw new IllegalArgumentException("placement load balance property can be specified only once");
          }
        } else {
          String[] lb_parts = part.split(EQUALS);
          if (lb_parts == null || lb_parts.length != 2) {
            throw new IllegalArgumentException("load balance part malformed: " + part);
          }
          processSimpleLoadBalanceProp(lb_parts[1]);
        }
      } else {
        sb.append('&');
        sb.append(part);
      }
    }
    // Check properties bag also
    if (placements == null && originalProperties != null && originalProperties.containsKey(LOAD_BALANCE_PROPERTY_KEY)) {
      String propValue = originalProperties.getProperty(LOAD_BALANCE_PROPERTY_KEY);
      if (propValue.startsWith(PLACEMENTS_KEYWORD)) {
        if (placements != null) {
          throw new IllegalArgumentException("load balance property already specified in url");
        }
        String[] parts = propValue.split(":");
        if (parts == null || parts.length != 2) {
          throw new IllegalArgumentException("load balance placement value in property bag malformed");
        }
        hasLoadBalance = true;
        placements = parts[1];
      } else {
        processSimpleLoadBalanceProp(propValue);
      }
    }
    return sb.toString();
  }

  private void processSimpleLoadBalanceProp(String propValue) {
    if (propValue.equalsIgnoreCase("true")) {
      this.hasLoadBalance = true;
    } else if (!propValue.equalsIgnoreCase("false")) {
      throw new IllegalArgumentException(
        "load balance part without placement only expects true or false as values"
      );
    }
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

  public ClusterAwareConnectionManager getAppropriateConnectionManager() {
    if (!hasLoadBalance) {
      throw new IllegalStateException("This method is expected to be called only when load-balance is true");
    }
    ClusterAwareConnectionManager cacm = null;
    if (placements == null) {
      // return base class conn manager.
      cacm = CONNECTION_MANAGER_MAP.get(SIMPLE_LB);
      if (cacm == null) {
        synchronized (CONNECTION_MANAGER_MAP) {
          cacm = CONNECTION_MANAGER_MAP.get(SIMPLE_LB);
          if (cacm == null) {
            cacm = ClusterAwareConnectionManager.getInstance();
            CONNECTION_MANAGER_MAP.put(SIMPLE_LB, cacm);
          }
        }
      }
    } else {
      cacm = CONNECTION_MANAGER_MAP.get(placements);
      if (cacm == null) {
        synchronized (CONNECTION_MANAGER_MAP) {
          cacm = CONNECTION_MANAGER_MAP.get(placements);
          if (cacm == null) {
            cacm = new TargetedServersConnectionManager(placements);
            CONNECTION_MANAGER_MAP.put(placements, cacm);
          }
        }
      }
    }
    return cacm;
  }
}
