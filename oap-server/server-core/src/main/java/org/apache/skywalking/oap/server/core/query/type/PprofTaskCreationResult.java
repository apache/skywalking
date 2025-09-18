package org.apache.skywalking.oap.server.core.query.type;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * create pprof task result
 */
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PprofTaskCreationResult {
    // ErrorReason gives detailed reason for the exception, if the code returned represents a kind of failure.
    private String errorReason;
    // Code defines the status of the response, i.e. success or failure.
    private PprofTaskCreationType code;
    // Task id, if code is SUCCESS.
    private String id;
}
