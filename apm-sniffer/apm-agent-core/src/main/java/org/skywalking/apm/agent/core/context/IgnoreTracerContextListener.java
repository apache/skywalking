package org.skywalking.apm.agent.core.context;


/**
 * @author wusheng
 */
public interface IgnoreTracerContextListener {
    void afterFinished(IgnoredTracerContext traceSegment);
}
