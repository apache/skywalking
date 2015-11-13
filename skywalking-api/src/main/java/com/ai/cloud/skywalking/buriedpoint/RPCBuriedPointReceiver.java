package com.ai.cloud.skywalking.buriedpoint;

import com.ai.cloud.skywalking.api.IBuriedPointReceiver;
import com.ai.cloud.skywalking.buffer.ContextBuffer;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.context.Span;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.util.ContextGenerator;
import com.ai.cloud.skywalking.util.ExceptionHandleUtil;

public class RPCBuriedPointReceiver implements IBuriedPointReceiver {

    public void afterReceived() {
        // 获取上下文的栈顶中的元素
        Span spanData =  Context.removeLastSpan();
        // 填上必要信息
        spanData.setCost(System.currentTimeMillis() - spanData.getStartDate());
        // 存放到本地发送进程中
        ContextBuffer.save(spanData);
    }

    public void beforeReceived(ContextData context) {
        Span spanData = ContextGenerator.generateContextFromContextData(context);
        // 存放到上下文
        if (Config.BuriedPoint.PRINTF) {
            System.out.println("viewpointId:" + spanData.getViewPointId() + "\tParentLevelId:" + spanData.
                    getParentLevel() + "\tLevelId:" + spanData.getLevelId());
        }
        Context.append(spanData);
    }

    public void handleException(Throwable e) {
        ExceptionHandleUtil.handleException(e);
    }
}
