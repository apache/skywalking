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

import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.logging.core.LogLevel;
import org.apache.skywalking.apm.agent.core.logging.core.LogOutput;
import org.apache.skywalking.apm.agent.core.logging.core.WriterFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * This is the core config in sniffer agent.
 *
 * @author wusheng
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
         * The identifier of the instance
         */
        public static String INSTANCE_UUID = "";

        /*
         * service instance properties
         * e.g.
         *   agent.instance_properties[org]=apache
         */
        public static Map<String, String> INSTANCE_PROPERTIES = new HashMap<>();

        /**
         * How depth the agent goes, when log cause exceptions.
         */
        public static int CAUSE_EXCEPTION_DEPTH = 5;

        /**
         * How long should the agent wait (in minute) before re-registering to the OAP server after receiving reset
         * command
         */
        public static int COOL_DOWN_THRESHOLD = 10;

        /**
         * Force reconnection period of grpc, based on grpc_channel_check_interval. If count of check grpc channel
         * status more than this number. The channel check will call channel.getState(true) to requestConnection.
         */
        public static long FORCE_RECONNECTION_PERIOD = 1;

        /**
         * Limit the length of the operationName to prevent errors when inserting elasticsearch
         **/
        public static int OPERATION_NAME_THRESHOLD = 500;
    }

    public static class Collector {
        /**
         * grpc channel status check interval
         */
        public static long GRPC_CHANNEL_CHECK_INTERVAL = 30;
        /**
         * service and endpoint registry check interval
         */
        public static long APP_AND_SERVICE_REGISTER_CHECK_INTERVAL = 3;
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
         * If true, skywalking agent will enable profile when user create a new profile task. Otherwise disable profile.
         */
        public static boolean ACTIVE = true;
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
        public static int SERVICE_CODE_BUFFER_SIZE = 10 * 10000;

        public static int ENDPOINT_NAME_BUFFER_SIZE = 1000 * 10000;
    }

    public static class Logging {
        /**
         * Log file name.
         */
        public static String FILE_NAME = "skywalking-api.log";

        /**
         * Log files directory. Default is blank string, means, use "{theSkywalkingAgentJarDir}/logs  " to output logs. 
         * {theSkywalkingAgentJarDir} is the directory where the skywalking agent jar file is located.
         *
         * Ref to {@link WriterFactory#getLogWriter()}
         */
        public static String DIR = "";

        /**
         * The max size of log file. If the size is bigger than this, archive the current file, and write into a new
         * file.
         */
        public static int MAX_FILE_SIZE = 300 * 1024 * 1024;

        /**
         * The max history log files. When rollover happened, if log files exceed this number,
         * then the oldest file will be delete. Negative or zero means off, by default.
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

    public static class Plugin {

        /**
         * Control the length of the peer field.
         */
        public static int PEER_MAX_LENGTH = 200;

        public static class MongoDB {
            /**
             * If true, trace all the parameters in MongoDB access, default is false. Only trace the operation, not
             * include parameters.
             */
            public static boolean TRACE_PARAM = false;

            /**
             * For the sake of performance, SkyWalking won't save the entire parameters string into the tag, but only
             * the first {@code FILTER_LENGTH_LIMIT} characters.
             *
             * Set a negative number to save the complete parameter string to the tag.
             */
            public static int FILTER_LENGTH_LIMIT = 256;
        }

        public static class Elasticsearch {
            /**
             * If true, trace all the DSL(Domain Specific Language) in ElasticSearch access, default is false.
             */
            public static boolean TRACE_DSL = false;
        }

        public static class Customize {
            /**
             * Custom enhancement class configuration file path, recommended to use an absolute path.
             */
            public static String ENHANCE_FILE = "";

            /**
             * Some information after custom enhancements, this configuration is used by the custom enhancement plugin.
             * And using Map CONTEXT for avoiding classloader isolation issue.
             */
            public static Map<String, Object> CONTEXT = new HashMap<>();
        }

        public static class Tomcat {
            /**
             * This config item controls that whether the Tomcat plugin should
             * collect the parameters of the request.
             */
            public static boolean COLLECT_HTTP_PARAMS = false;
        }

        public static class SpringMVC {
            /**
             * If true, the fully qualified method name will be used as the endpoint name instead of the request URL,
             * default is false.
             */
            public static boolean USE_QUALIFIED_NAME_AS_ENDPOINT_NAME = false;

            /**
             * This config item controls that whether the SpringMVC plugin should
             * collect the parameters of the request.
             */
            public static boolean COLLECT_HTTP_PARAMS = false;
        }

        public static class Toolkit {
            /**
             * If true, the fully qualified method name will be used as the operation name instead of the given
             * operation name, default is false.
             */
            public static boolean USE_QUALIFIED_NAME_AS_OPERATION_NAME = false;
        }

        public static class MySQL {
            /**
             * If set to true, the parameters of the sql (typically {@link java.sql.PreparedStatement}) would be
             * collected.
             */
            public static boolean TRACE_SQL_PARAMETERS = false;
            /**
             * For the sake of performance, SkyWalking won't save the entire parameters string into the tag, but only
             * the first {@code SQL_PARAMETERS_MAX_LENGTH} characters.
             *
             * Set a negative number to save the complete parameter string to the tag.
             */
            public static int SQL_PARAMETERS_MAX_LENGTH = 512;
        }

        public static class POSTGRESQL {
            /**
             * If set to true, the parameters of the sql (typically {@link java.sql.PreparedStatement}) would be
             * collected.
             */
            public static boolean TRACE_SQL_PARAMETERS = false;

            /**
             * For the sake of performance, SkyWalking won't save the entire parameters string into the tag, but only
             * the first {@code SQL_PARAMETERS_MAX_LENGTH} characters.
             *
             * Set a negative number to save the complete parameter string to the tag.
             */
            public static int SQL_PARAMETERS_MAX_LENGTH = 512;
        }

        public static class SolrJ {
            /**
             * If true, trace all the query parameters(include deleteByIds and deleteByQuery) in Solr query request,
             * default is false.
             */
            public static boolean TRACE_STATEMENT = false;

            /**
             * If true, trace all the operation parameters in Solr request, default is false.
             */
            public static boolean TRACE_OPS_PARAMS = false;
        }

        /**
         * Operation name group rules
         */
        public static class OPGroup {
            /*
             * Since 6.6.0, exit span is not requesting endpoint register,
             * this group rule is not required.
             *
             * Keep this commented, just as a reminder that, it will be reused in a RPC server side plugin.
             */
//            public static class RestTemplate implements OPGroupDefinition {
//                public static Map<String, String> RULE = new HashMap<String, String>();
//            }
        }

        public static class Light4J {
            /**
             * If true, trace all middleware/business handlers that are part of the Light4J handler chain for a request,
             * generating a local span for each.
             */
            public static boolean TRACE_HANDLER_CHAIN = false;
        }

        public static class SpringTransaction {

            /**
             * If true, the transaction definition name will be simplified
             */
            public static boolean SIMPLIFY_TRANSACTION_DEFINITION_NAME = false;
        }

        public static class JdkThreading {

            /**
             * Threading classes ({@link java.lang.Runnable} and {@link java.util.concurrent.Callable}
             * and their subclasses, including anonymous inner classes)
             * whose name matches any one of the {@code THREADING_CLASS_PREFIXES} (splitted by ,)
             * will be instrumented
             */
            public static String THREADING_CLASS_PREFIXES = "";
        }

        public static class Http {
            /**
             * When either {@link Tomcat#COLLECT_HTTP_PARAMS} or {@link SpringMVC#COLLECT_HTTP_PARAMS}
             * is enabled, how many characters to keep and send to the OAP backend,
             * use negative values to keep and send the complete parameters,
             * NB. this config item is added for the sake of performance
             */
            public static int HTTP_PARAMS_LENGTH_THRESHOLD = 1024;
        }
    }
}
