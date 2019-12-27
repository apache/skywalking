package org.apache.skywalking.oap.server.core.storage.profile;

import org.apache.skywalking.oap.server.core.profile.ThreadMonitorTaskNoneStream;
import org.apache.skywalking.oap.server.core.storage.DAO;

import java.util.List;

/**
 * process all thread monitor task query
 *
 * @author MrPro
 */
public interface IThreadMonitorTaskQueryDAO extends DAO {

    /**
     * check has task is started at time range
     * @return if has task already created
     */
    List<ThreadMonitorTaskNoneStream> getHasTaskAlreadyCreate(final int serviceId, final long taskStartTime, final long taskEndTime);

}
