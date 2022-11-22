package org.apache.skywalking.oap.server.core.query.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EBPFNetworkDataCollectingSettings {
    // Require to collect the complete request
    private boolean requireCompleteRequest;
    // The max size of request context. The unit is byte.
    // Collect the whole request header and body if this is not set.
    private Integer maxRequestSize;

    // Require to collect the complete response
    private boolean requireCompleteResponse;
    // The max size of response context. The unit is byte.
    // Collect the whole response header and body if this is not set.
    private Integer maxResponseSize;
}