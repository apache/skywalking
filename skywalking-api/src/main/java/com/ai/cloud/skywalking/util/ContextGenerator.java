package com.ai.cloud.skywalking.util;

import com.ai.cloud.skywalking.context.Context;
import com.ai.cloud.skywalking.context.Span;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.Identification;

public final class ContextGenerator {
    /**
     * 利用本地ThreadLocal的信息创建Context，主要用于非跨JVM的操作
     *
     * @param sendData 视点，业务数据等信息
     * @return
     */
    public static Span generateContextFromThreadLocal(Identification sendData) {
        Span spanData = getContextFromThreadLocal();
        // 设置基本属性
        spanData.setStartDate(System.currentTimeMillis());
        spanData.setProcessNo(BuriedPointMachineUtil.getProcessNo());
        spanData.setAddress(BuriedPointMachineUtil.getHostName() + "/" + BuriedPointMachineUtil.getHostIp());
        spanData.setViewPointId(sendData.getViewPoint());
        return spanData;
    }

    /**
     * 利用传入的Context对象，来构建相对应的Context信息，主要用于跨JVM的操作信息
     * 跨JVM会产生两条记录。
     *
     * @param context
     * @return
     */
    public static Span generateContextFromContextData(ContextData context) {
        Span spanData;
        // 校验传入的参数是否为空，如果为空，则新创建一个
        if (context == null || StringUtil.isEmpty(context.getTraceId())) {
            // 不存在，新创建一个Context
            spanData = new Span(TraceIdGenerator.generate());
           // spanData.setLevelId(0L);
        } else {
            // 如果不为空，则将当前的Context存放到上下文
            spanData = new Span(context.getTraceId());
            spanData.setParentLevel(context.getParentLevel());
        }
        // 设置基本信息
        spanData.setStartDate(System.currentTimeMillis());
        spanData.setProcessNo(BuriedPointMachineUtil.getProcessNo());
        spanData.setAddress(BuriedPointMachineUtil.getHostName() + "/" + BuriedPointMachineUtil.getHostIp());
        return spanData;
    }

    private static Span getContextFromThreadLocal() {
        Span span;
        // 1.获取Context，从ThreadLocal栈中获取中
        final Span parentSpan =  Context.getLastSpan();
        // 2 校验Context，Context是否存在
        if (parentSpan == null) {
            // 不存在，新创建一个Context
            span = new Span(TraceIdGenerator.generate());
        } else {
            // 根据ParentContextData的TraceId和RPCID
            span = new Span(parentSpan.getTraceId());
            span.setParentLevel(parentSpan.getParentLevel() + "." + parentSpan.getLevelId());
        }
        return span;
    }

}
