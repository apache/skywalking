package test.ai.cloud.skywalking.plugin.mysql;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.Test;

import com.ai.cloud.skywalking.plugin.TracingBootstrap;

public class TestMySQLDriver {
	@Test
	public void testsql() throws IllegalAccessException,
			IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException, ClassNotFoundException {
		TracingBootstrap
				.main(new String[] { "test.ai.cloud.skywalking.plugin.mysql.TestMySQLDriver" });
	}

	public static void main(String[] args) throws ClassNotFoundException, SQLException, InterruptedException {
		Class.forName("com.mysql.jdbc.Driver");
		String url="jdbc:mysql://10.1.228.202:31316/test?user=devrdbusr21&password=devrdbusr21";

		Connection con = DriverManager.getConnection(url);
		Statement state = con.createStatement();
		con.setAutoCommit(false);
		state.execute("select 1 from dual");
		
		PreparedStatement p0 = con.prepareStatement("select 1 from dual where 1=?");
		p0.setInt(1, 1);
		p0.execute();
		con.commit();
		con.close();
		
		Thread.sleep(5* 1000);
	}
}
