// Copyright (c) YugaByte, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the License
// is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
// or implied.  See the License for the specific language governing permissions and limitations
// under the License.
//

package com.yugabyte.ysql;

import com.datastax.driver.core.Cluster;
import com.zaxxer.hikari.pool.HikariPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

class YBClusterManager implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(YBClusterManager.class);

  private YBConnectionLoadBalancingPolicy policy;
  private Cluster cluster;

  YBClusterManager(String host, Properties poolProperties) {
    policy = new YBConnectionLoadBalancingPolicy(poolProperties);
    cluster = Cluster.builder().addContactPoint(host)
                               .withLoadBalancingPolicy(policy)
                               .build();
    policy.init(cluster);
  }

  HikariPool getPool() throws IllegalStateException {
    return policy.getPool();
  }

  @Override
  public void close() {
    policy.close();
    cluster.close();
  }
}
