package com.ai.cloud.skywalking.buriedpoint;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.api.IBuriedPointSender;
import com.ai.cloud.skywalking.buffer.ContextBuffer;
import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.EmptyContextData;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.util.ContextGenerator;

public class LocalBuriedPointSender extends ApplicationExceptionHandler
		implements IBuriedPointSender {

	private static Logger logger = LogManager
			.getLogger(LocalBuriedPointSender.class);

	public ContextData beforeSend(Identification id) {
		try {
			if (!AuthDesc.isAuth())
				return new EmptyContextData();

			Span spanData = ContextGenerator.generateSpanFromThreadLocal(id);
			// 将新创建的Context存放到ThreadLocal栈中。
			Context.append(spanData);
			// 并将当前的Context返回回去
			return new ContextData(spanData);
		} catch (Throwable t) {
			logger.error(t.getMessage(), t);
			return new EmptyContextData();
		}
	}

	public void afterSend() {
		try {
			if (!AuthDesc.isAuth())
				return;

			// 弹出上下文的栈顶中的元素
			Span spanData = Context.removeLastSpan();
			if (spanData == null) {
				return;
			}

			// 加上花费时间
			spanData.setCost(System.currentTimeMillis()
					- spanData.getStartDate());

			if (Config.BuriedPoint.PRINTF) {
				logger.debug("TraceId:" + spanData.getTraceId()
						+ "\tviewpointId:" + spanData.getViewPointId()
						+ "\tParentLevelId:" + spanData.getParentLevel()
						+ "\tLevelId:" + spanData.getLevelId()
						+ "\tbusinessKey:" + spanData.getBusinessKey());
			}

			// 存放到本地发送进程中
			if (!Config.Sender.IS_OFF) {
				ContextBuffer.save(spanData);
			}
		} catch (Throwable t) {
			logger.error(t.getMessage(), t);
		}
	}
}
