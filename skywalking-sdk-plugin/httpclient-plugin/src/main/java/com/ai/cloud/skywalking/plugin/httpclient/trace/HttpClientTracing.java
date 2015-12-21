package com.ai.cloud.skywalking.plugin.httpclient.trace;

import com.ai.cloud.skywalking.buriedpoint.RPCBuriedPointSender;
import com.ai.cloud.skywalking.model.Identification;
import org.apache.http.HttpRequest;

import java.io.IOException;

public class HttpClientTracing {

    private static RPCBuriedPointSender sender = new RPCBuriedPointSender();

    public static <R> R execute(String url, String traceHearName, HttpRequest httpRequest, Executor<R> executor) throws IOException {
        try {
            httpRequest.setHeader(traceHearName,
                    "ContextData=" + sender.beforeSend(Identification.newBuilder()
                            .viewPoint(url)
                            .spanType("W")
                            .build())
                            .toString());
            return executor.execute();
        } catch (IOException e) {
            sender.handleException(e);
            throw e;
        } finally {
            sender.afterSend();
        }
    }

    public interface Executor<R> {
        R execute() throws IOException;
    }


}
