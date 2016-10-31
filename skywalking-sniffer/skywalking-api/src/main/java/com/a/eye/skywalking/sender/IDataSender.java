package com.a.eye.skywalking.sender;

import com.a.eye.skywalking.protocol.common.ISerializable;

import java.util.List;

public interface IDataSender {
	public boolean send(List<ISerializable> data);
}
