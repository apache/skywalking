package org.skywalking.apm.collector.queue;

import com.lmax.disruptor.EventFactory;

/**
 * @author pengys5
 */
public class MessageHolderFactory implements EventFactory<MessageHolder> {

    public static MessageHolderFactory INSTANCE = new MessageHolderFactory();

    public MessageHolder newInstance() {
        return new MessageHolder();
    }
}
