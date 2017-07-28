package org.skywalking.apm.commons.datacarrier.partition;

import org.skywalking.apm.commons.datacarrier.buffer.BufferStrategy;

/**
 * Created by wusheng on 2016/10/25.
 */
public interface IDataPartitioner<T> {
    int partition(int total, T data);

    /**
     * @return an integer represents how many times should retry when {@link BufferStrategy#IF_POSSIBLE}.
     *
     * Less or equal 1, means not support retry.
     */
    int maxRetryCount();
}
