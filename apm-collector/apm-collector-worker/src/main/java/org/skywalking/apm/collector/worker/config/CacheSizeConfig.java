package org.skywalking.apm.collector.worker.config;

/**
 * @author pengys5
 */
public class CacheSizeConfig {

    public static class Cache {
        public static class Analysis {
            public static int SIZE = 1024;
        }

        public static class Persistence {
            public static int SIZE = 5000;
        }
    }
}
