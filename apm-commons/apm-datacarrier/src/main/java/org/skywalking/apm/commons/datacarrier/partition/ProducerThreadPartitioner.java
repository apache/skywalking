package org.skywalking.apm.commons.datacarrier.partition;

/**
 * use threadid % total to partition
 *
 * Created by wusheng on 2016/10/25.
 */
public class ProducerThreadPartitioner<T> implements IDataPartitioner<T> {
    private int retryTime = 3;

    public ProducerThreadPartitioner() {
    }

    public ProducerThreadPartitioner(int retryTime) {
        this.retryTime = retryTime;
    }

    @Override
    public int partition(int total, T data) {
        return (int)Thread.currentThread().getId() % total;
    }

    @Override
    public int maxRetryCount() {
        return 1;
    }
}
