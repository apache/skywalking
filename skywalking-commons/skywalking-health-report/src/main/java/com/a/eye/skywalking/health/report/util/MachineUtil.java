package com.a.eye.skywalking.health.report.util;

import java.lang.management.ManagementFactory;

/**
 * Created by xin on 2016/11/14.
 */

public class MachineUtil {
    private static String processNo;

    static {
        processNo = getProcessNo();
    }

    public static String getProcessNo() {
        if (processNo == null) {
            String name = ManagementFactory.getRuntimeMXBean().getName();
            processNo = name.split("@")[0];
        }
        return processNo;
    }

    private MachineUtil() {
        // Non
    }
}
