package com.a.eye.skywalking.plugin.jedis.v2;

import com.a.eye.skywalking.api.IBuriedPointType;

public class RedisBuriedPointType implements IBuriedPointType {
	 private static RedisBuriedPointType redisBuriedPointType;
	 
	 public static IBuriedPointType instance() {
	        if (redisBuriedPointType == null) {
	        	redisBuriedPointType = new RedisBuriedPointType();
	        }

	        return redisBuriedPointType;
	    }
	

	@Override
	public String getTypeName() {
		return "Redis";
	}

	@Override
	public CallType getCallType() {
		return CallType.SYNC;
	}

	private RedisBuriedPointType(){}
}
