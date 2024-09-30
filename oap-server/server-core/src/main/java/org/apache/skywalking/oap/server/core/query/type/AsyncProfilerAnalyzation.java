package org.apache.skywalking.oap.server.core.query.type;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AsyncProfilerAnalyzation {
    private String errorReason;
    private AsyncProfilerStackTree tree;
}
