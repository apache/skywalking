package com.ai.cloud.skywalking.plugin.jdbc;

import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

import com.ai.cloud.skywalking.conf.AuthDesc;

public abstract class TracingDriver implements Driver {
	private static final String TRACING_SIGN = "tracing:";

	private Driver realDriver;

	protected TracingDriver() {
		this.realDriver = this.registerTracingDriver();
	}

	protected abstract Driver registerTracingDriver();

	public java.sql.Connection connect(String url, Properties info) throws SQLException {
		java.sql.Connection conn = this.realDriver.connect(this.getRealUrl(url), info);
		if(!AuthDesc.isAuth()){
			return conn;
		}else{
			return new SWConnection(url, info, conn);
		}
	}

	public boolean acceptsURL(String url) throws SQLException {
		return this.realDriver.acceptsURL(this.getRealUrl(url));
	}

	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
			throws SQLException {
		return this.realDriver.getPropertyInfo(this.getRealUrl(url), info);
	}

	private String getRealUrl(String url) throws SQLException {
		if(url.toLowerCase().startsWith(TRACING_SIGN)){
			return url.substring(TRACING_SIGN.length());
		}else{
			throw new SQLException("tracing jdbc url must start with 'tracing:'");
		}
	}

	public int getMajorVersion() {
		return safeIntParse("1");
	}

	public int getMinorVersion() {
		return safeIntParse("0");
	}

	public boolean jdbcCompliant() {
		return false;
	}

	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return null;
	}

	private static int safeIntParse(String intAsString) {
		try {
			return Integer.parseInt(intAsString);
		} catch (NumberFormatException nfe) {
		}
		return 0;
	}
}
