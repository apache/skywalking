/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.agent.test.tools;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.network.trace.component.Component;

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class SpanAssert {
    public static void assertLogSize(AbstractSpan span, int exceptedSize) {
        assertThat(SpanHelper.getLogs(span).size(), is(exceptedSize));
    }

    public static void assertTagSize(AbstractSpan span, int exceptedSize) {
        assertThat(SpanHelper.getTags(span).size(), is(exceptedSize));
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
