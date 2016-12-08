package com.a.eye.skywalking.storage.alarm.checker;

import com.a.eye.skywalking.network.grpc.AckSpan;

/**
 * Created by xin on 2016/12/8.
 */
public interface ISpanChecker {
    CheckResult check(AckSpan span);
}
