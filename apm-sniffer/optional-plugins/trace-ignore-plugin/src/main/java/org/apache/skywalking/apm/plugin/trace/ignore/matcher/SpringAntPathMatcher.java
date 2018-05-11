package org.apache.skywalking.apm.plugin.trace.ignore.matcher;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

public class SpringAntPathMatcher implements TracePathMatcher {

    private PathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean match(String pattern, String path) {
        return pathMatcher.match(pattern, path);
    }
}
