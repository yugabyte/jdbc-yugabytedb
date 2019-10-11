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
import com.datastax.driver.core.Host;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.pool.HikariPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.zaxxer.hikari.pool.HikariPool.*;

/**
 * Load-balancing policy for YugaByte SQL connection.
 * Based on the YCQL RoundRobinPolicy implementation.
 // TODO The load-balacing/cluster-manager code should be refactored away to be API-agnostic.
 */
public class YBConnectionLoadBalancingPolicy extends RoundRobinPolicy {
  private static final Logger LOGGER = LoggerFactory.getLogger(YBConnectionLoadBalancingPolicy.class);

  // Dummy keyspace needed to pass to the YCQL-specific functions.
  private static final String DUMMY_KEYSPACE = "system";

  // Dummy statement needed to pass to the YCQL-specific functions.
  private static final Statement DUMMY_STATEMENT = new SimpleStatement("SELECT * FROM system.local");

  private Properties poolProperties;

  private final ConcurrentMap<Host, HikariPool> pools;
  private Cluster cluster = null;

  YBConnectionLoadBalancingPolicy(Properties poolProperties) {
    this.poolProperties = poolProperties;
    this.pools = new ConcurrentHashMap<>();
  }

  public HikariPool getPool() throws IllegalStateException {
    Iterator<Host> hosts = newQueryPlan(DUMMY_KEYSPACE, DUMMY_STATEMENT);
    while (hosts.hasNext()) {
      Host host = hosts.next();
      HikariPool pool = pools.get(host);
      if (pool != null && pool.poolState == POOL_NORMAL) {
        if (pool.getTotalConnections() > 0) { // TODO -- is this needed?.
          return pool;
        }
      } else {
        renewPools();
      }
    }

    throw new IllegalStateException("Could not find any active connection pool. Are all nodes down?");
  }

  @Override
  public void onAdd(Host host) {
    super.onAdd(host);
    // Host added but not yet up -- nothing to do.
    if (host.isUp()) {
      onHostUp(host);
    }
  }

  @Override
  public void onDown(Host host) {
    super.onDown(host);
    // TODO - check if suspending the pool is better here.
    onHostDown(host);
  }

  @Override
  public void onRemove(Host host) {
    super.onRemove(host);
    onHostRemoved(host);
  }

  @Override
  public void onUp(Host host) {
    super.onUp(host);
    onHostUp(host);
  }

  public void init(Cluster cluster) {
    super.init(cluster, cluster.getMetadata().getAllHosts());
    this.cluster = cluster;
    renewPools();
  }

  private void renewPools() {
    if (cluster == null) {
      LOGGER.trace("Skipping renewing pools");
      // Not initialized yet, nothing to do.
      return;
    }
    Set<Host> allHosts = cluster.getMetadata().getAllHosts();
    Set<Host> poolsHosts = pools.keySet();

    // Remove any entries from hosts that the cluster no longer knows about (node removed).
    Set<Host> unknownHosts = new HashSet<>();
    for (Host host : poolsHosts) {
      if (!allHosts.contains(host)) {
        LOGGER.debug("UnknownHost: " + hostToString(host));
        unknownHosts.add(host);
      }
    }

    for (Host host : unknownHosts) {
      HikariPool p = pools.remove(host);
      shutdownPool(p);
    }

    // Create/resume/suspend pools for the known nodes as needed.
    for (Host host : allHosts) {
      HikariPool pool = pools.get(host);
      if (host.isUp()) {
        if (pool != null && pool.poolState == POOL_NORMAL) {
          // If we have an existing, active pool we can just reuse it.
          continue;
        } else if (pool != null && pool.poolState == POOL_SUSPENDED) {
          LOGGER.debug("Resuming pool for host: " + hostToString(host));
          // If pool is suspended (a down node just came back up) just resume it.
          pool.resumePool();
        } else {
          if (pool == null) {
            LOGGER.info("Initializing connection pool for YSQL host " + hostToString(host) + " (host up).");
            // If pool for this node is missing (new node) or shutdown, we need to create a new pool.
            String hostName = host.getAddress().getHostName();
            Properties properties = (Properties) poolProperties.clone();
            properties.setProperty("dataSource.serverName", hostName);
            HikariConfig config = new HikariConfig(properties);
            config.validate();
            HikariPool newPool =  new HikariPool(config);
            HikariPool existing = pools.putIfAbsent(host, newPool);
            if (existing != null) {
              LOGGER.warn("Already existing pool, shutting down new one");
              shutdownPool(newPool);
            }
          }
        }
      } else {
        if (pool != null && pool.poolState == POOL_NORMAL) {
          LOGGER.info("Removing connection pool for YSQL host: " + hostToString(host) + " (host down).");
          shutdownPool(pool);
        }
      }
    }
    LOGGER.debug(poolsToString());
  }

  private void onHostUp(Host host) {
    HikariPool pool = pools.get(host);
    if (pool != null && pool.poolState == POOL_NORMAL) {
      // If we have an existing, active pool nothing to do.
      return;
    }

    LOGGER.info("Initializing connection pool for YSQL host " + hostToString(host) + " (host up).");

    if (pool != null && pool.poolState == POOL_SUSPENDED) {
      LOGGER.trace("Resuming pool for host: " + hostToString(host));
      // If pool is suspended (a down node just came back up) just resume it.
      pool.resumePool();
    } else { // pool == null || pool.poolState == POOL_SHUTDOWN
      LOGGER.trace("Initializing new pool for host: " + hostToString(host));
      // If pool for this node is missing (new node) or shutdown, we need to create a new pool.
      String hostName = host.getAddress().getHostName();
      Properties properties = (Properties) poolProperties.clone();
      properties.setProperty("dataSource.serverName", hostName);
      HikariConfig config = new HikariConfig(properties);
      config.validate();
      HikariPool newPool = new HikariPool(config);
      HikariPool existing = pools.putIfAbsent(host, newPool);
      if (existing != null) {
        LOGGER.warn("Already existing pool, shutting down new one");
        shutdownPool(newPool);
      }
    }

    LOGGER.debug(poolsToString());
  }

  private void onHostDown(Host host) {
    HikariPool pool = pools.remove(host);
    if (pool == null) {
      LOGGER.trace("Cannot remove connection pool for removed host: " + hostToString(host) + ": No associated pool found.");
      return;
    }
    LOGGER.info("Removing connection pool for YSQL host: " + hostToString(host) + " (host down).");
    shutdownPool(pool);
    LOGGER.debug(poolsToString());
  }

  private void onHostRemoved(Host host) {
    HikariPool pool = pools.remove(host);
    if (pool != null) {
      LOGGER.info("Removing connection pool for YSQL host: " + hostToString(host) + " (host removed).");
      shutdownPool(pool);
      LOGGER.debug(poolsToString());
    }
  }

  private String poolsToString() {
    StringBuilder sb = new StringBuilder();
    sb.append("-------- Pools --------\n");
    for (Map.Entry<Host, HikariPool> entry : pools.entrySet()) {
      sb.append(hostToString(entry.getKey()));
      sb.append(" : ").append(entry.getValue().getTotalConnections()).append("\n");
    }
    sb.append("-----------------------");
    return sb.toString();
  }

  private String hostToString(Host host) {
    return host.getSocketAddress().getAddress().toString();
  }

  private void shutdownPool(HikariPool pool) {
    if (pool != null) {
      try {
        pool.shutdown();
      } catch (InterruptedException e) {
        LOGGER.warn(pool.toString() + " - Interrupted during closing: " + e.toString());
        Thread.currentThread().interrupt();
      }
    }
  }

}
