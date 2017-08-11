package org.skywalking.apm.agent.core.context.trace;

import org.skywalking.apm.agent.core.dictionary.DictionaryManager;
import org.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.skywalking.apm.agent.core.dictionary.PossibleFound;

/**
 * The <code>StackBasedTracingSpan</code> represents a span with an inside stack construction.
 *
 * This kind of span can start and finish multi times in a stack-like invoke line.
 *
 * @author wusheng
 */
public abstract class StackBasedTracingSpan extends AbstractTracingSpan {
    protected int stackDepth;

    protected StackBasedTracingSpan(int spanId, int parentSpanId, String operationName) {
        super(spanId, parentSpanId, operationName);
        this.stackDepth = 0;
    }

    protected StackBasedTracingSpan(int spanId, int parentSpanId, int operationId) {
        super(spanId, parentSpanId, operationId);
        this.stackDepth = 0;
    }

    @Override
    public boolean finish(TraceSegment owner) {
        if (--stackDepth == 0) {
            if (this.operationId == DictionaryUtil.nullValue()) {
                this.operationId = (Integer)DictionaryManager.findOperationNameCodeSection()
                    .findOrPrepare4Register(owner.getApplicationId(), operationName)
                    .doInCondition(
                        new PossibleFound.FoundAndObtain() {
                            @Override public Object doProcess(int value) {
                                return value;
                            }
                        },
                        new PossibleFound.NotFoundAndObtain() {
                            @Override public Object doProcess() {
                                return DictionaryUtil.nullValue();
                            }
                        }
                    );
            }
            return super.finish(owner);
        } else {
            return false;
        }
    }
}
