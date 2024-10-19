package org.apache.skywalking.oap.server.core.query.input;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.JFREventType;

import java.util.List;

@Getter
@Setter
public class AsyncProfilerAnalyzatonRequest {
    private String taskId;
    private List<String> instanceIds;
    private JFREventType eventType;
}
