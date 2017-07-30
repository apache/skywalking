package org.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.ui.dao.INodeComponentDAO;
import org.skywalking.apm.collector.ui.dao.INodeMappingDAO;
import org.skywalking.apm.collector.ui.dao.INodeRefSumDAO;
import org.skywalking.apm.collector.ui.dao.INodeReferenceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class TraceDagService {

    private final Logger logger = LoggerFactory.getLogger(TraceDagService.class);

    public JsonObject load(long startTime, long endTime, String timeBucketType) {
        logger.debug("startTime: {}, endTime: {}, timeSliceType: {}", startTime, endTime, timeBucketType);
        INodeComponentDAO nodeComponentDAO = (INodeComponentDAO)DAOContainer.INSTANCE.get(INodeComponentDAO.class.getName());
        JsonArray nodeComponentArray = nodeComponentDAO.load(startTime, endTime);

        INodeMappingDAO nodeMappingDAO = (INodeMappingDAO)DAOContainer.INSTANCE.get(INodeMappingDAO.class.getName());
        JsonArray nodeMappingArray = nodeMappingDAO.load(startTime, endTime);

        INodeReferenceDAO nodeReferenceDAO = (INodeReferenceDAO)DAOContainer.INSTANCE.get(INodeReferenceDAO.class.getName());
        JsonArray nodeRefArray = nodeReferenceDAO.load(startTime, endTime);

        INodeRefSumDAO nodeRefSumDAO = (INodeRefSumDAO)DAOContainer.INSTANCE.get(INodeRefSumDAO.class.getName());
        JsonArray nodeRefSumArray = nodeRefSumDAO.load(startTime, endTime);

        TraceDagDataBuilder builder = new TraceDagDataBuilder();
        JsonObject traceDag = builder.build(nodeComponentArray, nodeMappingArray, nodeRefArray, nodeRefSumArray);

        return traceDag;
    }
}
