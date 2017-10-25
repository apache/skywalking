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
public class ApplicationDataDefine extends DataDefine {

    @Override protected int initialCapacity() {
        return 3;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute(ApplicationTable.COLUMN_ID, AttributeType.STRING, new NonOperation()));
        addAttribute(1, new Attribute(ApplicationTable.COLUMN_APPLICATION_CODE, AttributeType.STRING, new CoverOperation()));
        addAttribute(2, new Attribute(ApplicationTable.COLUMN_APPLICATION_ID, AttributeType.INTEGER, new CoverOperation()));
    }

    @Override public Object deserialize(RemoteData remoteData) {
        String id = remoteData.getDataStrings(0);
        String applicationCode = remoteData.getDataStrings(1);
        int applicationId = remoteData.getDataIntegers(0);
        return new Application(id, applicationCode, applicationId);
    }

    @Override public RemoteData serialize(Object object) {
        Application application = (Application)object;
        RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(application.getId());
        builder.addDataStrings(application.getApplicationCode());
        builder.addDataIntegers(application.getApplicationId());
        return builder.build();
    }

    public static class Application {
        private String id;
        private String applicationCode;
        private int applicationId;

        public Application(String id, String applicationCode, int applicationId) {
            this.id = id;
            this.applicationCode = applicationCode;
            this.applicationId = applicationId;
        }

        public String getId() {
            return id;
        }

        public String getApplicationCode() {
            return applicationCode;
        }

        public int getApplicationId() {
            return applicationId;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setApplicationCode(String applicationCode) {
            this.applicationCode = applicationCode;
        }

        public void setApplicationId(int applicationId) {
            this.applicationId = applicationId;
        }
    }
}
