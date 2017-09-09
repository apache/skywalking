package org.skywalking.apm.agent.core.dictionary;

import io.netty.util.internal.ConcurrentSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.skywalking.apm.network.proto.ServiceNameCollection;
import org.skywalking.apm.network.proto.ServiceNameDiscoveryServiceGrpc;
import org.skywalking.apm.network.proto.ServiceNameElement;
import org.skywalking.apm.network.proto.ServiceNameMappingCollection;
import org.skywalking.apm.network.proto.ServiceNameMappingElement;

import static org.skywalking.apm.agent.core.conf.Config.Dictionary.OPERATION_NAME_BUFFER_SIZE;

/**
 * @author wusheng
 */
public enum OperationNameDictionary {
    INSTANCE;
    private Map<OperationNameKey, Integer> operationNameDictionary = new ConcurrentHashMap<OperationNameKey, Integer>();
    private Set<OperationNameKey> unRegisterOperationNames = new ConcurrentSet<OperationNameKey>();

    public PossibleFound findOrPrepare4Register(int applicationId, String operationName) {
        return find0(applicationId, operationName, true);
    }

    public PossibleFound findOnly(int applicationId, String operationName) {
        return find0(applicationId, operationName, false);
    }

    private PossibleFound find0(int applicationId, String operationName, boolean registerWhenNotFound) {
        if (operationName == null || operationName.length() == 0) {
            return new NotFound();
        }
        OperationNameKey key = new OperationNameKey(applicationId, operationName);
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
                    .build();
                builder.addElements(serviceNameElement);
            }
            ServiceNameMappingCollection serviceNameMappingCollection = serviceNameDiscoveryServiceBlockingStub.discovery(builder.build());
            if (serviceNameMappingCollection.getElementsCount() > 0) {
                for (ServiceNameMappingElement serviceNameMappingElement : serviceNameMappingCollection.getElementsList()) {
                    OperationNameKey key = new OperationNameKey(
                        serviceNameMappingElement.getElement().getApplicationId(),
                        serviceNameMappingElement.getElement().getServiceName());
                    unRegisterOperationNames.remove(key);
                    operationNameDictionary.put(key, serviceNameMappingElement.getServiceId());
                }
            }
        }
    }

    private class OperationNameKey {
        private int applicationId;
        private String operationName;

        public OperationNameKey(int applicationId, String operationName) {
            this.applicationId = applicationId;
            this.operationName = operationName;
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

            if (applicationId != key.applicationId)
                return false;
            return operationName.equals(key.operationName);
        }

        @Override public int hashCode() {
            int result = applicationId;
            result = 31 * result + operationName.hashCode();
            return result;
        }
    }
}
