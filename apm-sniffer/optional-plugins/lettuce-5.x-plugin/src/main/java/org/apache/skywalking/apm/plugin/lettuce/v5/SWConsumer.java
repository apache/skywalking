package org.apache.skywalking.apm.plugin.lettuce.v5;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.util.function.Consumer;

/**
 * @Author: zhaoyuguang
 * @Date: 2019/1/15 5:38 PM
 */

public class SWConsumer<T> implements Consumer<T> {

    private Consumer<T> consumer;
    private ContextSnapshot snapshot;

    SWConsumer(Consumer<T> consumer, ContextSnapshot snapshot) {
        this.consumer = consumer;
        this.snapshot = snapshot;
    }

    @Override
    public void accept(T t) {
        AbstractSpan span = ContextManager.createLocalSpan("SWConsumer/accept");
        span.setComponent(ComponentsDefine.LETTUCE);
        try {
            ContextManager.continued(snapshot);
            consumer.accept(t);
        } catch (Throwable th) {
            ContextManager.activeSpan().errorOccurred().log(th);
        } finally {
            ContextManager.stopSpan();
        }
    }
}
