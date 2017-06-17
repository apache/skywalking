package org.skywalking.apm.sniffer.mock.trace.builders.span;

import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;

/**
 * The <code>MySQLGenerator</code> generates all possible spans, by tracing mysql client access.
 *
 * @author wusheng
 */
public class MySQLGenerator {
    public static class Query extends SpanGeneration {
        @Override
        protected void before() {
            AbstractSpan span = ContextManager.createSpan("mysql/jdbi/statement/executeQuery");
            Tags.COMPONENT.set(span, "Mysql");
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
            span.setPeerHost("10.5.34.18");
            span.setPort(30088);
            Tags.DB_INSTANCE.set(span, "mysql-instance");
            Tags.DB_STATEMENT.set(span, "select * from users where user_id = 1");
            Tags.DB_TYPE.set(span, "sql");
            Tags.SPAN_LAYER.asDB(span);
        }

        @Override
        protected void after() {
            ContextManager.stopSpan();
        }
    }
}
