package com.ai.cloud.skywalking.util;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.context.CurrentThreadSpanStack;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.Identification;
import com.ai.cloud.skywalking.protocol.Span;
import com.ai.cloud.skywalking.protocol.SpanType;

public final class ContextGenerator {
    /**
     * 利用本地ThreadLocal的信息创建Context，主要用于非跨JVM的操作
     *
     * @param id 视点，业务数据等信息
     * @return
     */
    public static Span generateSpanFromThreadLocal(Identification id) {
        Span spanData = getSpanFromThreadLocal();
        initNewSpanData(spanData, id);
        return spanData;
    }

    /**
     * 利用传入的Context对象，来构建相对应的Context信息，主要用于跨JVM的操作信息
     * 跨JVM会产生两条记录。
     *
     * @param context
     * @return
     */
    public static Span generateSpanFromContextData(ContextData context, Identification id) {
        Span spanData;
        // 校验传入的参数是否为空，如果为空，则新创建一个
        if (context == null || StringUtil.isEmpty(context.getTraceId())) {
            // 不存在，新创建一个Context
            spanData = new Span(TraceIdGenerator.generate(), Config.SkyWalking.APPLICATION_CODE, Config.SkyWalking.USER_ID);
        } else {
            // 如果不为空，则将当前的Context存放到上下文
            spanData = new Span(context.getTraceId(), context.getParentLevel(), context.getLevelId(), Config.SkyWalking.APPLICATION_CODE, Config.SkyWalking.USER_ID);
        }
        initNewSpanData(spanData, id);

        return spanData;
    }

    private static void initNewSpanData(Span spanData, Identification id) {
        spanData.setSpanTypeDesc(id.getSpanTypeDesc());
        spanData.setViewPointId(id.getViewPoint());
        spanData.setBusinessKey(id.getBusinessKey());
        //FIX Add Call Type field
        spanData.setCallType(id.getCallType());
        // 设置基本信息
        spanData.setStartDate(System.currentTimeMillis());
        spanData.setProcessNo(BuriedPointMachineUtil.getProcessNo());
        spanData.setAddress(BuriedPointMachineUtil.getHostDesc());
    }

    private static Span getSpanFromThreadLocal() {
        Span span;
        // 1.获取Context，从ThreadLocal栈中获取中
        final Span parentSpan = CurrentThreadSpanStack.peek();
        // 2 校验Context，Context是否存在
        if (parentSpan == null) {
            // 不存在，新创建一个Context
            span = new Span(TraceIdGenerator.generate(), Config.SkyWalking.APPLICATION_CODE, Config.SkyWalking.USER_ID);
        } else {


            // 根据ParentContextData的TraceId和RPCID
            // LevelId是由SpanNode类的nextSubSpanLevelId字段进行初始化的.
            // 所以在这里不需要初始化
            span = new Span(parentSpan.getTraceId(), Config.SkyWalking.APPLICATION_CODE, Config.SkyWalking.USER_ID);

            // check parent span is RPC span
            // if true, current span is invalidate and current span also belong to RPC span
            if (parentSpan.isRPCClientSpan()) {
                span.setSpanType(SpanType.RPC_CLIENT);
                span.setIsInvalidate(true);
            }

            if (!StringUtil.isEmpty(parentSpan.getParentLevel())) {
                span.setParentLevel(parentSpan.getParentLevel() + "." + parentSpan.getLevelId());
            } else {
                span.setParentLevel(String.valueOf(parentSpan.getLevelId()));
            }
        }
        return span;
    }

}
