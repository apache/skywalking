package com.ai.cloud.skywalking.plugin.web;


import com.ai.cloud.skywalking.buriedpoint.RPCBuriedPointReceiver;
import com.ai.cloud.skywalking.model.ContextData;
import com.ai.cloud.skywalking.model.Identification;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class SkyWalkingFilter implements Filter {

    private final String secondKey = "ContextData";
    private String tracingName;
    private static final String DEFAULT_TRACE_NAME = "SkyWalking-TRACING-NAME";

    public void init(FilterConfig filterConfig) throws ServletException {
        tracingName = filterConfig.getInitParameter("tracing-name");
        if (tracingName == null || tracingName.length() <= 0) {
            tracingName = DEFAULT_TRACE_NAME;
        }
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        RPCBuriedPointReceiver receiver = null;
        try {
            HttpServletRequest request = (HttpServletRequest) servletRequest;
            String tracingHeaderValue = request.getHeader(tracingName);
            ContextData contextData = null;
            if (tracingHeaderValue != null) {
                String contextDataStr = null;
                int index = tracingHeaderValue.indexOf("=");
                if (index > 0) {
                    String key = tracingHeaderValue.substring(0, index);
                    if (secondKey.equals(key)) {
                        contextDataStr = tracingHeaderValue.substring(index + 1);
                    }
                }

                if (contextDataStr != null && contextDataStr.length() > 0) {
                    contextData = new ContextData(contextDataStr);
                }
            }
            receiver = new RPCBuriedPointReceiver();
            receiver.beforeReceived(contextData, generateIdentification(request));
            filterChain.doFilter(servletRequest, servletResponse);
        } catch (Throwable e) {
            receiver.handleException(e);
            throw new ServletException(e);
        } finally {
            receiver.afterReceived();
        }

    }


    private Identification generateIdentification(HttpServletRequest request) {
        return Identification.newBuilder()
                .viewPoint(request.getRequestURL().toString())
                .spanType("W")
                .build();
    }

    public void destroy() {
        // do-nothing
    }
}
