package org.apache.skywalking.oap.server.core.source;

import lombok.Data;
import org.apache.skywalking.oap.server.library.jfr.parser.convert.FrameTree;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.JFREventType;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.JFR_PROFILING_DATA;

@Data
@ScopeDeclaration(id = JFR_PROFILING_DATA, name = "JFRProfilingData")
@ScopeDefaultColumn.VirtualColumnDefinition(fieldName = "entityId", columnName = "entity_id", isID = true, type = String.class)
public class JFRProfilingData extends Source {
    private volatile String entityId;

    @Override
    public int scope() {
        return JFR_PROFILING_DATA;
    }

    @Override
    public String getEntityId() {
        if (entityId == null) {
            return taskId + instanceId + eventType.name() + uploadTime;
        }
        return entityId;
    }

    private String taskId;
    private String instanceId;
    private long uploadTime;
    private JFREventType eventType;
    private FrameTree frameTree;
}
