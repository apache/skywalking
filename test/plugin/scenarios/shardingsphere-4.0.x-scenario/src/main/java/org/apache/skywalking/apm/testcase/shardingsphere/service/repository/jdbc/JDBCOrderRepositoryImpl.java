/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.testcase.shardingsphere.service.repository.jdbc;

import org.apache.skywalking.apm.testcase.shardingsphere.service.api.entity.Order;
import org.apache.skywalking.apm.testcase.shardingsphere.service.api.repository.OrderRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

public final class JDBCOrderRepositoryImpl implements OrderRepository {

    private final DataSource dataSource;
    
    private final Connection connection;

    public JDBCOrderRepositoryImpl(final DataSource dataSource) throws SQLException {
        this.dataSource = dataSource;
        this.connection = dataSource.getConnection();
    }

    @Override
    public void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS t_order (order_id BIGINT NOT NULL AUTO_INCREMENT, user_id INT NOT NULL, status VARCHAR(50), PRIMARY KEY (order_id))";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (final SQLException ignored) {
        }
    }

    @Override
    public void dropTable() {
        String sql = "DROP TABLE t_order";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (final SQLException ignored) {
        }
    }

    @Override
    public void truncateTable() {
        String sql = "TRUNCATE TABLE t_order";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (final SQLException ignored) {
        }
    }

    @Override
    public Long insert(final Order order) {
        String sql = "INSERT INTO t_order (user_id, status) VALUES (?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preparedStatement.setInt(1, order.getUserId());
            preparedStatement.setString(2, order.getStatus());
            preparedStatement.executeUpdate();
            try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                if (resultSet.next()) {
                    order.setOrderId(resultSet.getLong(1));
                }
            }
        } catch (final SQLException ignored) {
        }
        return order.getOrderId();
    }

    @Override
    public void delete(final Long orderId) {
        String sql = "DELETE FROM t_order WHERE order_id=?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setLong(1, orderId);
            preparedStatement.executeUpdate();
        } catch (final SQLException ignored) {
        }
    }

    @Override
    public List<Order> selectAll() {
        String sql = "SELECT * FROM t_order";
        return getOrders(sql);
    }

    @Override
    public List<Order> selectRange() {
        String sql = "SELECT * FROM t_order WHERE order_id BETWEEN 200000000000000000 AND 400000000000000000";
        return getOrders(sql);
    }

    private List<Order> getOrders(final String sql) {
        List<Order> result = new LinkedList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql); ResultSet resultSet = preparedStatement
            .executeQuery()) {
            while (resultSet.next()) {
                Order order = new Order();
                order.setOrderId(resultSet.getLong(1));
                order.setUserId(resultSet.getInt(2));
                order.setStatus(resultSet.getString(3));
                result.add(order);
            }
        } catch (final SQLException ignored) {
            ignored.printStackTrace();
        }
        return result;
    }
}
