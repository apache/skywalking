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

    private String entityId;
    private byte[] binaryData;
    private long timestampSeconds;
    private long timestampNanoSeconds;
}
