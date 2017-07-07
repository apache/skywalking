package org.skywalking.apm.agent.core.datacarrier.consumer;

import java.util.List;

/**
 * Created by wusheng on 2016/10/25.
 */
public interface IConsumer<T> {
    void init();

    void consume(List<T> data);

    void onError(List<T> data, Throwable t);

    void onExit();
}
