package org.skywalking.apm.collector.worker.tools;

import org.skywalking.apm.network.proto.SpanObject;

/**
 * @author pengys5
 */
public enum SpanPeersTools {
    INSTANCE;

    public int getPeers(SpanObject span) {
        if (span.getPeerId() == 0) {
            return 0; //TODO exchange peer to peer id
        } else {
            return span.getPeerId();
        }
    }
}
