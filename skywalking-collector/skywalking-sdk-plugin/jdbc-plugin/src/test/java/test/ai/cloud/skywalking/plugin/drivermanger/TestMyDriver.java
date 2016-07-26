package test.ai.cloud.skywalking.plugin.drivermanger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Created by xin on 16-6-14.
 */
public class TestMyDriver {
    public static void main(String[] args) throws ClassNotFoundException, SQLException {
        Class.forName("sample.ai.cloud.skywalking.plugin.drivermanger.MyDriver");
        String url = "jdbc:oracle:thin:@10.1.130.239:1521:ora";
        Connection con = DriverManager.getConnection(url, "edc_export", "edc_export");
        con.setAutoCommit(false);

        PreparedStatement p0 = con.prepareStatement("select 1 from dual where 1=?");
        p0.setInt(1, 1);
        p0.execute();
        con.commit();
        con.close();

    }
}
