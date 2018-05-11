package org.apache.skywalking.apm.plugin.trace.ignore.matcher;

public interface TracePathMatcher {

    boolean match(String pattern, String path);
}
