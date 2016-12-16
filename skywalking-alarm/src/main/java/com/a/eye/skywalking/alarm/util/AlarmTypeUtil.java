package com.a.eye.skywalking.alarm.util;

import com.a.eye.skywalking.alarm.model.AlarmType;

import java.util.ArrayList;
import java.util.List;

public class AlarmTypeUtil {
    private static List<AlarmType> alarmTypeList;

    public static List<AlarmType> getAlarmTypeList() {

        if (alarmTypeList == null || alarmTypeList.isEmpty()) {
            alarmTypeList = new ArrayList<AlarmType>();
            alarmTypeList.add(new AlarmType("default", "exception", "System Exception"));
            alarmTypeList.add(new AlarmType("ExecuteTime-PossibleError", "remark", "Excution Time > 5s"));
            alarmTypeList.add(new AlarmType("ExecuteTime-Warning", "remark", "Excution Time > 500ms"));
        }

        return alarmTypeList;
    }
}
