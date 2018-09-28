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

package org.apache.skywalking.oap.server.core.analysis.generated.serviceinstancejvmmemory;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.worker.IndicatorProcess;
import org.apache.skywalking.oap.server.core.analysis.indicator.expression.*;
import org.apache.skywalking.oap.server.core.source.*;

/**
 * This class is auto generated. Please don't change this class manually.
 *
 * @author Observability Analysis Language code generator
 */
public class ServiceInstanceJVMMemoryDispatcher implements SourceDispatcher<ServiceInstanceJVMMemory> {

    @Override public void dispatch(ServiceInstanceJVMMemory source) {
        doInstanceJvmMemoryHeap(source);
        doInstanceJvmMemoryNoheap(source);
        doInstanceJvmMemoryHeapMax(source);
        doInstanceJvmMemoryNoheapMax(source);
    }

    private void doInstanceJvmMemoryHeap(ServiceInstanceJVMMemory source) {
        InstanceJvmMemoryHeapIndicator indicator = new InstanceJvmMemoryHeapIndicator();

        if (!new EqualMatch().setLeft(source.isHeapStatus()).setRight(true).match()) {
            return;
        }

        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.setServiceInstanceId(source.getServiceInstanceId());
        indicator.combine(source.getUsed(), 1);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doInstanceJvmMemoryNoheap(ServiceInstanceJVMMemory source) {
        InstanceJvmMemoryNoheapIndicator indicator = new InstanceJvmMemoryNoheapIndicator();

        if (!new EqualMatch().setLeft(source.isHeapStatus()).setRight(false).match()) {
            return;
        }

        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.setServiceInstanceId(source.getServiceInstanceId());
        indicator.combine(source.getUsed(), 1);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doInstanceJvmMemoryHeapMax(ServiceInstanceJVMMemory source) {
        InstanceJvmMemoryHeapMaxIndicator indicator = new InstanceJvmMemoryHeapMaxIndicator();

        if (!new EqualMatch().setLeft(source.isHeapStatus()).setRight(true).match()) {
            return;
        }

        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.setServiceInstanceId(source.getServiceInstanceId());
        indicator.combine(source.getMax(), 1);
        IndicatorProcess.INSTANCE.in(indicator);
    }
    private void doInstanceJvmMemoryNoheapMax(ServiceInstanceJVMMemory source) {
        InstanceJvmMemoryNoheapMaxIndicator indicator = new InstanceJvmMemoryNoheapMaxIndicator();

        if (!new EqualMatch().setLeft(source.isHeapStatus()).setRight(false).match()) {
            return;
        }

        indicator.setTimeBucket(source.getTimeBucket());
        indicator.setEntityId(source.getEntityId());
        indicator.setServiceInstanceId(source.getServiceInstanceId());
        indicator.combine(source.getMax(), 1);
        IndicatorProcess.INSTANCE.in(indicator);
    }
}
