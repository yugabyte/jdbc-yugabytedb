package com.yugabyte.ysql;

import org.postgresql.PGProperty;
import org.postgresql.ds.PGSimpleDataSource;

public class YBClusterAwareDataSource extends PGSimpleDataSource {
  private String additionalEndPoints;

  public void setLoadBalance(String value) {
    PGProperty.YB_LOAD_BALANCE.set(properties, value);
  }

  public void setTopologyKeys(String value) {
    PGProperty.YB_TOPOLOGY_KEYS.set(properties, value);
  }

  // additionalEndpoints
  public void setAdditionalEndpoints(String value) {
    this.additionalEndPoints = value;
  }

  public String getAdditionalEndPoints() {
    return additionalEndPoints;
  }
}
