package org.postgresql.jdbc.yugabyte;

import org.postgresql.PGProperty;
import org.postgresql.ds.PGSimpleDataSource;

public class YBSimpleDataSource extends PGSimpleDataSource {
  public void setLoadBalance(String value) {
    PGProperty.YB_LOAD_BALANCE.set(properties, value);
  }

  public void setTopologyKeys(String value) {
    PGProperty.YB_TOPOLOGY_KEYS.set(properties, value);
  }
}
