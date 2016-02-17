package com.ai.cloud.skywalking.buriedpoint;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.api.IBuriedPointReceiver;
import com.ai.cloud.skywalking.buffer.ContextBuffer;
import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.util.ContextGenerator;

public class RPCBuriedPointReceiver extends ApplicationExceptionHandler implements IBuriedPointReceiver {

	private static Logger logger = LogManager.getLogger(RPCBuriedPointReceiver.class);

    public void afterReceived() {
        if (!AuthDesc.isAuth())
            return;

        // 获取上下文的栈顶中的元素
        Span spanData = Context.removeLastSpan();
        // 填上必要信息
        spanData.setCost(System.currentTimeMillis() - spanData.getStartDate());
        // 存放到本地发送进程中
        ContextBuffer.save(spanData);
    }

    public void beforeReceived(ContextData context, Identification id) {
        if (!AuthDesc.isAuth())
            return;

        Span spanData = ContextGenerator.generateSpanFromContextData(context, id);
        //设置是否为接收端
        spanData.setReceiver(true);

        if (Config.BuriedPoint.PRINTF) {
            logger.debug("TraceId:" + spanData.getTraceId() + "\tviewpointId:" + spanData.getViewPointId() + "\tParentLevelId:" + spanData.
                    getParentLevel() + "\tLevelId:" + spanData.getLevelId());
        }

        Context.append(spanData);
    }
}
