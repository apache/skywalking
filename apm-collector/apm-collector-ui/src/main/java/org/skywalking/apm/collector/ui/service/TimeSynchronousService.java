package org.skywalking.apm.collector.ui.service;

import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.ui.dao.IInstanceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class TimeSynchronousService {

    private final Logger logger = LoggerFactory.getLogger(SegmentTopService.class);

    public Long allInstanceLastTime() {
        IInstanceDAO instanceDAO = (IInstanceDAO)DAOContainer.INSTANCE.get(IInstanceDAO.class.getName());
        return instanceDAO.lastHeartBeatTime();
    }

    public Long instanceLastTime(int applicationInstanceId) {
        IInstanceDAO instanceDAO = (IInstanceDAO)DAOContainer.INSTANCE.get(IInstanceDAO.class.getName());
        return instanceDAO.instanceLastHeartBeatTime(applicationInstanceId);
    }
}
