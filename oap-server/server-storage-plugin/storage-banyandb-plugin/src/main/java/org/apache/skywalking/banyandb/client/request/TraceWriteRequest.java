package org.apache.skywalking.banyandb.client.request;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

@Builder
@Data
public class TraceWriteRequest {
    @Singular
    private final List<Object> fields;

    private byte[] dataBinary;

    private final long timestampSeconds;

    private int timestampNanos;

    private final String entityId;
}
