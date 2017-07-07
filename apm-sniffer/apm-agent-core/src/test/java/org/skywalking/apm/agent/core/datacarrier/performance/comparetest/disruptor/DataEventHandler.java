package org.skywalking.apm.agent.core.datacarrier.performance.comparetest.disruptor;

import com.lmax.disruptor.EventHandler;

/**
 * Created by wusheng on 2016/11/24.
 */
public class DataEventHandler implements EventHandler<Data> {
    public long counter = 0;

    @Override
    public void onEvent(Data data, long sequence, boolean endOfBatch) throws Exception {
        counter++;
        System.out.println("handler:" + data.getValue1());

        if (counter == TestDisruptor.totalSize) {
            System.out.println("time cost:" + (System.currentTimeMillis() - TestDisruptor.startTime));
            TestDisruptor.isEnd = true;
        }
    }
}
