package com.ai.cloud.skywalking.buriedpoint;

import static com.ai.cloud.skywalking.conf.Config.BuriedPoint.EXCLUSIVE_EXCEPTIONS;

import java.util.HashSet;
import java.util.Set;


import com.ai.cloud.skywalking.api.IExceptionHandler;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.protocol.Span;

public class ApplicationExceptionHandler implements IExceptionHandler {
	private static Logger logger = LogManager
			.getLogger(ApplicationExceptionHandler.class);

	private static String EXCEPTION_SPLIT = ",";

	private static Set<String> exclusiveExceptionSet = null;

	@Override
	public void handleException(Throwable th) {
		try {
			if (exclusiveExceptionSet == null) {
				Set<String> exclusiveExceptions = new HashSet<String>();

				String[] exceptions = EXCLUSIVE_EXCEPTIONS
						.split(EXCEPTION_SPLIT);
				for (String exception : exceptions) {
					exclusiveExceptions.add(exception);
				}
				exclusiveExceptionSet = exclusiveExceptions;
			}

			Span span = Context.getLastSpan();
			span.handleException(th, exclusiveExceptionSet,
					Config.BuriedPoint.MAX_EXCEPTION_STACK_LENGTH);
		} catch (Throwable t) {
			logger.error(t.getMessage(), t);
		}
	}

}
