package org.skywalking.apm.agent.core.context.trace;

/**
 * @author wusheng
 */
public enum SpanLayer {
    DB,
    RPC_FRAMEWORK,
    HTTP,
    MQ;

    public static void asDB(AbstractSpan span) {
        span.setLayer(SpanLayer.DB);
    }

    public static void asRPCFramework(AbstractSpan span) {
        span.setLayer(SpanLayer.RPC_FRAMEWORK);
    }

    public static void asHttp(AbstractSpan span) {
        span.setLayer(SpanLayer.HTTP);
    }

    public static void asMQ(AbstractSpan span) {
        span.setLayer(SpanLayer.MQ);
    }
}
