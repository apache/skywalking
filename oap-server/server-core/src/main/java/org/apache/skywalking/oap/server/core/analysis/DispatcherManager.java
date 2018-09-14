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

package org.apache.skywalking.oap.server.core.analysis;

import java.util.*;
import org.apache.skywalking.oap.server.core.analysis.generated.all.AllDispatcher;
import org.apache.skywalking.oap.server.core.analysis.generated.endpoint.EndpointDispatcher;
import org.apache.skywalking.oap.server.core.analysis.generated.endpointrelation.EndpointRelationDispatcher;
import org.apache.skywalking.oap.server.core.analysis.generated.service.ServiceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.generated.serviceinstance.ServiceInstanceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.generated.serviceinstancejvmcpu.ServiceInstanceJVMCPUDispatcher;
import org.apache.skywalking.oap.server.core.analysis.generated.serviceinstancejvmgc.ServiceInstanceJVMGCDispatcher;
import org.apache.skywalking.oap.server.core.analysis.generated.serviceinstancejvmmemory.ServiceInstanceJVMMemoryDispatcher;
import org.apache.skywalking.oap.server.core.analysis.generated.serviceinstancejvmmemorypool.ServiceInstanceJVMMemoryPoolDispatcher;
import org.apache.skywalking.oap.server.core.analysis.generated.serviceinstancerelation.ServiceInstanceRelationDispatcher;
import org.apache.skywalking.oap.server.core.analysis.generated.servicerelation.ServiceRelationDispatcher;
import org.apache.skywalking.oap.server.core.analysis.manual.endpointrelation.EndpointCallRelationDispatcher;
import org.apache.skywalking.oap.server.core.analysis.manual.service.*;
import org.apache.skywalking.oap.server.core.analysis.manual.servicerelation.ServiceCallRelationDispatcher;
import org.apache.skywalking.oap.server.core.source.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class DispatcherManager {

    private static final Logger logger = LoggerFactory.getLogger(DispatcherManager.class);

    private Map<Scope, SourceDispatcher[]> dispatcherMap;

    public DispatcherManager() {
        this.dispatcherMap = new HashMap<>();

        this.dispatcherMap.put(Scope.All, new SourceDispatcher[] {new AllDispatcher()});

        this.dispatcherMap.put(Scope.Service, new SourceDispatcher[] {new ServiceDispatcher()});
        this.dispatcherMap.put(Scope.ServiceInstance, new SourceDispatcher[] {new ServiceInstanceDispatcher()});
        this.dispatcherMap.put(Scope.Endpoint, new SourceDispatcher[] {new EndpointDispatcher()});

        this.dispatcherMap.put(Scope.ServiceComponent, new SourceDispatcher[] {new ServiceComponentDispatcher()});
        this.dispatcherMap.put(Scope.ServiceMapping, new SourceDispatcher[] {new ServiceMappingDispatcher()});

        this.dispatcherMap.put(Scope.ServiceRelation, new SourceDispatcher[] {new ServiceRelationDispatcher(), new ServiceCallRelationDispatcher()});
        this.dispatcherMap.put(Scope.ServiceInstanceRelation, new SourceDispatcher[] {new ServiceInstanceRelationDispatcher()});
        this.dispatcherMap.put(Scope.EndpointRelation, new SourceDispatcher[] {new EndpointRelationDispatcher(), new EndpointCallRelationDispatcher()});

        this.dispatcherMap.put(Scope.ServiceInstanceJVMCPU, new SourceDispatcher[] {new ServiceInstanceJVMCPUDispatcher()});
        this.dispatcherMap.put(Scope.ServiceInstanceJVMGC, new SourceDispatcher[] {new ServiceInstanceJVMGCDispatcher()});
        this.dispatcherMap.put(Scope.ServiceInstanceJVMMemory, new SourceDispatcher[] {new ServiceInstanceJVMMemoryDispatcher()});
        this.dispatcherMap.put(Scope.ServiceInstanceJVMMemoryPool, new SourceDispatcher[] {new ServiceInstanceJVMMemoryPoolDispatcher()});
    }

    public void forward(Source source) {
        for (SourceDispatcher dispatcher : dispatcherMap.get(source.scope())) {
            dispatcher.dispatch(source);
        }
    }
}
