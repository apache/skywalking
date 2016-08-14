package test.a.eye.cloud.skywalking.plugin.mysql;

import com.a.eye.skywalking.plugin.PluginException;
import com.a.eye.skywalking.plugin.TracingBootstrap;
import com.a.eye.skywalking.testframework.api.RequestSpanAssert;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class MysqlJDBCTest {

    @Test
    public void testMySqlJDBC() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, PluginException {
        TracingBootstrap.main(new String[] {"MysqlJDBCTest"});
    }

    public static void main(String[] args) throws ClassNotFoundException, SQLException, InterruptedException {
        Class.forName("com.mysql.jdbc.Driver");
        String url = "tracing:jdbc:mysql://127.0.0.1:3306/test?user=root&password=root";
        Connection con = DriverManager.getConnection(url);
        con.setAutoCommit(false);

        PreparedStatement p0 = con.prepareStatement("select 1 from dual where 1=?");
        p0.setInt(1, 1);
        p0.execute();
        con.commit();
        con.close();
        RequestSpanAssert.assertEquals(
                new String[][] {{"0", "jdbc:mysql://127.0.0.1:3306/test?user=root&password=root(null)", "preaparedStatement.executeUpdate:select 1 from dual where 1=?"},
                        {"0", "jdbc:mysql://127.0.0.1:3306/test?user=root&password=root(null)", "connection.commit"},
                        {"0", "jdbc:mysql://127.0.0.1:3306/test?user=root&password=root(null)", "connection.close"},}, true);

    }

}
