package com.a.eye.skywalking.collector.worker.tools;

import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.collector.worker.Const;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.tag.Tags;

/**
 * @author pengys5
 */
public enum SpanPeersTools {
    INSTANCE;

    public String getPeers(Span span) {
        if (StringUtil.isEmpty(Tags.PEERS.get(span))) {
            String host = Tags.PEER_HOST.get(span);
            int port = Tags.PEER_PORT.get(span);
            return Const.PEERS_FRONT_SPLIT + host + ":" + port + Const.PEERS_BEHIND_SPLIT;
        } else {
            return Const.PEERS_FRONT_SPLIT + Tags.PEERS.get(span) + Const.PEERS_BEHIND_SPLIT;
        }
    }
}
