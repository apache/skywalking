package org.skywalking.apm.commons.datacarrier.partition;

/**
 * use threadid % total to partition
 *
 * Created by wusheng on 2016/10/25.
 */
public class ProducerThreadPartitioner<T> implements IDataPartitioner<T> {
    @Override
    public int partition(int total, T data) {
        return (int)Thread.currentThread().getId() % total;
    }
}
