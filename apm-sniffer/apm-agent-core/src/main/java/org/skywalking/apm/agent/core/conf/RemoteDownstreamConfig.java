package org.skywalking.apm.agent.core.conf;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.agent.core.dictionary.DictionaryUtil;

/**
 * @author wusheng
 */
public class RemoteDownstreamConfig {
    public static class Agent {
        public volatile static int APPLICATION_ID = DictionaryUtil.nullValue();
    }

    public static class Collector {
        /**
         * Collector GRPC-Service address.
         */
        public volatile static List<String> GRPC_SERVERS = new LinkedList<String>();
    }
}
