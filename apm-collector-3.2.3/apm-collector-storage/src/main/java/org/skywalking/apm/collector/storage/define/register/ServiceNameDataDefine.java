/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.define.register;

import org.skywalking.apm.collector.core.stream.operate.CoverOperation;
import org.skywalking.apm.collector.core.stream.operate.NonOperation;
import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.storage.define.Attribute;
import org.skywalking.apm.collector.storage.define.AttributeType;
import org.skywalking.apm.collector.storage.define.DataDefine;

/**
 * @author peng-yongsheng
 */
public class ServiceNameDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 4;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(ServiceNameTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(ServiceNameTable.COLUMN_SERVICE_NAME, AttributeType.STRING, new CoverOperation()));
        addAttribute(2, new Attribute(ServiceNameTable.COLUMN_APPLICATION_ID, AttributeType.INTEGER, new CoverOperation()));
        addAttribute(3, new Attribute(ServiceNameTable.COLUMN_SERVICE_ID, AttributeType.INTEGER, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        String id = remoteData.getDataStrings(0);
        String serviceName = remoteData.getDataStrings(1);
        int applicationId = remoteData.getDataIntegers(0);
        int serviceId = remoteData.getDataIntegers(1);
        return new ServiceName(id, serviceName, applicationId, serviceId);
    }

    @Override public RemoteData serialize(Object object) {
        ServiceName serviceName = (ServiceName)object;
        RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(serviceName.getId());
        builder.addDataStrings(serviceName.getServiceName());
        builder.addDataIntegers(serviceName.getApplicationId());
        builder.addDataIntegers(serviceName.getServiceId());
        return builder.build();
    }

    public static class ServiceName {
        private String id;
        private String serviceName;
        private int applicationId;
        private int serviceId;

        public ServiceName(String id, String serviceName, int applicationId, int serviceId) {
            this.id = id;
            this.serviceName = serviceName;
            this.applicationId = applicationId;
            this.serviceId = serviceId;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public int getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(int applicationId) {
            this.applicationId = applicationId;
        }

        public int getServiceId() {
            return serviceId;
        }

        public void setServiceId(int serviceId) {
            this.serviceId = serviceId;
        }
    }
}
