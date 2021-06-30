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

package org.apache.skywalking.apm.testcase.shardingsphere.service.api.service;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.testcase.shardingsphere.service.api.entity.Order;
import org.apache.skywalking.apm.testcase.shardingsphere.service.api.entity.OrderItem;
import org.apache.skywalking.apm.testcase.shardingsphere.service.api.repository.OrderItemRepository;
import org.apache.skywalking.apm.testcase.shardingsphere.service.api.repository.OrderRepository;

public abstract class CommonServiceImpl implements CommonService {

    @Override
    public void initEnvironment() {
        getOrderRepository().createTableIfNotExists();
        getOrderItemRepository().createTableIfNotExists();
        getOrderRepository().truncateTable();
        getOrderItemRepository().truncateTable();
        insertData();
    }

    @Override
    public void cleanEnvironment() {
        getOrderRepository().dropTable();
        getOrderItemRepository().dropTable();
    }

    @Override
    public void processSuccess(final boolean isRangeSharding) {
        printData(isRangeSharding);
    }

    @Override
    public void processFailure() {
        insertData();
        throw new RuntimeException("Exception occur for transaction test.");
    }

    private List<Long> insertData() {
        List<Long> result = new ArrayList<>(10);
        for (int i = 1; i <= 10; i++) {
            Order order = newOrder();
            order.setUserId(i);
            order.setStatus("INSERT_TEST");
            getOrderRepository().insert(order);
            OrderItem item = newOrderItem();
            item.setOrderId(order.getOrderId());
            item.setUserId(i);
            item.setStatus("INSERT_TEST");
            getOrderItemRepository().insert(item);
            result.add(order.getOrderId());
        }
        return result;
    }

    private void deleteData(final List<Long> orderIds) {
        for (Long each : orderIds) {
            getOrderRepository().delete(each);
            getOrderItemRepository().delete(each);
        }
    }

    @Override
    public void printData(final boolean isRangeSharding) {
        if (isRangeSharding) {
            printDataRange();
        } else {
            printDataAll();
        }
    }

    private void printDataRange() {
        for (Object each : getOrderRepository().selectRange()) {
        }
        for (Object each : getOrderItemRepository().selectRange()) {
        }
    }

    private void printDataAll() {
        for (Object each : getOrderRepository().selectAll()) {
        }
    }

    protected abstract OrderRepository getOrderRepository();

    protected abstract OrderItemRepository getOrderItemRepository();

    protected abstract Order newOrder();

    protected abstract OrderItem newOrderItem();
}
