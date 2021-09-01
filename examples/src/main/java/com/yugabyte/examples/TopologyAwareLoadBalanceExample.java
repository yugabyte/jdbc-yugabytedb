package com.yugabyte.examples;

import com.yugabyte.ysql.LoadBalanceProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public class TopologyAwareLoadBalanceExample extends UniformLoadBalanceExample{

  public static void main(String[] args) {
    int argsLen = args.length;
    Boolean verbose = argsLen > 0 ? args[0].equals("1") : Boolean.FALSE;
    Boolean interactive = argsLen > 1 ? args[1].equals("1") : Boolean.FALSE;
    debugLogging = argsLen > 2 ? args[2].equals("1") : Boolean.FALSE;
    // Since it is just a demo app. Using some smaller values so that it can run
    // on laptop
    String numConnections = "6";
    String controlHost = "127.0.0.1";
    String controlPort = "5433";

    controlUrl = "jdbc:postgresql://" + controlHost
      + ":" + controlPort + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=true&topology-keys=cloud1.region1.zone1";
    if (debugLogging) {
      controlUrl = "jdbc:postgresql://" + controlHost
        + ":" + controlPort + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=true"
        + "&topology-keys=cloud1.region1.zone1&loggerLevel=debug";
    }
    System.out.println("Setting up the connection pool having 6 connections.......");

    testUsingHikariPool("topology_aware_load_balance", "cloud1.region1.zone1", "cloud1.region1.zone1",
      controlHost, controlPort, numConnections, verbose, interactive);
  }
}
