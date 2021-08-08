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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBBatchDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBStorageDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.dao.BanyanDBTraceQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.H2StorageProvider;

public class BanyanDBStorageProvider extends H2StorageProvider {
    private BanyanDBStorageConfig config;

    public BanyanDBStorageProvider() {
        this(new BanyanDBStorageConfig());
    }

    protected BanyanDBStorageProvider(BanyanDBStorageConfig config) {
        super(config);
        this.config = config;
    }

    @Override
    public String name() {
        return "banyandb";
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        // first call super to initialize with H2
        super.prepare();
        // prepare Trace related modules
        // 1) IBatchDAO
        // 2) StorageDao
        // 3) ITraceQueryDAO
        this.registerServiceImplementation(IBatchDAO.class, new BanyanDBBatchDAO(this.getH2Client()));
        this.registerServiceImplementation(StorageDAO.class, new BanyanDBStorageDAO(getManager(), this.getH2Client()));
        this.registerServiceImplementation(ITraceQueryDAO.class, new BanyanDBTraceQueryDAO(this.config));
    }
}
