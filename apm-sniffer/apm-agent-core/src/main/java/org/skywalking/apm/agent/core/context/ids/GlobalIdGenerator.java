package org.skywalking.apm.agent.core.context.ids;

import org.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.skywalking.apm.agent.core.dictionary.DictionaryUtil;

public final class GlobalIdGenerator {
    private static final ThreadLocal<Integer> THREAD_ID_SEQUENCE = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    private GlobalIdGenerator() {
    }

    /**
     * Generate a new id, combined by three long numbers.
     *
     * The first one represents application instance id. (most likely just an integer value, would be helpful in
     * protobuf)
     *
     * The second one represents thread id. (most likely just an integer value, would be helpful in protobuf)
     *
     * The third one also has two parts,<br/>
     * 1) a timestamp, measured in milliseconds<br/>
     * 2) a seq, in current thread, between 0(included) and 9999(included)
     *
     * Notice, a long costs 8 bytes, three longs cost 24 bytes. And at the same time, a char costs 2 bytes. So
     * sky-walking's old global and segment id like this: "S.1490097253214.-866187727.57515.1.1" which costs at least 72
     * bytes.
     *
     * @return an array contains three long numbers, which represents a unique id.
     */
    public static ID generate() {
        if (RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID == DictionaryUtil.nullValue()) {
            throw new IllegalStateException();
        }
        Integer seq = THREAD_ID_SEQUENCE.get();
        seq++;
        if (seq > 9999) {
            seq = 0;
        }
        THREAD_ID_SEQUENCE.set(seq);

        return new ID(
            RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID,
            Thread.currentThread().getId(),
            System.currentTimeMillis() * 10000 + seq
        );
    }
}
