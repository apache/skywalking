package test.ai.cloud.skywalking.plugin.h2;

import com.ai.cloud.skywalking.plugin.TracingBootstrap;
import com.ai.skywalking.testframework.api.RequestSpanAssert;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class H2JDBCTest {
    @Test
    public void testMySqlJDBC() throws InvocationTargetException, NoSuchMethodException, ClassNotFoundException, IllegalAccessException {
        TracingBootstrap.main(new String[]{H2JDBCTest.class.getName()});
    }

    public static void main(String[] args) throws ClassNotFoundException, SQLException, InterruptedException {
        Class.forName("org.h2.Driver");
        String url = "jdbc:h2:" + H2JDBCTest.class.getResource("/") + "sample.db";
        Connection con = DriverManager.getConnection(url);
        con.setAutoCommit(false);

        PreparedStatement p0 = con.prepareStatement("select 1 from dual where 1=?");
        p0.setInt(1, 1);
        p0.execute();
        con.commit();
        con.close();
        RequestSpanAssert.assertEquals(
                new String[][]{{"0", "jdbc:h2:" +H2JDBCTest.class.getResource("/") + "sample.db" + "(null)", "preaparedStatement.executeUpdate:select 1 from dual where 1=?"},
                        {"0", "jdbc:h2:" +H2JDBCTest.class.getResource("/") + "sample.db" + "(null)", "connection.commit"},
                        {"0", "jdbc:h2:" +H2JDBCTest.class.getResource("/") + "sample.db" + "(null)", "connection.close"},}, true);

    }
}
