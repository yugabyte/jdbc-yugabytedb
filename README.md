
# YSQL JDBC Driver
JDBC driver for YugaByte YSQL.
Based on the [Postgresql JDBC Driver](https://github.com/pgjdbc/pgjdbc).

## Get the Driver

### Use from Maven

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
