package org.skywalking.apm.agent.core.conf;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.logging.LogLevel;
import org.skywalking.apm.agent.core.logging.WriterFactory;

/**
 * This is the core config in sniffer agent.
 *
 * @author wusheng
 */
public class Config {

    public static class Agent {
        /**
         * Application code is showed in sky-walking-ui.
         * Suggestion: set an unique name for each application, one application's nodes share the same code.
         */
        public static String APPLICATION_CODE = "";

        /**
         * Negative or zero means off, by default.
         * {@link #SAMPLE_N_PER_3_SECS} means sampling N {@link TraceSegment} in 10 seconds tops.
         */
        public static int SAMPLE_N_PER_3_SECS = -1;

        /**
         * If the operation name of the first span is included in this set,
         * this segment should be ignored.
         */
        public static String IGNORE_SUFFIX = ".jpg,.jpeg,.js,.css,.png,.bmp,.gif,.ico,.mp3,.mp4,.html,.svg";
    }

    public static class Collector {
        /**
         * grpc channel status check interval
         */
        public static long GRPC_CHANNEL_CHECK_INTERVAL = 30;
        /**
         * application and service registry check interval
         */
        public static long APP_AND_SERVICE_REGISTER_CHECK_INTERVAL = 10;
        /**
         * discovery rest check interval
         */
        public static long DISCOVERY_CHECK_INTERVAL = 60;
        /**
         * Collector REST-Service address.
         * e.g.
         * SERVERS="127.0.0.1:8080"  for single collector node.
         * SERVERS="10.2.45.126:8080,10.2.45.127:7600"  for multi collector nodes.
         */
        public static String SERVERS = "";

        /**
         * Collector service discovery REST service name
         */
        public static String DISCOVERY_SERVICE_NAME = "/agentstream/grpc";
    }

    public static class Jvm {
        /**
         * The buffer size of collected JVM info.
         */
        public static int BUFFER_SIZE = 60 * 10;
    }

    public static class Buffer {
        public static int CHANNEL_SIZE = 5;

        public static int BUFFER_SIZE = 300;
    }

    public static class Dictionary {
        /**
         * The buffer size of application codes and peer
         */
        public static int APPLICATION_CODE_BUFFER_SIZE = 10 * 10000;

        public static int OPERATION_NAME_BUFFER_SIZE = 1000 * 10000;
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

        /**
         * Name of disabled plugin, The value spilt by <code>,</code>
         * if you have multiple plugins need to disable.
         *
         * Here are the plugin names :
         * tomcat-7.x/8.x, dubbo, jedis-2.x, motan, httpclient-4.x, jdbc, mongodb-3.x.
         */
        public static List DISABLED_PLUGINS = new LinkedList();

        /**
         * Name of force enable plugin, The value spilt by <code>,</code>
         * if you have multiple plugins need to enable.
         */
        public static List FORCE_ENABLE_PLUGINS = new LinkedList();

        public static class MongoDB {
            /**
             * If true, trace all the parameters, default is false.
             * Only trace the operation, not include parameters.
             */
            public static boolean TRACE_PARAM = false;
        }
    }
}
