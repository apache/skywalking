package org.skywalking.apm.agent.test.tools;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.test.helper.SpanHelper;
import org.skywalking.apm.network.trace.component.Component;

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class SpanAssert {
    public static void assertLogSize(AbstractSpan span, int exceptedSize) {
        assertThat(SpanHelper.getLogs(span).size(), is(exceptedSize));
    }

    public static void assertException(LogDataEntity logDataEntity, Class<? extends Throwable> throwableClass,
        String message) {
        Assert.assertThat(logDataEntity.getLogs().size(), is(4));
        Assert.assertThat(logDataEntity.getLogs().get(0).getValue(), CoreMatchers.<Object>is("error"));
        Assert.assertThat(logDataEntity.getLogs().get(1).getValue(), CoreMatchers.<Object>is(throwableClass.getName()));
        Assert.assertThat(logDataEntity.getLogs().get(2).getValue(), is(message));
        assertNotNull(logDataEntity.getLogs().get(3).getValue());
    }

    public static void assertException(LogDataEntity logDataEntity, Class<? extends Throwable> throwableClass) {
        Assert.assertThat(logDataEntity.getLogs().size(), is(4));
        Assert.assertThat(logDataEntity.getLogs().get(0).getValue(), CoreMatchers.<Object>is("error"));
        Assert.assertThat(logDataEntity.getLogs().get(1).getValue(), CoreMatchers.<Object>is(throwableClass.getName()));
        Assert.assertNull(logDataEntity.getLogs().get(2).getValue());
        assertNotNull(logDataEntity.getLogs().get(3).getValue());
    }

    public static void assertComponent(AbstractSpan span, Component component) {
        assertThat(SpanHelper.getComponentId(span), is(component.getId()));
    }

    public static void assertComponent(AbstractSpan span, String componentName) {
        assertThat(SpanHelper.getComponentName(span), is(componentName));
    }

    public static void assertLayer(AbstractSpan span, SpanLayer spanLayer) {
        assertThat(SpanHelper.getLayer(span), is(spanLayer));
    }

    public static void assertTag(AbstractSpan span, int index, String value) {
        assertThat(SpanHelper.getTags(span).get(index).getValue(), is(value));
    }

    public static void assertOccurException(AbstractSpan span, boolean excepted) {
        assertThat(SpanHelper.getErrorOccurred(span), is(excepted));
    }

}
