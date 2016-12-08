package com.a.eye.skywalking.storage.alarm.sender;

/**
 * Created by xin on 2016/12/8.
 */
public class AlarmMessageSenderFactory {

    private static AlarmMessageSender sender = new AlarmMessageSender();

    public static AlarmMessageSender getSender() {
        return sender;
    }
}
