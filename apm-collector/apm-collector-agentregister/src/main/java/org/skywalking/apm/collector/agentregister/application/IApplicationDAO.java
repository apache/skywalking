package org.skywalking.apm.collector.agentregister.application;

/**
 * @author pengys5
 */
public interface IApplicationDAO {
    int getApplicationId(String applicationCode);
}
