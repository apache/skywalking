package org.apache.skywalking.banyandb.client.request;

import lombok.Getter;
import lombok.Singular;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@Getter
public class TraceFetchRequest extends HasMetadata {
    private final String traceId;

    @Singular
    private final List<String> projections;
}
