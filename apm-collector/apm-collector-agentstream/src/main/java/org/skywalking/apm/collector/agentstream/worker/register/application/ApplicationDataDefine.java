package org.skywalking.apm.collector.agentstream.worker.register.application;

import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.storage.table.register.ApplicationTable;
import org.skywalking.apm.collector.stream.worker.impl.data.Attribute;
import org.skywalking.apm.collector.stream.worker.impl.data.AttributeType;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.CoverOperation;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.NonOperation;

/**
 * @author pengys5
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
