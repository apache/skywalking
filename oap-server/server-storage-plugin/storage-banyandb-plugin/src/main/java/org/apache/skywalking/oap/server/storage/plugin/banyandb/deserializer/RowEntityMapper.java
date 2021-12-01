package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import org.apache.skywalking.banyandb.v1.client.RowEntity;

import java.util.List;

public interface RowEntityMapper<T> {
    List<String> searchableProjection();

    List<String> dataProjection();

    T map(RowEntity row);
}
