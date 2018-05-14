package org.apache.skywalking.apm.plugin.trace.ignore.matcher;

/**
 *
 * @author liujc [liujunc1993@163.com]
 *
 */
public interface TracePathMatcher {

    boolean match(String pattern, String path);
}
