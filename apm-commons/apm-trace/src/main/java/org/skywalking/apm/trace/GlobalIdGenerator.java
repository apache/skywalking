package org.skywalking.apm.trace;

import org.skywalking.apm.api.util.MachineInfo;
import org.skywalking.apm.api.util.StringUtil;

import java.util.UUID;

public final class GlobalIdGenerator {
    private static final ThreadLocal<Integer> THREAD_ID_SEQUENCE = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    private static final int PROCESS_UUID;

    static {
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        PROCESS_UUID = uuid.substring(uuid.length() - 7).hashCode();
    }

    private GlobalIdGenerator() {
    }

    public static String generate(String type) {
        Integer seq = THREAD_ID_SEQUENCE.get();
        seq++;
        THREAD_ID_SEQUENCE.set(seq);

        return StringUtil.join('.',
            type + "", System.currentTimeMillis() + "", PROCESS_UUID + "",
            MachineInfo.getProcessNo() + "", Thread.currentThread().getId() + "", seq + "");
    }
}
