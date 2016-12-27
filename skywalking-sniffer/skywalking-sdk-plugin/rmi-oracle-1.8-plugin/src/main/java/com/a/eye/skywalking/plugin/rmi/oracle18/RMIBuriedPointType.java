package com.a.eye.skywalking.plugin.rmi.oracle18;

import com.a.eye.skywalking.api.IBuriedPointType;

/**
 * Created by xin on 2016/12/22.
 */
public class RMIBuriedPointType implements IBuriedPointType{

    public static RMIBuriedPointType INSTANCE = new RMIBuriedPointType();

    private RMIBuriedPointType(){

    }

    @Override
    public String getTypeName() {
        return "R";
    }

    @Override
    public CallType getCallType() {
       return CallType.SYNC;
    }
}
