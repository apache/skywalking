package org.skywalking.apm.plugin.jdbc;

import java.sql.SQLException;
import java.util.List;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.context.util.KeyValuePair;
import org.skywalking.apm.agent.test.helper.SpanHelper;

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class AbstractStatementTest {

    protected void assertDBSpanLog(LogDataEntity logData) {
        Assert.assertThat(logData.getLogs().size(), is(4));
        Assert.assertThat(logData.getLogs().get(0).getValue(), CoreMatchers.<Object>is("error"));
        Assert.assertThat(logData.getLogs().get(1).getValue(), CoreMatchers.<Object>is(SQLException.class.getName()));
        Assert.assertNull(logData.getLogs().get(2).getValue());
        assertNotNull(logData.getLogs().get(3).getValue());
    }

    protected void assertDBSpan(AbstractTracingSpan span, String exceptOperationName, String exceptDBStatement) {
        assertDBSpan(span, exceptOperationName);
        assertThat(span.isExit(), is(true));
        List<KeyValuePair> tags = SpanHelper.getTags(span);
        assertThat(tags.get(2).getValue(), is(exceptDBStatement));
    }

    protected void assertDBSpan(AbstractTracingSpan span, String exceptOperationName) {
        assertThat(span.getOperationName(), is(exceptOperationName));
        assertThat(SpanHelper.getComponentId(span), is(5));
        List<KeyValuePair> tags = SpanHelper.getTags(span);
        assertThat(tags.get(0).getValue(), is("sql"));
        assertThat(tags.get(1).getValue(), is("test"));
        assertThat(SpanHelper.getLayer(span), is(SpanLayer.DB));
    }

}
