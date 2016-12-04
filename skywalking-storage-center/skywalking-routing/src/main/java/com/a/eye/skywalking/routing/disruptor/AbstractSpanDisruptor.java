package com.a.eye.skywalking.routing.disruptor;

import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;

/**
 * Created by wusheng on 2016/12/4.
 */
public class AbstractSpanDisruptor {
    private volatile boolean isRunning = true;

    public long getRingBufferSequence(RingBuffer buffer){
        long sequence;
        while(true) {
            try {
                if(!isRunning){
                    return -1;
                }

                sequence = buffer.tryNext();
                break;
            } catch (InsufficientCapacityException e) {
                try {
                    Thread.sleep(1L);
                } catch (InterruptedException e1) {
                }
            }
        }
        return sequence;
    }

    protected boolean isShutdown(){
        return !isRunning;
    }

    protected void shutdown(){
        isRunning = false;
    }
}
