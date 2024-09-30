package org.apache.skywalking.oap.server.core.query.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AsyncProfilerTaskListRequest {
    private String serviceId;
    private Long startTime;
    private Long endTime;
    private Integer limit;
}
