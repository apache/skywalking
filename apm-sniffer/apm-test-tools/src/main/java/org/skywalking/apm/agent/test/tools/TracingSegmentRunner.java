package org.skywalking.apm.agent.test.tools;

import java.lang.reflect.Field;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.skywalking.apm.agent.core.context.IgnoreTracerContextListener;
import org.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.skywalking.apm.agent.core.context.TracingContext;
import org.skywalking.apm.agent.core.context.TracingContextListener;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;

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

    @Override protected Statement withAfters(FrameworkMethod method, Object target, final Statement statement) {
        return new Statement() {
            @Override public void evaluate() throws Throwable {
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
