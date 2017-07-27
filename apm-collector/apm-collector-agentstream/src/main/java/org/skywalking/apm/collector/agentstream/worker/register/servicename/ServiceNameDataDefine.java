package org.skywalking.apm.collector.agentstream.worker.register.servicename;

import org.skywalking.apm.collector.remote.grpc.proto.RemoteData;
import org.skywalking.apm.collector.stream.worker.impl.data.Attribute;
import org.skywalking.apm.collector.stream.worker.impl.data.AttributeType;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.CoverOperation;
import org.skywalking.apm.collector.stream.worker.impl.data.operate.NonOperation;

/**
 * @author pengys5
 */
public class ServiceNameDataDefine extends DataDefine {

    public static final int DEFINE_ID = 103;

    @Override public int defineId() {
        return DEFINE_ID;
    }

    @Override protected int initialCapacity() {
        return 4;
    }

    @Override protected void attributeDefine() {
        addAttribute(0, new Attribute("id", AttributeType.STRING, new NonOperation()));
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
