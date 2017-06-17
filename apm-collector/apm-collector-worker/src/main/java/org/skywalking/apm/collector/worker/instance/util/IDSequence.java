package org.skywalking.apm.collector.worker.instance.util;

public enum IDSequence {
    INSTANCE;

    State state;
    IdentificationSegment segment;

    static {
        IdentificationSegmentFetcher.INSTANCE.fetchSegment(new IdentificationSegmentFetcher.Listener() {
            @Override
            public void failed() {
                INSTANCE.state = State.ABNORMAL;
                IdentificationSegmentFetcher.INSTANCE.fetchSegmentInBackGround(INSTANCE);
            }

            @Override
            public void success(IdentificationSegment idSegment) {
                INSTANCE.segment = idSegment;
                INSTANCE.state = State.NORMAL;
            }
        });
    }

    public long fetchInstanceId() {
        synchronized (INSTANCE) {
            if (state == State.ABNORMAL) {
                return -1;
            }

            if (!segment.hasNext()) {
                IdentificationSegmentFetcher.INSTANCE.fetchSegment(new IdentificationSegmentFetcher.Listener() {
                    @Override
                    public void failed() {
                        state = State.ABNORMAL;
                        IdentificationSegmentFetcher.INSTANCE.fetchSegmentInBackGround(INSTANCE);
                    }

                    @Override
                    public void success(IdentificationSegment idSegment) {
                        segment = idSegment;
                    }
                });
            }

            return segment.nextInstanceId();
        }
    }

    public enum State {
        NORMAL,
        ABNORMAL
    }
}
