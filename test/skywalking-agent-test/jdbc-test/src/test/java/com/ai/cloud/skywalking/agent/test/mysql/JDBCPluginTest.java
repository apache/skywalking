package com.ai.cloud.skywalking.agent.test.mysql;

import com.ai.skywalking.testframework.api.TraceTreeAssert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Created by xin on 16-6-15.
 */
public class JDBCPluginTest {

    @Test
    public void testMysqlJDBC() throws ClassNotFoundException, SQLException {
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
                {"0", "jdbc:mysql://127.0.0.1:3306/test?user=root&password=root(null)", "preaparedStatement.executeUpdate:select 1 from dual where 1=?"},
                {"0", "jdbc:mysql://127.0.0.1:3306/test?user=root&password=root(null)", "connection.commit"},
                {"0", "jdbc:mysql://127.0.0.1:3306/test?user=root&password=root(null)", "connection.close"},
        }, true);

        TraceTreeAssert.clearTraceData();
    }

    @Test
    public void testOracleJDBC() throws ClassNotFoundException, SQLException {
        Class.forName("oracle.jdbc.driver.OracleDriver");
        String url = "jdbc:oracle:thin:@10.1.130.239:1521:ora";
        Connection con = DriverManager.getConnection(url, "edc_export", "edc_export");
        con.setAutoCommit(false);

        PreparedStatement p0 = con.prepareStatement("select 1 from dual where 1=?");
        p0.setInt(1, 1);
        p0.execute();
        con.commit();
        con.close();
        TraceTreeAssert.assertEquals(new String[][]{
                {"0", "jdbc:oracle:thin:@10.1.130.239:1521:ora(edc_export)", "preaparedStatement.executeUpdate:select 1 from dual where 1=?"},
                {"0", "jdbc:oracle:thin:@10.1.130.239:1521:ora(edc_export)", "connection.commit"},
                {"0", "jdbc:oracle:thin:@10.1.130.239:1521:ora(edc_export)", "connection.close"},
        }, true);

        TraceTreeAssert.clearTraceData();
    }
}
