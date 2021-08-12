package org.apache.skywalking.banyandb.client.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = true)
public class TraceWriteRequest extends HasMetadata {
    @Singular
    private final List<Object> fields;

    private byte[] dataBinary;

    private final long timestampSeconds;

    private int timestampNanos;

    private final String entityId;
}
