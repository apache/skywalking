package com.ai.cloud.skywalking.plugin.jdbc.mysql;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.ai.cloud.skywalking.plugin.jdbc.TracingDriver;

public class MySQLTracingDriver extends TracingDriver {
	static {
		try {
			DriverManager.registerDriver(new MySQLTracingDriver());
		} catch (SQLException e) {
			throw new RuntimeException("register "
					+ MySQLTracingDriver.class.getName() + " driver failure.");
		}
	}

	/**
	 * 继承自TracingDriver，返回真实的Driver
	 */
	@Override
	protected Driver registerTracingDriver() {
		try {
			return new com.mysql.jdbc.Driver();
		} catch (SQLException e) {
			throw new RuntimeException("create com.mysql.jdbc.Driver failure.");
		}
	}

}
