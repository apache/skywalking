package org.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.ui.dao.IInstanceDAO;

/**
 * @author pengys5
 */
public class ApplicationService {

    public JsonArray getApplications(long startTime, long endTime) {
        IInstanceDAO instanceDAO = (IInstanceDAO)DAOContainer.INSTANCE.get(IInstanceDAO.class.getName());
        return instanceDAO.getApplications(startTime, endTime);
    }
}
