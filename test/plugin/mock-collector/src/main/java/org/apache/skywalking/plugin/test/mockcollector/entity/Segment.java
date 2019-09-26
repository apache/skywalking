package org.apache.skywalking.plugin.test.mockcollector.entity;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.ToString;
import org.apache.skywalking.apm.network.language.agent.UniqueId;

@ToString
@Builder
@AllArgsConstructor
public class Segment {
    private String segmentId;
    private List<Span> spans;

    public static class SegmentBuilder {

        public SegmentBuilder addSpan(Span.SpanBuilder spanBuilder) {
            if (spans == null) {
                this.spans = new ArrayList<>();
            }

            spans.add(spanBuilder.build());
            return this;
        }

        public SegmentBuilder segmentId(UniqueId segmentUniqueId) {
            segmentId = String.join(".", Long.toString(segmentUniqueId.getIdParts(0)), Long.toString(segmentUniqueId.getIdParts(1)), Long.toString(segmentUniqueId.getIdParts(2)));
            return this;
        }
    }

}
