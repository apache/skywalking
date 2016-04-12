package com.ai.cloud.skywalking.reciever.storage.chain.alarm;

import com.ai.cloud.skywalking.protocol.Span;

public interface ISpanChecker {
	void check(Span span);
}
