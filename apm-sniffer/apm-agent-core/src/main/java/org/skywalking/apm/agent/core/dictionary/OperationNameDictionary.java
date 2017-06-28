package org.skywalking.apm.agent.core.dictionary;

import io.netty.util.internal.ConcurrentSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wusheng
 */
public enum OperationNameDictionary {
    INSTANCE;
    private Map<OperationNameKey, Integer> operationNameDictionary = new ConcurrentHashMap<OperationNameKey, Integer>();
    private Set<OperationNameKey> unRegisterOperationName = new ConcurrentSet<OperationNameKey>();

    public PossibleFound find(int applicationId, String operationName) {
        OperationNameKey key = new OperationNameKey(applicationId, operationName);
        Integer operationId = operationNameDictionary.get(key);
        if (operationId != null) {
            return new Found(applicationId);
        } else {
            unRegisterOperationName.add(key);
            return new NotFound();
        }
    }

    private class OperationNameKey {
        private int applicationId;
        private String operationName;

        public OperationNameKey(int applicationId, String operationName) {
            this.applicationId = applicationId;
            this.operationName = operationName;
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
