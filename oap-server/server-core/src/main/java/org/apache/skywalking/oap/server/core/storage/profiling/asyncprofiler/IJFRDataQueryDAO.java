package org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler;

import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.JFRProfilingDataRecord;
import org.apache.skywalking.oap.server.library.module.Service;

import java.io.IOException;
import java.util.List;

public interface IJFRDataQueryDAO extends Service {
    /**
     * get jfr data record
     *
     * @param taskId taskId
     * @param instanceIds instances of successfully uploaded file and parsed
     * @param eventType jfr eventType
     * @return record list
     */
    List<JFRProfilingDataRecord> getByTaskIdAndInstancesAndEvent(final String taskId, List<String> instanceIds, final String eventType) throws IOException;
}
