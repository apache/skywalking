package org.apache.skywalking.banyandb.client.request;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.apache.skywalking.banyandb.Query;

@Builder
@Data
public class TraceSearchRequest {
    private final TimeRange timeRange;

    // (min|max)duration
    private final long maxDuration;
    private final long minDuration;

    // indexed fields
    private final String endpointName;
    private final String serviceId;
    private final String serviceInstanceId;
    private final String endpointId;

    // paging parameters: limit & offset
    private final int limit;
    private final int offset;

    // traceState
    private final TraceState traceState;
    // queryOrder
    private final String queryOrderField;
    private final SortOrder queryOrderSort;

    @Builder
    @Data
    public static class TimeRange {
        private final long startTime;
        private final long endTime;
    }

    public enum SortOrder {
        DESC(Query.QueryOrder.Sort.SORT_DESC), ASC(Query.QueryOrder.Sort.SORT_ASC);

        @Getter
        private final Query.QueryOrder.Sort sort;

        SortOrder(Query.QueryOrder.Sort sort) {
            this.sort = sort;
        }
    }

    public enum TraceState {
        ALL(0), SUCCESS(1), ERROR(2);

        @Getter
        private final int state;

        TraceState(int state) {
            this.state = state;
        }
    }
}
