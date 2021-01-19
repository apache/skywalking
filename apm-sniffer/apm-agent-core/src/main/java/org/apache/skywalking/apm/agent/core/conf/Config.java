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

package org.apache.skywalking.apm.agent.core.conf;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.logging.core.LogLevel;
import org.apache.skywalking.apm.agent.core.logging.core.LogOutput;
import org.apache.skywalking.apm.agent.core.logging.core.ResolverType;
import org.apache.skywalking.apm.agent.core.logging.core.WriterFactory;
import org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ClassCacheMode;
import org.apache.skywalking.apm.util.Length;

/**
 * This is the core config in sniffer agent.
 */
public class Config {

    public static class Agent {
        /**
         * Namespace isolates headers in cross process propagation. The HEADER name will be `HeaderName:Namespace`.
         */
        public static String NAMESPACE = "";

        /**
         * Service name is showed in skywalking-ui. Suggestion: set a unique name for each service, service instance
         * nodes share the same code
         */
        @Length(50)
        public static String SERVICE_NAME = "";

        /**
         * Authentication active is based on backend setting, see application.yml for more details. For most scenarios,
         * this needs backend extensions, only basic match auth provided in default implementation.
         */
        public static String AUTHENTICATION = "";

        /**
         * Negative or zero means off, by default. {@code #SAMPLE_N_PER_3_SECS} means sampling N {@link TraceSegment} in
         * 3 seconds tops.
         */
        public static int SAMPLE_N_PER_3_SECS = -1;

        /**
         * If the operation name of the first span is included in this set, this segment should be ignored.
         */
        public static String IGNORE_SUFFIX = ".jpg,.jpeg,.js,.css,.png,.bmp,.gif,.ico,.mp3,.mp4,.html,.svg";

        /**
         * The max number of spans in a single segment. Through this config item, SkyWalking keep your application
         * memory cost estimated.
         */
        public static int SPAN_LIMIT_PER_SEGMENT = 300;

        /**
         * If true, SkyWalking agent will save all instrumented classes files in `/debugging` folder. SkyWalking team
         * may ask for these files in order to resolve compatible problem.
         */
        public static boolean IS_OPEN_DEBUGGING_CLASS = false;

        /**
         * If true, SkyWalking agent will cache all instrumented classes to memory or disk files (decided by class cache
         * mode), allow other javaagent to enhance those classes that enhanced by SkyWalking agent.
         */
        public static boolean IS_CACHE_ENHANCED_CLASS = false;

        /**
         * The instrumented classes cache mode: MEMORY or FILE MEMORY: cache class bytes to memory, if instrumented
         * classes is too many or too large, it may take up more memory FILE: cache class bytes in `/class-cache`
         * folder, automatically clean up cached class files when the application exits
         */
        public static ClassCacheMode CLASS_CACHE_MODE = ClassCacheMode.MEMORY;

        /**
         * The identifier of the instance
         */
        @Length(50)
        public volatile static String INSTANCE_NAME = "";

        /**
         * service instance properties e.g. agent.instance_properties[org]=apache
         */
        public static Map<String, String> INSTANCE_PROPERTIES = new HashMap<>();

        /**
         * How depth the agent goes, when log cause exceptions.
         */
        public static int CAUSE_EXCEPTION_DEPTH = 5;

        /**
         * Force reconnection period of grpc, based on grpc_channel_check_interval. If count of check grpc channel
         * status more than this number. The channel check will call channel.getState(true) to requestConnection.
         */
        public static long FORCE_RECONNECTION_PERIOD = 1;

        /**
         * Limit the length of the operationName to prevent the overlength issue in the storage.
         *
         * <p>NOTICE</p>
         * In the current practice, we don't recommend the length over 190.
         */
        public static int OPERATION_NAME_THRESHOLD = 150;

        /**
         * Keep tracing even the backend is not available.
         */
        public static boolean KEEP_TRACING = false;

        /**
         * Force open TLS for gRPC channel if true.
         */
        public static boolean FORCE_TLS = false;
    }

    public static class OsInfo {
        /**
         * Limit the length of the ipv4 list size.
         */
        public static int IPV4_LIST_SIZE = 10;
    }

    public static class Collector {
        /**
         * grpc channel status check interval
         */
        public static long GRPC_CHANNEL_CHECK_INTERVAL = 30;
        /**
         * The period in which the agent report a heartbeat to the backend.
         */
        public static long HEARTBEAT_PERIOD = 30;
        /**
         * The agent sends the instance properties to the backend every `collector.heartbeat_period * collector.properties_report_period_factor` seconds
         */
        public static int PROPERTIES_REPORT_PERIOD_FACTOR = 10;
        /**
         * Collector skywalking trace receiver service addresses.
         */
        public static String BACKEND_SERVICE = "";
        /**
         * How long grpc client will timeout in sending data to upstream.
         */
        public static int GRPC_UPSTREAM_TIMEOUT = 30;
        /**
         * Get profile task list interval
         */
        public static int GET_PROFILE_TASK_INTERVAL = 20;

    }

    public static class Profile {
        /**
         * If true, skywalking agent will enable profile when user create a new profile task. Otherwise disable
         * profile.
         */
        public static boolean ACTIVE = true;

        /**
         * Parallel monitor segment count
         */
        public static int MAX_PARALLEL = 5;

        /**
         * Max monitor segment time(minutes), if current segment monitor time out of limit, then stop it.
         */
        public static int MAX_DURATION = 10;

        /**
         * Max dump thread stack depth
         */
        public static int DUMP_MAX_STACK_DEPTH = 500;

        /**
         * Snapshot transport to backend buffer size
         */
        public static int SNAPSHOT_TRANSPORT_BUFFER_SIZE = 500;
    }

    public static class Meter {
        /**
         * If true, skywalking agent will enable sending meters. Otherwise disable meter report.
         */
        public static boolean ACTIVE = true;

        /**
         * Report meters interval
         */
        public static Integer REPORT_INTERVAL = 20;

        /**
         * Max size of the meter count, using {@link org.apache.skywalking.apm.agent.core.meter.MeterId} as identity
         */
        public static Integer MAX_METER_SIZE = 500;
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

    public static class Logging {
        /**
         * Log file name.
         */
        public static String FILE_NAME = "skywalking-api.log";

        /**
         * Log files directory. Default is blank string, means, use "{theSkywalkingAgentJarDir}/logs  " to output logs.
         * {theSkywalkingAgentJarDir} is the directory where the skywalking agent jar file is located.
         * <p>
         * Ref to {@link WriterFactory#getLogWriter()}
         */
        public static String DIR = "";

        /**
         * The max size of log file. If the size is bigger than this, archive the current file, and write into a new
         * file.
         */
        public static int MAX_FILE_SIZE = 300 * 1024 * 1024;

        /**
         * The max history log files. When rollover happened, if log files exceed this number, then the oldest file will
         * be delete. Negative or zero means off, by default.
         */
        public static int MAX_HISTORY_FILES = -1;

        /**
         * The log level. Default is debug.
         */
        public static LogLevel LEVEL = LogLevel.DEBUG;

        /**
         * The log output. Default is FILE.
         */
        public static LogOutput OUTPUT = LogOutput.FILE;

        /**
         * The log resolver type. Default is PATTERN which will create PatternLogResolver later.
         */
        public static ResolverType RESOLVER = ResolverType.PATTERN;

        /**
         * The log patten. Default is "%level %timestamp %thread %class : %msg %throwable". Each conversion specifiers
         * starts with a percent sign '%' and fis followed by conversion word. There are some default conversion
         * specifiers: %thread = ThreadName %level = LogLevel  {@link LogLevel} %timestamp = The now() who format is
         * 'yyyy-MM-dd HH:mm:ss:SSS' %class = SimpleName of TargetClass %msg = Message of user input %throwable =
         * Throwable of user input %agent_name = ServiceName of Agent {@link Agent#SERVICE_NAME}
         *
         * @see org.apache.skywalking.apm.agent.core.logging.core.PatternLogger#DEFAULT_CONVERTER_MAP
         */
        public static String PATTERN = "%level %timestamp %thread %class : %msg %throwable";
    }

    public static class StatusCheck {
        /**
         * Listed exceptions would not be treated as an error. Because in some codes, the exception is being used as a
         * way of controlling business flow.
         */
        public static String IGNORED_EXCEPTIONS = "";

        /**
         * The max recursive depth when checking the exception traced by the agent. Typically, we don't recommend
         * setting this more than 10, which could cause a performance issue. Negative value and 0 would be ignored,
         * which means all exceptions would make the span tagged in error status.
         */
        public static Integer MAX_RECURSIVE_DEPTH = 1;
    }

    public static class Plugin {
        /**
         * Control the length of the peer field.
         */
        public static int PEER_MAX_LENGTH = 200;

        /**
         * Exclude activated plugins
         */
        public static String EXCLUDE_PLUGINS = "";

        /**
         * Mount the folders of the plugins. The folder path is relative to agent.jar.
         */
        public static List<String> MOUNT = Arrays.asList("plugins", "activations");
    }

    public static class Correlation {
        /**
         * Max element count in the correlation context.
         */
        public static int ELEMENT_MAX_NUMBER = 3;

        /**
         * Max value length of each element.
         */
        public static int VALUE_MAX_LENGTH = 128;

        /**
         * Tag the span by the key/value in the correlation context, when the keys listed here exist.
         */
        public static String AUTO_TAG_KEYS = "";
    }
}
