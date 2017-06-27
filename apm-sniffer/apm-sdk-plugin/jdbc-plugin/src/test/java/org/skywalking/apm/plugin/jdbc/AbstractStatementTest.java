package org.skywalking.apm.plugin.jdbc;

import java.lang.reflect.Field;
import java.util.List;
import org.hamcrest.CoreMatchers;
import org.skywalking.apm.sniffer.mock.context.MockTracingContextListener;
import org.skywalking.apm.sniffer.mock.trace.tags.StringTagReader;
import org.skywalking.apm.agent.core.context.tag.Tags;

import java.sql.SQLException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

public abstract class AbstractStatementTest {

    protected MockTracingContextListener mockTracerContextListener;

    protected void assertDBSpanLog(LogData logData) {
        assertThat(logData.getFields().size(), is(4));
        assertThat(logData.getFields().get("event"), CoreMatchers.<Object>is("error"));
        assertEquals(logData.getFields().get("error.kind"), SQLException.class.getName());
        assertNull(logData.getFields().get("message"));
    }

    protected void assertDBSpan(Span span, String exceptOperationName, String exceptDBStatement) {
        assertDBSpan(span, exceptOperationName);
        assertThat(StringTagReader.get(span, Tags.DB_STATEMENT), is(exceptDBStatement));
    }

    protected void assertDBSpan(Span span, String exceptOperationName) {
        assertThat(span.getOperationName(), is(exceptOperationName));
        assertThat(StringTagReader.get(span, Tags.COMPONENT), is("Mysql"));
        assertThat(StringTagReader.get(span, Tags.DB_INSTANCE), is("test"));
        assertThat(StringTagReader.get(span, Tags.SPAN_LAYER.SPAN_LAYER_TAG), is("db"));
    }

    protected List<LogData> getLogs(Span span) throws NoSuchFieldException, IllegalAccessException {
        Field logs = Span.class.getDeclaredField("logs");
        logs.setAccessible(true);
        return (List<LogData>)logs.get(span);
    }

}
