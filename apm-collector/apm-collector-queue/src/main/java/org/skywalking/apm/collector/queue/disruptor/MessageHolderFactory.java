package org.skywalking.apm.collector.queue.disruptor;

import com.lmax.disruptor.EventFactory;
import org.skywalking.apm.collector.core.queue.MessageHolder;

/**
 * @author pengys5
 */
public class MessageHolderFactory implements EventFactory<MessageHolder> {

    public static MessageHolderFactory INSTANCE = new MessageHolderFactory();

    public MessageHolder newInstance() {
        return new MessageHolder();
    }
}
