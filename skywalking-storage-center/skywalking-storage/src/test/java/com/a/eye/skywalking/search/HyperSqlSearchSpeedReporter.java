package com.a.eye.skywalking.search;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by xin on 2016/11/1.
 */
public class HyperSqlSearchSpeedReporter {

    private static final long BASE_TIME_STAMP = 1477983548L;

    private static boolean useSingConnection = true;
    private static HikariDataSource hikariDataSource;
    private static Connection       connection;

    private static String CREATE_TABLE_SQL =
            "CREATE TABLE data_index\n" + "(\n" + "    id INT IDENTITY PRIMARY KEY NOT NULL,\n"
                    + "    startTime BIGINT NOT NULL\n" + ");\n";
    private static String CREATE_INDEX_SQL =
            "CREATE UNIQUE INDEX \"table_name_startTime_uindex\" ON data_index (startTime);";

    private static String INSERT_DATA_SQL = "INSERT INTO data_index(startTime) VALUES(?);";

    private static String QUERY_DATA_SQL =
            "SELECT startTime FROM  data_index WHERE startTime > ? ORDER BY startTime" + " ASC LIMIT 1";

    public static void initData() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:hsqldb:mem:test-speed");
        config.setUsername("root");
        config.setPassword("root");
        hikariDataSource = new HikariDataSource(config);
        connection = hikariDataSource.getConnection();

        PreparedStatement ps = connection.prepareStatement(CREATE_TABLE_SQL);
        ps.execute();
        ps = connection.prepareStatement(CREATE_INDEX_SQL);
        ps.execute();

        ps = connection.prepareStatement(INSERT_DATA_SQL);
        for (int i = 0; i < 3000; i++) {
            ps.setLong(1, BASE_TIME_STAMP + i * 1000 * 60 * 60);
            //System.out.print(BASE_TIME_STAMP + i * 1000 * 60 * 60);
            //System.out.print(",");
            ps.execute();
        }

        //System.out.println();

        ps.close();
    }

    public static long find(long element) throws SQLException {
        Connection connection = null;
        if (!useSingConnection) {
            connection = hikariDataSource.getConnection();
        }else{
            connection = HyperSqlSearchSpeedReporter.connection;
        }
        PreparedStatement preparedStatement = connection.prepareStatement(QUERY_DATA_SQL);
        preparedStatement.setLong(1, element);
        ResultSet resultSet = preparedStatement.executeQuery();
        resultSet.next();
        long result =  resultSet.getLong("startTime");
        preparedStatement.close();

        if (!useSingConnection){
            connection.close();
        }

        return result;
    }

    public static void main(String[] args) throws SQLException {
        initData();
        long startTime = System.nanoTime();

        for (long i = 0; i < 100000000L; i++) {
            find(1478323448L);
        }

        long totalTime = System.nanoTime() - startTime;
        System.out.println("total time : " + totalTime + " " + (totalTime * 1.0 / 100000000L));

    }
}
