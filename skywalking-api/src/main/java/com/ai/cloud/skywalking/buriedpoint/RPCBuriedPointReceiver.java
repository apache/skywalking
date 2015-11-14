package com.ai.cloud.skywalking.buriedpoint;

import com.ai.cloud.skywalking.api.IBuriedPointReceiver;
import com.ai.cloud.skywalking.buffer.ContextBuffer;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.context.Span;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.util.ContextGenerator;
import com.ai.cloud.skywalking.util.ExceptionHandleUtil;

import java.util.logging.Level;
import java.util.logging.Logger;

public class RPCBuriedPointReceiver implements IBuriedPointReceiver {

    private static Logger logger = Logger.getLogger(LocalBuriedPointSender.class.getName());

    public void afterReceived() {
        // 获取上下文的栈顶中的元素
        Span spanData = Context.removeLastSpan();
        // 填上必要信息
        spanData.setCost(System.currentTimeMillis() - spanData.getStartDate());
        // 存放到本地发送进程中
        ContextBuffer.save(spanData);
    }

    public void beforeReceived(ContextData context, Identification id) {
        Span spanData = ContextGenerator.generateSpanFromContextData(context, id);
        //设置是否为接收端
        spanData.setReceiver(true);

        if (Config.BuriedPoint.PRINTF) {
            logger.log(Level.INFO, "viewpointId:" + spanData.getViewPointId() + "\tParentLevelId:" + spanData.
                    getParentLevel() + "\tLevelId:" + spanData.getLevelId());
        }

        Context.append(spanData);
    }

    public void handleException(Throwable e) {
        ExceptionHandleUtil.handleException(e);
    }
}
