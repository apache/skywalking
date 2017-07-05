package org.skywalking.apm.agent.core.datacarrier.performance.comparetest.disruptor;

import com.lmax.disruptor.EventFactory;

/**
 * Created by wusheng on 2016/11/24.
 */
public class DataEventFactory implements EventFactory<Data> {

    @Override
    public Data newInstance() {
        return new Data();
    }
}
