package org.skywalking.apm.collector.agentregister.instance;

/**
 * @author pengys5
 */
public interface IInstanceDAO {
    int getInstanceId(String agentUUID);
}
