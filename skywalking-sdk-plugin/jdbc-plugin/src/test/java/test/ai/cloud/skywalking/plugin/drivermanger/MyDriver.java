package test.ai.cloud.skywalking.plugin.drivermanger;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

public class MyDriver implements Driver {

    private Driver realDriver;

    static {
        try {
            DriverManager.registerDriver(new MyDriver());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public MyDriver() {

    }

    public Driver buildOracleDriver() {
        if (realDriver == null)
            realDriver = new oracle.jdbc.OracleDriver();

        return realDriver;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        System.out.println("MyDriver connect(String url, Properties info)");
        return buildOracleDriver().connect(url, info);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        System.out.println("MyDriver acceptsURL(String url)");
        return buildOracleDriver().acceptsURL(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        System.out.println("MyDriver getPropertyInfo(String url, Properties info)");
        return buildOracleDriver().getPropertyInfo(url, info);
    }

    @Override
    public int getMajorVersion() {
        System.out.println("MyDriver getMajorVersion()");
        return buildOracleDriver().getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        System.out.println("MyDriver getMinorVersion()");
        return buildOracleDriver().getMajorVersion();
    }

    @Override
    public boolean jdbcCompliant() {
        System.out.println("MyDriver jdbcCompliant()");
        return realDriver.jdbcCompliant();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        System.out.println("MyDriver getParentLogger()");
        return realDriver.getParentLogger();
    }
}
