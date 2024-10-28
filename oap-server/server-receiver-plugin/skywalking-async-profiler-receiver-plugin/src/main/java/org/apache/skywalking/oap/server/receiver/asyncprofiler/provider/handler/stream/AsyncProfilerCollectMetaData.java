package org.apache.skywalking.oap.server.receiver.asyncprofiler.provider.handler.stream;

import lombok.Builder;
import lombok.Data;
import org.apache.skywalking.apm.network.language.asyncprofiler.v10.AsyncProfilerCollectType;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;

@Data
@Builder
public class AsyncProfilerCollectMetaData {
    private AsyncProfilerTask task;
    private String serviceId;
    private String instanceId;
    private int contentSize;
    private AsyncProfilerCollectType type;
}
