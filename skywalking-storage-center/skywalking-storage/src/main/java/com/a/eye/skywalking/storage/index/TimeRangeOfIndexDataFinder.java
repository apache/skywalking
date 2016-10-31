package com.a.eye.skywalking.storage.index;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Created by xin on 2016/10/31.
 */
public class TimeRangeOfIndexDataFinder {
    private static       Logger logger           = LogManager.getLogger(TimeRangeOfIndexDataFinder.class);
    private static final String SQL_JDBC_URL     = "jdbc:hsqldb:mem:TIME_RANGE";
    private static final String SQL_CREATE_TABLE =
            "CREATE TABLE Time_Range("
                    + "id INT PRIMARY KEY NOT NULL IDENTITY,"
                    + "fileName VARCHAR(32) NOT NULL," + "    "
                    + "startTime INT NOT NULL,"
                    + "endTime INT NOT NULL\n"
                    + ");";
    private static final String SQL_CREATE_INDEX =
            "CREATE INDEX \"Time_Range_startTime_endTime_index\" ON Time_Range (startTime, endTime);";
    private static final String SQL_INSERT_DATA  = "INSERT INTO Time_Range(fileName,startTime,endTime) VALUES(?,?,?);";
    private static final String SQL_SELECT_DATA  = "SELECT fileName FROM Time_Range WHERE startTime < ? AND endTime >?";



    private static final TimeRangeOfIndexDataFinder finder = new TimeRangeOfIndexDataFinder();

    private HikariDataSource hikariDataSource;

    private TimeRangeOfIndexDataFinder() {
        initDataSource();
        initTableAndIndex();
        initTimeRangeData();
    }

    private void initDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(SQL_JDBC_URL);
        // TODO: 2016/10/31 初始化数据源参数
        hikariDataSource = new HikariDataSource(config);
    }

    private void initTableAndIndex() {
        Connection connection = null;
        try {
            connection = hikariDataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(SQL_CREATE_TABLE);
            ps.execute();

            ps = connection.prepareStatement(SQL_CREATE_INDEX);
            ps.execute();
        } catch (SQLException e) {
            logger.error("Failed to ");
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.error("Failed to close connection when init time range data");
                }
            }
        }
    }

    private void initTimeRangeData() {
        List<TimeRangeOfIndexData> timeRangeOfIndexDataList = TimeRangeOfIndexDataFile.INSTANCE().read();
        Connection connection = null;
        int currentCount = 0;
        try {
            connection = hikariDataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(SQL_INSERT_DATA);
            for (TimeRangeOfIndexData data : timeRangeOfIndexDataList) {
                ps.setString(1, data.getIndexDataFileName());
                ps.setLong(2, data.getStartTime());
                ps.setLong(3, data.getEndTime());

                if ((currentCount++) % 100 == 0) {
                    ps.executeBatch();
                }
            }

            ps.executeBatch();
        } catch (SQLException e) {
            logger.error("Failed to init time range data");
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.error("Failed to close connection when init time range data");
                }
            }
        }

    }


    public static TimeRangeOfIndexDataFinder INSTANCE() {
        return finder;
    }

    public TimeRangeOfIndexData find(long timestamp) {
        Connection connection = null;
        try {
            connection = hikariDataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(SQL_SELECT_DATA);
            ps.setLong(1, timestamp);
            ps.setLong(2, timestamp);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return new TimeRangeOfIndexData(rs.getString("fileName"));
        } catch (SQLException e) {
            return null;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.error("Failed to close connection when find data.");
                }
            }
        }
    }
}
