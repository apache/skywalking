package org.apache.skywalking.apm.plugin.trace.ignore.conf;

/**
 *
 * @author liujc [liujunc1993@163.com]
 *
 */
public class IgnoreConfig {

    public static class Trace {
        /**
         * If the operation name of the first span is matching, this segment should be ignored
         * /path/?   Match any single character
         * /path/*   Match any number of characters
         * /path/**  Match any number of characters and support multilevel directories
         */
        public static String IGNORE_PATH = "";
    }
}
