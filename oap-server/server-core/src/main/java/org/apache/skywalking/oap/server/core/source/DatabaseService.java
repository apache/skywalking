package org.apache.skywalking.oap.server.core.source;

import lombok.Getter;
import lombok.Setter;

/**
 * @Author: Liu-Haoyang
 */
public class DatabaseService extends Source {

    @Override
    public Scope scope() {
        return Scope.DatabaseService;
    }

    @Override
    public String getEntityId() {
        return String.valueOf(id);
    }

    @Getter @Setter private int id;
    @Getter @Setter private String name;
    @Getter @Setter private String endpointName;
    @Getter @Setter private String statement;
    @Getter @Setter private int componentId;
    @Getter @Setter private int latency;
    @Getter @Setter private boolean status;
}
