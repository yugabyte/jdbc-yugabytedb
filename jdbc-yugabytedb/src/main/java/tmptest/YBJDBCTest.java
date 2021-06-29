package tmptest;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import com.yugabyte.ysql.ClusterAwareLoadBalancer;
import org.postgresql.jdbc.PgConnection;
import com.yugabyte.ysql.LoadBalanceProperties;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Map;

public class YBJDBCTest {
  private static String which_host_query = "select inet_server_port( ), inet_server_addr( )";

  public static void main(String[] argv) {
    try {
      System.out.println("TEST");
      String javaHome = System.getenv("JAVA_HOME");
      System.out.println("JAVA_HOME = " + javaHome);
      String controlhost = "127.0.0.1";
      String controlport = "5433";
      String hostport = controlhost + ":" + controlport;
      // String multHostPort = "127.0.0.1:5433,127.0.0.2:5433,127.0.0.3:5433,127.0.0.4:5433,127.0.0.6:5433";
      String multHostPort = "127.0.0.1:5433,127.0.0.2:5433";
      // String controlurl = "jdbc:postgresql://" +  hostport+ "/yugabyte?user=yugabyte&password=yugabyte&load-balance=true";
      String controlurl = "jdbc:postgresql://" +  multHostPort + "/yugabyte?user=yugabyte&password=yugabyte&load-balance=true";
      testPreparedStatement(controlurl);
    } catch (SQLException throwables) {
      throwables.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void testPreparedStatement(
      String controlurl) throws SQLException, InterruptedException, IOException {
    Connection conn = DriverManager.getConnection(controlurl);
    ArrayList<Connection> listconn = new ArrayList<>();
    for (int i=0; i<10; i++) {
      listconn.add(DriverManager.getConnection(controlurl));
    }
    ClusterAwareLoadBalancer clb = LoadBalanceProperties.CONNECTION_MANAGER_MAP.get("simple");
    clb.printHostToConnMap();
    System.exit(0);
    Statement st = conn.createStatement();
    st.execute("DROP TABLE IF EXISTS SOCCER_PLAYERS");
    st.execute("CREATE TABLE SOCCER_PLAYERS(" +
      "id int not null primary key, name varchar(20) not null," +
      " age int not null, role varchar(20) not null)");
    st.executeUpdate("insert into SOCCER_PLAYERS values (1, 'messi', 30, 'forward')");
    st.executeUpdate("insert into SOCCER_PLAYERS values (2, 'ronaldo', 31, 'forward')");
    st.executeUpdate("insert into SOCCER_PLAYERS values (3, 'salah', 29, 'mid-fielder')");
    st.executeUpdate("insert into SOCCER_PLAYERS values (4, 'karim', 24, 'defender')");
    st.executeUpdate("insert into SOCCER_PLAYERS values (5, 'benzema', 24, 'goal-keeper')");
    PreparedStatement ps = conn.prepareStatement("update SOCCER_PLAYERS set role = ? where id = ?");
    ps.setString(1, "forward");
    ps.setInt(2, 3);
    int cnt = ps.executeUpdate();
    assert cnt == 1;
    System.out.println("UPDATE COUNT = " + cnt);
  }
}
