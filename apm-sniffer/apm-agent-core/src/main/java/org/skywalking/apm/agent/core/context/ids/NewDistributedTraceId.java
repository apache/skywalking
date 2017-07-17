package org.skywalking.apm.agent.core.context.ids;

/**
 * The <code>NewDistributedTraceId</code> is a {@link DistributedTraceId} with a new generated id.
 *
 * @author wusheng
 */
public class NewDistributedTraceId extends DistributedTraceId {
    private static final String ID_TYPE = "T";

    public NewDistributedTraceId() {
        super(GlobalIdGenerator.generate(ID_TYPE));
    }
}
