package com.a.eye.skywalking.plugin.jedis.v2;

import com.a.eye.skywalking.api.IBuriedPointType;

public enum  RedisBuriedPointType implements IBuriedPointType {
	INSTANCE;

	@Override
	public String getTypeName() {
		return "Redis";
	}

	@Override
	public CallType getCallType() {
		return CallType.SYNC;
	}

}
