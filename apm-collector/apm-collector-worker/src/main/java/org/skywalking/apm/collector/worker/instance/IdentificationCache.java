package org.skywalking.apm.collector.worker.instance;

class IdentificationCache {
    private static final IdentificationCache INSTANCE = new IdentificationCache();

    State state;
    IdentificationSegment segment;

    private IdentificationCache() {
        state = State.NORMAL;
    }

    public synchronized long fetchInstanceId() {
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

    public static IdentificationCache initCache() {
        IdentificationSegmentFetcher.INSTANCE.fetchSegment(new IdentificationSegmentFetcher.Listener() {
            @Override
            public void failed() {
                INSTANCE.state = State.ABNORMAL;
                IdentificationSegmentFetcher.INSTANCE.fetchSegmentInBackGround(INSTANCE);
            }

            @Override
            public void success(IdentificationSegment idSegment) {
                INSTANCE.segment = idSegment;
            }
        });

        return INSTANCE;
    }

    enum State {
        NORMAL,
        ABNORMAL
    }
}
