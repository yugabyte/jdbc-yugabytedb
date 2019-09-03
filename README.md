
# YSQL JDBC Driver
JDBC driver for YugaByte YSQL.
Based on the [Postgresql JDBC Driver](https://github.com/pgjdbc/pgjdbc).

## Get the Driver

### Build locally

```
 mvn clean install -DskipTests
```

Then you can use it by adding the following lines to your project:
```xml
<dependency>
    <groupId>com.yugabyte</groupId>
    <artifactId>ysql</artifactId>
    <version>42.2.7-yb-1-SNAPSHOT</version>
</dependency> 
```
