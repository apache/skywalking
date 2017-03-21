package com.a.eye.skywalking.collector.worker.tools;

import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.tag.Tags;

/**
 * @author pengys5
 */
public class SpanPeersTools {
    public static String getPeers(Span span) {
        if (StringUtil.isEmpty(Tags.PEERS.get(span))) {
            String host = Tags.PEER_HOST.get(span);
            int port = Tags.PEER_PORT.get(span);
            return host + ":" + port;
        } else {
            return Tags.PEERS.get(span);
        }
    }
}
