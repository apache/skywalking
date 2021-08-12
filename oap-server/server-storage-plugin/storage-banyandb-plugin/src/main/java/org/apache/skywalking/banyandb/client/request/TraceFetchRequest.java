package org.apache.skywalking.banyandb.client.request;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

@Builder
@Getter
public class TraceFetchRequest {
    private final String traceId;

    @Singular
    private final List<String> projections;
}
