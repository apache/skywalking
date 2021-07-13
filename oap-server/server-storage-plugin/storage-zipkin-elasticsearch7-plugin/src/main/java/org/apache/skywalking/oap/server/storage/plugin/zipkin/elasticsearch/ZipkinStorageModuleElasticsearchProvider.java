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

package org.apache.skywalking.oap.server.storage.plugin.zipkin.elasticsearch;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.storage.query.ITraceQueryDAO;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.StorageModuleElasticsearch7Provider;

@Slf4j
public class ZipkinStorageModuleElasticsearchProvider extends StorageModuleElasticsearch7Provider {

    @Override
    public String name() {
        return "zipkin-elasticsearch7";
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {
        super.prepare();
        final ZipkinTraceQueryEs7DAO traceQueryEsDAO = new ZipkinTraceQueryEs7DAO(elasticSearch7Client);
        this.registerServiceImplementation(ITraceQueryDAO.class, traceQueryEsDAO);
    }

    @Override
    public String[] requiredModules() {
        return new String[] {CoreModule.NAME};
    }
}
