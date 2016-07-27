package test.ai.cloud.skywalking.plugin.oracle;

import com.ai.cloud.skywalking.plugin.TracingBootstrap;
import com.ai.skywalking.testframework.api.RequestSpanAssert;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Created by xin on 16-6-15.
 */
public class OracleJDBCTest {

    @Test
    public void testOracleJDBC() throws InvocationTargetException,
            NoSuchMethodException, ClassNotFoundException,
            IllegalAccessException {
        TracingBootstrap
                .main(new String[]{"test.ai.cloud.skywalking.plugin.oracle.OracleJDBCTest"});
    }

    public static void main(String[] args) throws ClassNotFoundException,
            SQLException, InterruptedException {
        Class.forName("oracle.jdbc.driver.OracleDriver");
        String url = "jdbc:oracle:thin:@10.1.130.239:1521:ora";
        Connection con = DriverManager.getConnection(url, "edc_export", "edc_export");
        con.setAutoCommit(false);

        PreparedStatement p0 = con.prepareStatement("select 1 from dual where 1=?");
        p0.setInt(1, 1);
        p0.execute();
        con.commit();
        con.close();
        RequestSpanAssert.assertEquals(new String[][]{
                {"0", "jdbc:oracle:thin:@10.1.130.239:1521:ora(edc_export)", "preaparedStatement.executeUpdate:select 1 from dual where 1=?"},
                {"0", "jdbc:oracle:thin:@10.1.130.239:1521:ora(edc_export)", "connection.commit"},
                {"0", "jdbc:oracle:thin:@10.1.130.239:1521:ora(edc_export)", "connection.close"},
        }, true);

    }
}
