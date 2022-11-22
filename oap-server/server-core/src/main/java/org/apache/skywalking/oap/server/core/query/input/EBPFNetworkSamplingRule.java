package org.apache.skywalking.oap.server.core.query.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EBPFNetworkSamplingRule {
    // The match pattern for HTTP request. This is HTTP URI-oriented.
    // Matches all requests if not set
    private String uriRegex;

    // The minimal request duration to activate the network data(HTTP request/response raw data) sampling.
    // Collecting requests without minimal request duration.
    private Integer minDuration;
    // Collecting requests when the response code is 400-499.
    private boolean when4xx;
    // Collecting requests when the response code is 500-599
    private boolean when5xx;

    // Define how to collect sampled data
    private EBPFNetworkDataCollectingSettings settings;
}
