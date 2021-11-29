package org.apache.skywalking.oap.server.storage.plugin.banyandb.converter;

import org.apache.skywalking.banyandb.v1.client.RowEntity;

public interface RowEntityMapper<T> {
    T map(RowEntity row);
}
