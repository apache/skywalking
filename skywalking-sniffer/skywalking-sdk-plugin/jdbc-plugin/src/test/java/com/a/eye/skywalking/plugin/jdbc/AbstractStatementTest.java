package com.a.eye.skywalking.plugin.jdbc;

import com.a.eye.skywalking.sniffer.mock.context.MockTracerContextListener;
import com.a.eye.skywalking.trace.LogData;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.tag.Tags;

import org.hamcrest.CoreMatchers;

import java.sql.SQLException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public abstract class AbstractStatementTest {

    protected MockTracerContextListener mockTracerContextListener;

    protected void assertDBSpanLog(LogData logData) {
        assertThat(logData.getFields().size(), is(4));
        assertThat(logData.getFields().get("event"), CoreMatchers.<Object>is("error"));
        assertEquals(logData.getFields().get("error.kind"), SQLException.class.getName());
        assertNull(logData.getFields().get("message"));
    }

    protected void assertDBSpan(Span span, String exceptOperationName, String exceptDBStatement) {
        assertDBSpan(span, exceptOperationName);
        assertThat(Tags.DB_STATEMENT.get(span), is(exceptDBStatement));
    }

    protected void assertDBSpan(Span span, String exceptOperationName) {
        assertThat(span.getOperationName(), is(exceptOperationName));
        assertThat(Tags.COMPONENT.get(span), is("Mysql"));
        assertThat(Tags.DB_INSTANCE.get(span), is("test"));
        assertTrue(Tags.SPAN_LAYER.isDB(span));
    }
}
