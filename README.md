
# YugabyteDB JDBC Driver
This is a distributed JDBC driver for YugabyteDB SQL. This driver is based on the [PostgreSQL JDBC Driver](https://github.com/pgjdbc/pgjdbc).

## Features

This JDBC driver has the following features:

### Cluster Awareness to eliminate need for a load balancer

This driver adds a `YBClusterAwareDataSource` that requires only an initial _contact point_ for the YugabyteDB cluster, using which it discovers the rest of the nodes. Additionally, it automatically learns about the nodes being started/added or stopped/removed. Internally a connection pool is maintained for each node. A random live node is chosen to connect to the cluster and execute a statement. it will choose a live node to get a connection. When the connection is closed by the application, it is returned to the respective pool.

### Topology Awareness to enable geo-distributed apps

> **NOTE:** This feature is still in progress.

### Shard awareness for high performance

> **NOTE:** This feature is still in the design phase.



## Get the Driver

### From Maven

Add the following lines to your maven project.

```
<dependency>
  <groupId>com.yugabyte</groupId>
  <artifactId>jdbc-yugabytedb</artifactId>
  <version>42.2.7-yb-3</version>
</dependency>
```


### Build locally

1. Clone this repository.

    ```
    git clone https://github.com/yugabyte/jdbc-yugabytedb.git && cd jdbc-yugabytedb
    ```

2. Build and install into your local maven folder.

    ```
     mvn clean install -DskipTests
    ```

3. Finally, use it by adding the lines below to your project.

    ```xml
    <dependency>
        <groupId>com.yugabyte</groupId>
        <artifactId>jdbc-yugabytedb</artifactId>
        <version>42.2.7-yb-3-SNAPSHOT</version>
    </dependency> 
    ```

## Use the Driver

- Create the DataSource by passing an initial contact point
    ```
    String jdbcUrl = "jdbc:postgresql://127.0.0.1:5433/yugabyte";
    YBClusterAwareDataSource ds = new YBClusterAwareDataSource(jdbcUrl);
    ```

- Use like a regular (pooling) DataSource
    ```
    // Using try-with-resources to auto-close the connection when done.
    try (Connection connection = ds.getConnection()) {
        // Use the connection as usual.
    } catch (java.sql.SQLException e) {
        // Handle/Report error.
    }
    ```
