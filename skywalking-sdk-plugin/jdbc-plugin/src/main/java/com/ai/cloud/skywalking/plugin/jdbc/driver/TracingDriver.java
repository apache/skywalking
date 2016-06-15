package com.ai.cloud.skywalking.plugin.jdbc.driver;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.logging.log4j.LogManager;

import com.ai.cloud.skywalking.conf.AuthDesc;

public class TracingDriver implements Driver {
	private static org.apache.logging.log4j.Logger logger = LogManager.getLogger(TracingDriver.class);

	private static final String TRACING_SIGN = "tracing:";

	private static boolean isOpenCompensation = false;

	public static final void registerDriver() {
        try {
            DriverManager.registerDriver(new TracingDriver());
            isOpenCompensation = true;
        } catch (SQLException e) {
        	logger.error("register TracingDriver failure.", e);
        }
    }

	private Driver realDriver;

	private Driver chooseDriver(String url) throws IllegalAccessException,
			InstantiationException, ClassNotFoundException, SQLException {
		if (realDriver == null) {
			this.realDriver = DriverChooser.choose(url);
		}
		return realDriver;
	}

	private String getRealUrl(String url) throws SQLException {
		if (!isOpenCompensation) {
			return url;
		} else {
			if (url.toLowerCase().startsWith(TRACING_SIGN)) {
				return url.substring(TRACING_SIGN.length());
			} else {
				throw new SQLException(
						"tracing jdbc url must start with 'tracing:'");
			}
		}
	}

	public java.sql.Connection connect(String url, Properties info)
			throws SQLException {
		java.sql.Connection conn = null;

		try {
			conn = chooseDriver(getRealUrl(url)).connect(getRealUrl(url), info);
		} catch (Exception e) {
			throw new SQLException(e);
		}

		if (!AuthDesc.isAuth()) {
			return conn;
		} else {
			return new SWConnection(getRealUrl(url), info, conn);
		}
	}

	public boolean acceptsURL(String url) throws SQLException {
		Driver driver = null;
		try {
			driver = chooseDriver(getRealUrl(url));
		} catch (Exception e) {
			throw new SQLException(e);
		}
		return driver.acceptsURL(getRealUrl(url));
	}

	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
			throws SQLException {
		Driver driver = null;
		try {
			driver = chooseDriver(getRealUrl(url));
		} catch (Exception e) {
			throw new SQLException(e);
		}
		return driver.getPropertyInfo(getRealUrl(url), info);
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
