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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.postgresql;

import org.apache.skywalking.oap.server.core.storage.model.ModelInstaller;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCStorageProvider;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.postgresql.dao.PostgreSQLAggregationQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.postgresql.dao.PostgreSQLMetricsQueryDAO;

/**
 * PostgreSQL storage enhanced and came from MySQLStorageProvider to support PostgreSQL.
 */
public class PostgreSQLStorageProvider extends JDBCStorageProvider {
    @Override
    public String name() {
        return "postgresql";
    }

    @Override
    protected ModelInstaller createModelInstaller() {
        return new PostgreSQLTableInstaller(jdbcClient, getManager());
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        super.prepare();

        // Override service implementations.

        this.registerServiceImplementation(
            IMetricsQueryDAO.class,
            new PostgreSQLMetricsQueryDAO(jdbcClient, tableHelper));
        this.registerServiceImplementation(
            IAggregationQueryDAO.class,
            new PostgreSQLAggregationQueryDAO(jdbcClient, tableHelper));
    }
}
