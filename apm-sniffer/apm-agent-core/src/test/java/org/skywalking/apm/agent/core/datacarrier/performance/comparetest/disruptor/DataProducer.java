package org.skywalking.apm.agent.core.datacarrier.performance.comparetest.disruptor;

import com.lmax.disruptor.RingBuffer;

/**
 * Created by wusheng on 2016/11/24.
 */
public class DataProducer {
    private final RingBuffer<Data> ringBuffer;

    public DataProducer(RingBuffer<Data> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    public void onData(Data bb) {
        long sequence = ringBuffer.next();  // Grab the next sequence
        try {
            Data event = ringBuffer.get(sequence); // Get the entry in the Disruptor
            // for the sequence
            event.setValue1(bb.getValue1());// Fill with data
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}
