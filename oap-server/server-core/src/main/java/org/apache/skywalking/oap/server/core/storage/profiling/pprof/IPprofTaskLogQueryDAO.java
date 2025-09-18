package org.apache.skywalking.oap.server.core.storage.profiling.pprof;

import org.apache.skywalking.oap.server.core.query.PprofTaskLog;
import org.apache.skywalking.oap.server.core.storage.DAO;

import java.io.IOException;
import java.util.List;

public interface IPprofTaskLogQueryDAO extends DAO {
    /**
     * search all task log list in appoint task id
     */
    List<PprofTaskLog> getTaskLogList() throws IOException;
}
