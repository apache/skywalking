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

package org.apache.skywalking.apm.agent.core.dictionary;

import io.netty.util.internal.ConcurrentSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.skywalking.apm.network.proto.ServiceNameCollection;
import org.apache.skywalking.apm.network.proto.ServiceNameDiscoveryServiceGrpc;
import org.apache.skywalking.apm.network.proto.ServiceNameElement;
import org.apache.skywalking.apm.network.proto.ServiceNameMappingCollection;
import org.apache.skywalking.apm.network.proto.ServiceNameMappingElement;
import org.apache.skywalking.apm.network.proto.SpanType;

import static org.apache.skywalking.apm.agent.core.conf.Config.Dictionary.OPERATION_NAME_BUFFER_SIZE;

/**
 * @author wusheng
 */
public enum OperationNameDictionary {
    INSTANCE;
    private Map<OperationNameKey, Integer> operationNameDictionary = new ConcurrentHashMap<OperationNameKey, Integer>();
    private Set<OperationNameKey> unRegisterOperationNames = new ConcurrentSet<OperationNameKey>();

    public PossibleFound findOrPrepare4Register(int applicationId, String operationName,
        boolean isEntry, boolean isExit) {
        return find0(applicationId, operationName, isEntry, isExit, true);
    }

    public PossibleFound findOnly(int applicationId, String operationName) {
        return find0(applicationId, operationName, false, false, false);
    }

    private PossibleFound find0(int applicationId, String operationName,
        boolean isEntry, boolean isExit, boolean registerWhenNotFound) {
        if (operationName == null || operationName.length() == 0) {
            return new NotFound();
        }
        OperationNameKey key = new OperationNameKey(applicationId, operationName, isEntry, isExit);
        Integer operationId = operationNameDictionary.get(key);
        if (operationId != null) {
            return new Found(operationId);
        } else {
            if (registerWhenNotFound &&
                operationNameDictionary.size() + unRegisterOperationNames.size() < OPERATION_NAME_BUFFER_SIZE) {
                unRegisterOperationNames.add(key);
            }
            return new NotFound();
        }
    }

    public void syncRemoteDictionary(
        ServiceNameDiscoveryServiceGrpc.ServiceNameDiscoveryServiceBlockingStub serviceNameDiscoveryServiceBlockingStub) {
        if (unRegisterOperationNames.size() > 0) {
            ServiceNameCollection.Builder builder = ServiceNameCollection.newBuilder();
            for (OperationNameKey operationNameKey : unRegisterOperationNames) {
                ServiceNameElement serviceNameElement = ServiceNameElement.newBuilder()
                    .setApplicationId(operationNameKey.getApplicationId())
                    .setServiceName(operationNameKey.getOperationName())
                    .setSrcSpanType(operationNameKey.getSpanType())
                    .build();
                builder.addElements(serviceNameElement);
            }
            ServiceNameMappingCollection serviceNameMappingCollection = serviceNameDiscoveryServiceBlockingStub.discovery(builder.build());
            if (serviceNameMappingCollection.getElementsCount() > 0) {
                for (ServiceNameMappingElement serviceNameMappingElement : serviceNameMappingCollection.getElementsList()) {
                    ServiceNameElement element = serviceNameMappingElement.getElement();
                    OperationNameKey key = new OperationNameKey(
                        element.getApplicationId(),
                        element.getServiceName(),
                        SpanType.Entry.equals(element.getSrcSpanType()),
                        SpanType.Exit.equals(element.getSrcSpanType()));
                    unRegisterOperationNames.remove(key);
                    operationNameDictionary.put(key, serviceNameMappingElement.getServiceId());
                }
            }
        }
    }

    private class OperationNameKey {
        private int applicationId;
        private String operationName;
        private boolean isEntry;
        private boolean isExit;

        public OperationNameKey(int applicationId, String operationName, boolean isEntry, boolean isExit) {
            this.applicationId = applicationId;
            this.operationName = operationName;
            this.isEntry = isEntry;
            this.isExit = isExit;
        }

        public int getApplicationId() {
            return applicationId;
        }

        public String getOperationName() {
            return operationName;
        }

        @Override public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            OperationNameKey key = (OperationNameKey)o;

            boolean isApplicationMatch = false;
            if (applicationId == key.applicationId) {
                isApplicationMatch = true;
            } else if (operationName.equals(key.operationName)) {
                isApplicationMatch = true;
            }
            return isApplicationMatch && isEntry == key.isEntry
                && isExit == key.isExit;
        }

        @Override public int hashCode() {
            int result = applicationId;
            result = 31 * result + operationName.hashCode();
            return result;
        }

        boolean isEntry() {
            return isEntry;
        }

        boolean isExit() {
            return isExit;
        }

        SpanType getSpanType() {
            if (isEntry) {
                return SpanType.Entry;
            } else if (isExit) {
                return SpanType.Exit;
            } else {
                return SpanType.Local;
            }
        }
    }
}
