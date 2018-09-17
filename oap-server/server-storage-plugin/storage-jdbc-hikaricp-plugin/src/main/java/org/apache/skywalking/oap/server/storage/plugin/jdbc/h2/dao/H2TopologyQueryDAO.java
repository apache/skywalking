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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.oap.server.core.query.entity.Call;
import org.apache.skywalking.oap.server.core.query.entity.Step;
import org.apache.skywalking.oap.server.core.source.ServiceComponent;
import org.apache.skywalking.oap.server.core.source.ServiceMapping;
import org.apache.skywalking.oap.server.core.storage.query.ITopologyQueryDAO;

/**
 * @author wusheng
 */
public class H2TopologyQueryDAO implements ITopologyQueryDAO {
    @Override public List<Call> loadSpecifiedServerSideServiceRelations(Step step, long startTB, long endTB,
        List<Integer> serviceIds) throws IOException {
        return null;
    }

    @Override public List<Call> loadSpecifiedClientSideServiceRelations(Step step, long startTB, long endTB,
        List<Integer> serviceIds) throws IOException {
        return null;
    }

    @Override public List<Call> loadServerSideServiceRelations(Step step, long startTB, long endTB) throws IOException {
        return null;
    }

    @Override public List<Call> loadClientSideServiceRelations(Step step, long startTB, long endTB) throws IOException {
        return null;
    }

    @Override public List<ServiceMapping> loadServiceMappings(Step step, long startTB, long endTB) throws IOException {
        return null;
    }

    @Override
    public List<ServiceComponent> loadServiceComponents(Step step, long startTB, long endTB) throws IOException {
        return null;
    }

    @Override public List<Call> loadSpecifiedDestOfServerSideEndpointRelations(Step step, long startTB, long endTB,
        int destEndpointId) throws IOException {
        return null;
    }

    @Override public List<Call> loadSpecifiedSourceOfClientSideEndpointRelations(Step step, long startTB, long endTB,
        int sourceEndpointId) throws IOException {
        return null;
    }
}
