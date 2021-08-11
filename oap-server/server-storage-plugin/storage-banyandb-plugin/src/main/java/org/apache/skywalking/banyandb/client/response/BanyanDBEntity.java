package org.apache.skywalking.banyandb.client.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.Map;

@Builder
@Getter
public class BanyanDBEntity {
    @Singular
    private Map<String, Object> fields;

    private final String entityId;
    private byte[] binaryData;
    private final long timestampSeconds;
    private final int timestampNanoSeconds;
}
