package org.skywalking.apm.collector.ui.dao;

/**
 * @author pengys5
 */
public interface IInstanceDAO {
    Long lastHeartBeatTime();

    Long instanceLastHeartBeatTime(long applicationInstanceId);
}
