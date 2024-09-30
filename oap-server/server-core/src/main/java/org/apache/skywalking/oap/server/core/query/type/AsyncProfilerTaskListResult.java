package org.apache.skywalking.oap.server.core.query.type;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AsyncProfilerTaskListResult {
    private String errorReason;
    private List<AsyncProfilerTask> tasks;
}
