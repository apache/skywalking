package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import org.apache.skywalking.banyandb.v1.client.RowEntity;

@FunctionalInterface
public interface RowEntityMapper<T> {
    T map(RowEntity row);
}
