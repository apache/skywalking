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

package org.apache.skywalking.apm.agent.core.test.tools;

import java.lang.reflect.Field;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.apache.skywalking.apm.agent.core.context.IgnoreTracerContextListener;
import org.apache.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.agent.core.context.TracingContextListener;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;

public class TracingSegmentRunner extends BlockJUnit4ClassRunner {
    private TracingContextListener tracingContextListener;
    private IgnoreTracerContextListener ignoreTracerContextListener;
    private Field field;
    private Object targetObject;
    private SegmentStorage tracingData;

    public TracingSegmentRunner(Class<?> klass) throws InitializationError {
        super(klass);
        for (Field field : klass.getDeclaredFields()) {
            if (field.isAnnotationPresent(SegmentStoragePoint.class) && field.getType().equals(SegmentStorage.class)) {
                this.field = field;
                this.field.setAccessible(true);
                break;
            }
        }
    }

    @Override
    protected Object createTest() throws Exception {
        targetObject = super.createTest();
        return targetObject;
    }

    @Override
    protected Statement withAfters(FrameworkMethod method, Object target, final Statement statement) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (field != null) {
                    try {
                        tracingData = new SegmentStorage();
                        field.set(targetObject, tracingData);
                    } catch (IllegalAccessException e) {
                    }
                }
                tracingContextListener = new TracingContextListener() {
                    @Override
                    public void afterFinished(TraceSegment traceSegment) {
                        tracingData.addTraceSegment(traceSegment);
                    }
                };

                ignoreTracerContextListener = new IgnoreTracerContextListener() {
                    @Override
                    public void afterFinished(IgnoredTracerContext tracerContext) {
                        tracingData.addIgnoreTraceContext(tracerContext);
                    }
                };

                TracingContext.ListenerManager.add(tracingContextListener);
                IgnoredTracerContext.ListenerManager.add(ignoreTracerContextListener);
                try {
                    statement.evaluate();
                } finally {
                    TracingContext.ListenerManager.remove(tracingContextListener);
                    IgnoredTracerContext.ListenerManager.remove(ignoreTracerContextListener);
                }
            }
        };
    }
}
