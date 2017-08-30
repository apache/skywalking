package org.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.ui.dao.IServiceEntryDAO;
import org.skywalking.apm.collector.ui.dao.IServiceReferenceDAO;

/**
 * @author pengys5
 */
public class ServiceTreeService {

    public JsonObject loadEntryService(int applicationId, String entryServiceName, long startTime, long endTime,
        int from, int size) {
        IServiceEntryDAO serviceEntryDAO = (IServiceEntryDAO)DAOContainer.INSTANCE.get(IServiceEntryDAO.class.getName());
        return serviceEntryDAO.load(applicationId, entryServiceName, startTime, endTime, from, size);
    }

    public JsonArray loadServiceTree(int entryServiceId, long startTime, long endTime) {
        IServiceReferenceDAO serviceReferenceDAO = (IServiceReferenceDAO)DAOContainer.INSTANCE.get(IServiceReferenceDAO.class.getName());
        return serviceReferenceDAO.load(entryServiceId, startTime, endTime);
    }
}