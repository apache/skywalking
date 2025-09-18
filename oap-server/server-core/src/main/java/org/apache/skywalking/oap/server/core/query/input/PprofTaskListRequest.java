package org.apache.skywalking.oap.server.core.query.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PprofTaskListRequest {
    private String serviceId;
    private Duration queryDuration;
    private Integer limit;
}
