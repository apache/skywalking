package org.skywalking.apm.toolkit.opentracing;

import io.opentracing.ActiveSpan;

/**
 * @author wusheng
 */
public class SkywalkingContinuation implements ActiveSpan.Continuation {
    @NeedSnifferActivation("1. ContextManager#capture" +
        "2. set ContextSnapshot to the dynamic field")
    public SkywalkingContinuation() {
    }

    @NeedSnifferActivation("1. get ContextSnapshot from the dynamic field" +
        "2. ContextManager#continued")
    @Override
    public ActiveSpan activate() {
        SkywalkingSpanBuilder builder = new SkywalkingSpanBuilder("Thread/" + Thread.currentThread().getName());
        return builder.startActive();
    }
}
