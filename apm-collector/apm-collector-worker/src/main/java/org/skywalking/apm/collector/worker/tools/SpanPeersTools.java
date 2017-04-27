package org.skywalking.apm.collector.worker.tools;

import org.skywalking.apm.api.util.StringUtil;
import org.skywalking.apm.collector.worker.Const;
import org.skywalking.apm.collector.worker.segment.entity.Span;
import org.skywalking.apm.collector.worker.segment.entity.tag.Tags;

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
