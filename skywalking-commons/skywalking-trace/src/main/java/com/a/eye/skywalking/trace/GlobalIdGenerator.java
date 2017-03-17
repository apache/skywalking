package com.a.eye.skywalking.trace;

import com.a.eye.skywalking.api.util.MachineInfo;
import com.a.eye.skywalking.api.util.StringUtil;
import java.util.UUID;

public final class GlobalIdGenerator {
    private static final ThreadLocal<Integer> ThreadTraceIdSequence = new ThreadLocal<Integer>() {
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

    /**
     * TraceId由以下规则组成<br/>
     * ID类型 + 1位时间戳（毫秒数） + 1位进程随机号（UUID后7位） + 1位进程数号 + 1位线程号 + 1位线程内序号
     * <p>
     * 注意：这里的位，是指“.”作为分隔符所占的位数，非字符串长度的位数。
     * TraceId为6个片段组成的数组
     *
     * @return
     */
    public static String generate(String type) {
        Integer seq = ThreadTraceIdSequence.get();
        seq++;
        ThreadTraceIdSequence.set(seq);

        return StringUtil.join('.',
            type + "", System.currentTimeMillis() + "", PROCESS_UUID + "",
            MachineInfo.getProcessNo() + "", Thread.currentThread().getId() + "", seq + "");
    }
}
