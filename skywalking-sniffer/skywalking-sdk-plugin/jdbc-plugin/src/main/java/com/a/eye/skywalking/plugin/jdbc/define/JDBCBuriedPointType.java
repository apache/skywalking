package com.a.eye.skywalking.plugin.jdbc.define;

import com.a.eye.skywalking.api.IBuriedPointType;
import com.a.eye.skywalking.protocol.common.CallType;

public class JDBCBuriedPointType implements IBuriedPointType {

    private static JDBCBuriedPointType jdbcBuriedPointType;

    public static IBuriedPointType instance() {
        if (jdbcBuriedPointType == null) {
            jdbcBuriedPointType = new JDBCBuriedPointType();
        }

        return jdbcBuriedPointType;
    }


    @Override
    public String getTypeName() {
        return "J";
    }

    @Override
    public CallType getCallType() {
        return CallType.LOCAL;
    }

    private JDBCBuriedPointType() {
        //Non
    }
}
