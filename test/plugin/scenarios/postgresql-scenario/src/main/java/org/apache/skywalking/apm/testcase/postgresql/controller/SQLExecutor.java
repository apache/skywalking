package org.apache.skywalking.apm.testcase.postgresql.controller;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.postgresql.core.BaseConnection;

public class SQLExecutor {
    private Connection connection;

    public SQLExecutor(PostgresqlConfig postgresqlConfig) throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            //
        }
        connection = DriverManager.getConnection(postgresqlConfig.getUrl(), postgresqlConfig.getUserName(), postgresqlConfig.getPassword());
    }

    public void createTable(String sql) throws SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.execute();
    }

    public void insertData(String sql, String id, String value) throws SQLException {
        CallableStatement preparedStatement = connection.prepareCall(sql);
        preparedStatement.setString(1, id);
        preparedStatement.setString(2, value);
        preparedStatement.execute();
    }

    public void dropTable(String sql) throws SQLException {
        Statement preparedStatement = connection.createStatement();
        preparedStatement.execute(sql);
        closeConnection();
    }

    public void closeConnection() throws SQLException {
        if (this.connection != null) {
            this.connection.close();
        }
    }

    public void queryData(String sql) throws SQLException {
        ResultSet resultSet = ((BaseConnection)connection).execSQLQuery(sql);
        resultSet.next();
        System.out.println(resultSet.getString("id"));

    }
}
