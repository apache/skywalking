package org.skywalking.apm.api.conf;

import org.skywalking.apm.api.logging.LogLevel;
import org.skywalking.apm.api.logging.WriterFactory;

/**
 * This is the core config in sniffer agent.
 *
 * @author wusheng
 */
public class Config {

    public static class Agent {
        /**
         * Application code is showed in sky-walking-ui.
         * Suggestion: set a unique name for each application, one application's nodes share the same code.
         */
        public static String APPLICATION_CODE = "";

        /**
         * One, means sampling OFF.
         * Greater than one, select one trace in every N traces.
         * Zero and negative number are illegal.
         */
        public static int SAMPLING_CYCLE = 1;
    }

    public static class Collector {
        /**
         * Collector REST-Service address.
         * e.g.
         * SERVERS="127.0.0.1:8080"  for single collector node.
         * SERVERS="10.2.45.126:8080,10.2.45.127:7600"  for multi collector nodes.
         */
        public static String SERVERS = "";

        /**
         * Collector receive segments REST-Service name.
         */
        public static String SERVICE_NAME = "/segments";

        /**
         * The max size to send traces per rest-service call.
         */
        public static int BATCH_SIZE = 50;
    }

    public static class Buffer {
        /**
         * The in-memory buffer size. Based on Disruptor, this value must be 2^n.
         *
         * @see {https://github.com/LMAX-Exchange/disruptor}
         */
        public static int SIZE = 512;
    }

    public static class Logging {
        /**
         * Log file name.
         */
        public static String FILE_NAME = "skywalking-api.log";

        /**
         * Log files directory.
         * Default is blank string, means, use "system.out" to output logs.
         *
         * @see {@link WriterFactory#getLogWriter()}
         */
        public static String DIR = "";

        /**
         * The max size of log file.
         * If the size is bigger than this, archive the current file, and write into a new file.
         */
        public static int MAX_FILE_SIZE = 300 * 1024 * 1024;

        /**
         * The log level. Default is debug.
         *
         * @see {@link LogLevel}
         */
        public static LogLevel LEVEL = LogLevel.DEBUG;
    }

    public static class Plugin {
        public static class MongoDB {
            /**
             * If true, trace all the parameters, default is false.
             * Only trace the operation, not include parameters.
             */
            public static boolean TRACE_PARAM = false;
        }
    }
}
