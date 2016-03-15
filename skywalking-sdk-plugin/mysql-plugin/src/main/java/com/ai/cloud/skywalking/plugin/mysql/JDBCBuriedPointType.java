package com.ai.cloud.skywalking.plugin.mysql;

import com.ai.cloud.skywalking.api.IBuriedPointType;
import com.ai.cloud.skywalking.protocol.CallType;

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

    private JDBCBuriedPointType(){
        //Non
    }
}
