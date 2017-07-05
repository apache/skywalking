package org.skywalking.apm.agent.core.datacarrier.consumer;


import java.util.List;
import org.skywalking.apm.agent.core.datacarrier.SampleData;

/**
 * Created by wusheng on 2016/10/26.
 */
public class SampleConsumer implements IConsumer<SampleData> {
    public int i = 1;

    @Override
    public void init() {

    }

    @Override
    public void consume(List<SampleData> data) {
        for(SampleData one : data) {
            one.setIntValue(this.hashCode());
            ConsumerTest.buffer.offer(one);
        }
    }

    @Override
    public void onError(List<SampleData> data, Throwable t) {

    }

    @Override
    public void onExit() {

    }
}
