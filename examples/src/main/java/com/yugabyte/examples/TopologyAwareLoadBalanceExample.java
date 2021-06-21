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
    Boolean verbose = args[0].equals("1");
    Boolean interactive = args[1].equals("1");
    String numConnections = "6";
    String controlHost = "127.0.0.1";
    String controlPort = "5433";

    controlUrl = "jdbc:postgresql://" + controlHost
      + ":" + controlPort + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=true&topology-keys=region1.zone1";

    System.out.println("Setting up the connection pool having 6 connections.......");

    testUsingHikariPool("topology_aware_load_balance", "region1.zone1", "region1.zone1",
      controlHost, controlPort, numConnections, verbose, interactive);
  }
}
