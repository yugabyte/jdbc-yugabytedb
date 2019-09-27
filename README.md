
# YSQL JDBC Driver
JDBC driver for YugaByte YSQL.
Based on the [Postgresql JDBC Driver](https://github.com/pgjdbc/pgjdbc).
This driver adds a `YBClusterAwareDataSource` that requires only an initial _contact point_ for the YugaByte cluster.
Then it discovers the rest of the nodes and automatically responds to nodes being started/added or stopped/removed.
Internally it maintains a connection pool for each node and it will choose a live node to get a connection.
Then, whenever the connection is closed it will be returned to the respective pool.

## Get the Driver

### From Maven

Add the following lines to your maven project.

```
<dependency>
    <groupId>com.yugabyte</groupId>
    <artifactId>ysql</artifactId>
    <version>42.2.7-yb-1</version>
</dependency>
```


### Build locally

1. Clone this repository.

    ```
    git clone https://github.com/yugabyte/ybjdbc.git && cd ybjdbc
    ```

2. Build and install into your local maven folder.

    ```
     mvn clean install -DskipTests
    ```

3. Finally, use it by adding the lines below to your project.

    ```xml
    <dependency>
        <groupId>com.yugabyte</groupId>
        <artifactId>ysql</artifactId>
        <version>42.2.7-yb-2-SNAPSHOT</version>
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
