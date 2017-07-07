package org.skywalking.apm.agent.core.datacarrier.partition;

/**
 * Created by wusheng on 2016/10/25.
 */
public interface IDataPartitioner<T> {
    int partition(int total, T data);
}
