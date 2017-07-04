package org.skywalking.apm.collector.worker.segment.entity;

import com.sun.org.apache.xml.internal.security.utils.Base64;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.network.proto.TraceSegmentObject;

/**
 * The <code>SegmentDeserialize</code> provides single segment json string deserialize and segment array file
 * deserialize.
 *
 * @author pengys5
 * @since v3.0-2017
 */
public enum SegmentDeserialize {
    INSTANCE;

    private final Logger logger = LogManager.getFormatterLogger(SegmentDeserialize.class);

    /**
     * Segment object binary value as a base64 encoded string deserialize.
     *
     * @param segmentObjBlob , to be a binary value as a base64 encoded string
     * @return an {@link TraceSegmentObject}
     */
    public TraceSegmentObject deserializeSingle(String segmentObjBlob) {
        try {
            byte[] decode = Base64.decode(segmentObjBlob);
            return TraceSegmentObject.parseFrom(decode);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }
}
