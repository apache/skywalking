package org.apache.skywalking.oap.server.core.source;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;

/**
 * @Author: Liu-Haoyang
 */
public class DatabaseServiceRelation extends Source {
    @Override public Scope scope() {
        return Scope.DatabaseServiceRelation;
    }

    @Override public String getEntityId() {
        return String.valueOf(sourceServiceId) + Const.ID_SPLIT + String.valueOf(destServiceId) + Const.ID_SPLIT + String.valueOf(componentId);
    }

    @Getter @Setter private int sourceServiceId;
    @Getter @Setter private String sourceServiceName;
    @Getter @Setter private int destServiceId;
    @Getter @Setter private String destServiceName;
    @Getter @Setter private int componentId;
    @Getter @Setter private int latency;
    @Getter @Setter private boolean status;
}
