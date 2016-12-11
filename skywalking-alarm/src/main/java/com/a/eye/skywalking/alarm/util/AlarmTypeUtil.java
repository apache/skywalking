package com.a.eye.skywalking.alarm.util;

import com.a.eye.skywalking.alarm.model.AlarmType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class AlarmTypeUtil {

    private static Logger logger = LogManager.getLogger(AlarmTypeUtil.class);
    private static List<AlarmType> alarmTypeList;

    static {
        try {
            alarmTypeList = new ArrayList<AlarmType>();
            alarmTypeList.add(new AlarmType("default", "exception", "System Exception"));
            alarmTypeList.add(new AlarmType("ExecuteTime-PossibleError", "remark", "Excution Time > 5s"));
            alarmTypeList.add(new AlarmType("ExecuteTime-Warning", "remark", "Excution Time > 500ms"));
        } catch (Exception e) {
            logger.error("Failed to load alarm type info.", e);
            System.exit(-1);
        }
    }

    public static List<AlarmType> getAlarmTypeList() {

        if (alarmTypeList == null || alarmTypeList.isEmpty()) {
            alarmTypeList = new ArrayList<AlarmType>();
        }

        return alarmTypeList;
    }
}
