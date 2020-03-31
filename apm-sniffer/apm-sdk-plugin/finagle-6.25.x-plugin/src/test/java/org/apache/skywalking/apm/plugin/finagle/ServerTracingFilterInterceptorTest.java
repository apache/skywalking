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

package org.apache.skywalking.apm.plugin.finagle;

import com.twitter.finagle.context.Contexts;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.SW8CarrierItem;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.apache.skywalking.apm.agent.test.helper.SegmentRefHelper;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import scala.runtime.AbstractFunction0;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class ServerTracingFilterInterceptorTest extends AbstractTracingFilterTest {

    @SegmentStoragePoint
    protected SegmentStorage segmentStorage;

    private ServerTracingFilterInterceptor serverTracingFilterInterceptor;

    @Override
    protected void prepareForTest() {
        serverTracingFilterInterceptor = new ServerTracingFilterInterceptor();
    }

    @Override
    protected void assertSpan(AbstractTracingSpan span) {
        assertTrue(span.isEntry());
    }

    @Test
    public void testServer() {
        runWithContext(new TestFunction() {
            @Override
            public void apply() throws Throwable {
                rpcInterceptor.onConstruct(null, new Object[]{rpc});
                serverTracingFilterInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);
                result.setValue(new Object());
                serverTracingFilterInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, result);
                assertServer();
                assertTraceSegments(segmentStorage.getTraceSegments());
            }
        });
    }

    @Test
    public void testServerWithException() {
        runWithContext(new TestFunction() {
            @Override
            public void apply() throws Throwable {
                rpcInterceptor.onConstruct(null, new Object[]{rpc});
                serverTracingFilterInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);
                result.setValue(new Object());
                serverTracingFilterInterceptor.handleMethodException(enhancedInstance, null, allArguments, argumentTypes, new RuntimeException());
                serverTracingFilterInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, result);
                assertServer();
                assertTraceSegmentsWithError(segmentStorage.getTraceSegments());
            }
        });
    }

    @Test
    public void testServerWithResultHasException() {
        runWithContext(new TestFunction() {
            @Override
            public void apply() throws Throwable {
                rpcInterceptor.onConstruct(null, new Object[]{rpc});
                serverTracingFilterInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);
                result.setException(new RuntimeException());
                serverTracingFilterInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, result);
                assertServer();
                assertTraceSegmentsWithError(segmentStorage.getTraceSegments());
            }
        });
    }

    private void runWithContext(final TestFunction function) {
        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            if (next.getHeadKey().equals(SW8CarrierItem.HEADER_NAME)) {
                next.setHeadValue("1-My40LjU=-MS4yLjM=-3-c2VydmljZQ==-aW5zdGFuY2U=-L2FwcA==-MTI3LjAuMC4xOjgwODA=");
            }
        }
        SWContextCarrier swContextCarrier = new SWContextCarrier();
        swContextCarrier.setContextCarrier(contextCarrier);
        swContextCarrier.setOperationName(rpc);
        Contexts.broadcast().let(SWContextCarrier$.MODULE$, swContextCarrier, new AbstractFunction0<Void>() {
            @Override
            public Void apply() {
                try {
                    function.apply();
                } catch (Throwable throwable) {
                    throw new RuntimeException(throwable);
                }
                return null;
            }
        });
    }

    interface TestFunction {
        void apply() throws Throwable;
    }

    private void assertServer() {
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        TraceSegmentRef actual = traceSegment.getRefs().get(0);
        assertThat(SegmentRefHelper.getSpanId(actual), is(3));
        assertThat(SegmentRefHelper.getParentServiceInstance(actual), is("instance"));
        assertThat(SegmentRefHelper.getTraceSegmentId(actual).toString(), is("3.4.5"));
    }
}
