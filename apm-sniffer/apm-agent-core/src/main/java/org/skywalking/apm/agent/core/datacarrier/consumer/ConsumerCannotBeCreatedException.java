package org.skywalking.apm.agent.core.datacarrier.consumer;

/**
 * Created by wusheng on 2016/11/15.
 */
public class ConsumerCannotBeCreatedException extends RuntimeException {
    ConsumerCannotBeCreatedException(Throwable t) {
        super(t);
    }
}
