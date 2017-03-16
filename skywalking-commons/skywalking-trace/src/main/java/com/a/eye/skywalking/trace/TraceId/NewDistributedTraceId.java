package com.a.eye.skywalking.trace.TraceId;

import com.a.eye.skywalking.trace.GlobalIdGenerator;

/**
 * The <code>NewDistributedTraceId</code> is a {@link DistributedTraceId} with a new generated id.
 *
 * @author wusheng
 */
public class NewDistributedTraceId extends DistributedTraceId {
    private static final String ID_TYPE = "Trace";

    public NewDistributedTraceId() {
        super(GlobalIdGenerator.generate(ID_TYPE));
    }
}
