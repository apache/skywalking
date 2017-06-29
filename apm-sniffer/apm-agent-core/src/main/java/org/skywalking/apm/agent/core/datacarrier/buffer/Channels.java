package org.skywalking.apm.agent.core.datacarrier.buffer;

import org.skywalking.apm.agent.core.datacarrier.partition.IDataPartitioner;

/**
 * Channels of Buffer
 * It contais all buffer data which belongs to this channel.
 * It supports several strategy when buffer is full. The Default is BLOCKING
 * <p>
 * Created by wusheng on 2016/10/25.
 */
public class Channels<T> {
    private final Buffer<T>[] bufferChannels;
    private IDataPartitioner<T> dataPartitioner;

    public Channels(int channelSize, int bufferSize, IDataPartitioner<T> partitioner, BufferStrategy strategy) {
        this.dataPartitioner = partitioner;
        bufferChannels = new Buffer[channelSize];
        for (int i = 0; i < channelSize; i++) {
            bufferChannels[i] = new Buffer<T>(bufferSize, strategy);
        }
    }

    public boolean save(T data) {
        int index = dataPartitioner.partition(bufferChannels.length, data);
        return bufferChannels[index].save(data);
    }

    public void setPartitioner(IDataPartitioner<T> dataPartitioner) {
        this.dataPartitioner = dataPartitioner;
    }

    /**
     * override the strategy at runtime. Notice, this will override several channels one by one. So, when running
     * setStrategy, each channel may use different BufferStrategy
     *
     * @param strategy
     */
    public void setStrategy(BufferStrategy strategy) {
        for (Buffer<T> buffer : bufferChannels) {
            buffer.setStrategy(strategy);
        }
    }

    /**
     * get channelSize
     *
     * @return
     */
    public int getChannelSize() {
        return this.bufferChannels.length;
    }

    public Buffer<T> getBuffer(int index) {
        return this.bufferChannels[index];
    }
}
