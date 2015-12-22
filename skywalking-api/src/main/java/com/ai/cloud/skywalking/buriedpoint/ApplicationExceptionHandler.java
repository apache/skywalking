package com.ai.cloud.skywalking.buriedpoint;

import java.util.ArrayList;
import java.util.List;

import com.ai.cloud.skywalking.api.IExceptionHandler;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.protocol.Span;

public class ApplicationExceptionHandler implements IExceptionHandler {
	private static Boolean isExclusiveExceptionListInit = false;

	private static List<String> exclusiveExceptionList = new ArrayList<String>();

	@Override
	public void handleException(Throwable th) {
		if (isExclusiveExceptionListInit == false)
			synchronized (isExclusiveExceptionListInit) {
				if (isExclusiveExceptionListInit == false) {

					isExclusiveExceptionListInit = true;
				}
			}

		Span span = Context.getLastSpan();
		span.handleException(th, exclusiveExceptionList, Config.BuriedPoint.MAX_EXCEPTION_STACK_LENGTH);
	}

}
