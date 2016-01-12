package com.ai.cloud.skywalking.util;

import java.util.UUID;

import com.ai.cloud.skywalking.conf.Constants;

public final class TraceIdGenerator {
    private static final ThreadLocal<Integer> ThreadTraceIdSequence = new ThreadLocal<Integer>();

    private static final String PROCESS_UUID;

    static {
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        PROCESS_UUID = uuid.substring(uuid.length() - 7);
    }

    private TraceIdGenerator() {
    }

    public static String generate() {
        Integer seq = ThreadTraceIdSequence.get();
        if (seq == null || seq == 10000 || seq > 10000) {
            seq = 0;
        }
        seq++;
        ThreadTraceIdSequence.set(seq);

        return Constants.SDK_VERSION + "." + System.currentTimeMillis()
        		 + "."+ PROCESS_UUID
        		 + "."+ BuriedPointMachineUtil.getProcessNo()
                 + "."+ Thread.currentThread().getId()
                 + "."+ seq;
    }
}
