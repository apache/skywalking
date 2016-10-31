package com.a.eye.skywalking.plugin.motan;

import com.a.eye.skywalking.model.Identification;
import com.weibo.api.motan.rpc.Request;
import com.weibo.api.motan.rpc.URL;

import java.util.Map;

public class IdentificationUtil {
    /**
     * for example:
     * <p>
     * <strong>service URL</strong> : motan://172.18.80.208:0/org.a.eye.skywalking.motan
     * .FooService?group=default_rpc
     * <strong>execute method</strong>  : helloWorld
     * <strong>execute parameter</strong>: java.lang.String,java.lang.String
     * <p>
     * <p>
     * <strong>view point</strong>: motan://172.18.80.208:0/org.a.eye.skywalking.motan.FooService.helloWorld
     * (java.lang.String,java.lang.String)?group=default_rpc
     *
     * @param serviceURI such as: motan://172.18.80.208:0/org.a.eye.skywalking.motan.FooService?group=default_rpc
     * @param request
     * @return such as: motan://172.18.80.208:0/org.a.eye.skywalking.motan.FooService.helloWorld
     * (java.lang.String,java.lang.String)?group=default_rpc
     */
    private static String generateViewPoint(URL serviceURI, Request request) {
        StringBuilder viewPoint = new StringBuilder(serviceURI.getUri());
        viewPoint.append("." + request.getMethodName());
        viewPoint.append("(" + request.getParamtersDesc() + ")?group=" + serviceURI.getGroup());
        return viewPoint.toString();
    }

    /**
     * @param request
     * @param serviceURI such as: motan://172.18.80.208:0/org.a.eye.skywalking.motan.FooService?group=default_rpc
     * @return
     */
    public static Identification generateIdentify(Request request, URL serviceURI) {
        return Identification.newBuilder().viewPoint(generateViewPoint(serviceURI, request))
                .spanType(MotanBuriedPointType.instance()).build();
    }
}
