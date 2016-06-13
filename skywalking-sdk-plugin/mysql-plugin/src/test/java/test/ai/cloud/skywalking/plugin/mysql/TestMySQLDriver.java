package test.ai.cloud.skywalking.plugin.mysql;

import com.ai.cloud.skywalking.plugin.TracingBootstrap;
import com.ai.skywalking.testframework.api.TraceTreeAssert;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class TestMySQLDriver {
    @Test
    public void testsql() throws IllegalAccessException,
            IllegalArgumentException, InvocationTargetException,
            NoSuchMethodException, SecurityException, ClassNotFoundException {
        TracingBootstrap
                .main(new String[]{"test.ai.cloud.skywalking.plugin.mysql.TestMySQLDriver"});
    }

    public static void main(String[] args) throws ClassNotFoundException, SQLException, InterruptedException {
        Class.forName("com.mysql.jdbc.Driver");
        String url = "jdbc:mysql://127.0.0.1:3306/test?user=root&password=root";
        Connection con = DriverManager.getConnection(url);
        con.setAutoCommit(false);

        PreparedStatement p0 = con.prepareStatement("select 1 from dual where 1=?");
        p0.setInt(1, 1);
        p0.execute();
        con.commit();
        con.close();
        TraceTreeAssert.assertEquals(new String[][]{
                {"0", "jdbc:mysql://127.0.0.1:3306/test?user=root&password=root(root)", "preaparedStatement.executeUpdate:select 1 from dual where 1=?"},
                {"0", "jdbc:mysql://127.0.0.1:3306/test?user=root&password=root(root)", "connection.commit"},
                {"0.0", "jdbc:mysql://127.0.0.1:3306/test?user=root&password=root(root)", "connection.rollback"},
                {"0", "jdbc:mysql://127.0.0.1:3306/test?user=root&password=root(root)", "connection.close"},
        }, true);
    }
}
