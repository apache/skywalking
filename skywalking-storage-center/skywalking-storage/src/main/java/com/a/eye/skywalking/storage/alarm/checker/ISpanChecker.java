package com.a.eye.skywalking.storage.alarm.checker;

import com.a.eye.skywalking.storage.data.spandata.AckSpanData;

/**
 * Created by xin on 2016/12/8.
 */
public interface ISpanChecker {
    CheckResult check(AckSpanData span);
}
