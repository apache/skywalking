package org.skywalking.apm.collector.worker.instance.util;

public enum IDSequenceCache {
    INSTANCE;

    State state;
    IDSequence idSequence;

    static {
        IDSequenceFetcher.INSTANCE.fetchSequence(new IDSequenceFetcher.Listener() {
            @Override
            public void failed() {
                INSTANCE.state = State.ABNORMAL;
                IDSequenceFetcher.INSTANCE.fetchSequenceInBackGround(INSTANCE);
            }

            @Override
            public void success(IDSequence idSequence) {
                INSTANCE.idSequence = idSequence;
                INSTANCE.state = State.NORMAL;
            }
        });
    }

    public long fetchInstanceId() {
        synchronized (INSTANCE) {
            if (state == State.ABNORMAL) {
                return -1;
            }

            if (!idSequence.hasNext()) {
                IDSequenceFetcher.INSTANCE.fetchSequence(new IDSequenceFetcher.Listener() {
                    @Override
                    public void failed() {
                        state = State.ABNORMAL;
                        IDSequenceFetcher.INSTANCE.fetchSequenceInBackGround(INSTANCE);
                    }

                    @Override
                    public void success(IDSequence idSequence) {
                        IDSequenceCache.this.idSequence = idSequence;
                    }
                });
            }

            return idSequence.nextInstanceId();
        }
    }

    public enum State {
        NORMAL,
        ABNORMAL
    }
}
