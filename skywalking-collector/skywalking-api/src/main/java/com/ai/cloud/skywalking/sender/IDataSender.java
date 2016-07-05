package com.ai.cloud.skywalking.sender;

import com.ai.cloud.skywalking.protocol.common.ISerializable;

import java.util.List;

public interface IDataSender {
	public boolean send(List<ISerializable> data);
}
