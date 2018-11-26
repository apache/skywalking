package org.apache.skywalking.apm.webapp.security;


import com.google.gson.Gson;
import org.apache.commons.lang.StringUtils;
import org.jasig.cas.client.util.AssertionHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by moruwen on 2018/11/7.
 */
public class PermissionFilter implements Filter {
    private static Logger logger = LoggerFactory.getLogger(PermissionFilter.class);

    private Set<String> excludesPathSet = new HashSet<>();

    public void destroy() {
    }

    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws ServletException, IOException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        String ssoUserName = AssertionHolder.getAssertion().getPrincipal().getName();
        String path = request.getServletPath();
        logger.info("用户[{}]请求访问[{}]", ssoUserName, path);

        if (StringUtils.isEmpty(ssoUserName)) {
            logger.warn("用户未登录，无权访问[{}]", path);
            buildResponse(response, "用户未登录，无权访问" + path);
            return;
        }
        if (ssoUserName.equalsIgnoreCase("qibaichao")) {
            logger.warn("您没有访问此链接权限{}", path);
            buildResponse(response, "您没有访问此链接权限:" + path);
            return;
        }

        chain.doFilter(request, response);
    }

    public void init(FilterConfig config) throws ServletException {
        excludesPathSet.add("/index.htm");
    }

    private void buildResponse(HttpServletResponse response, String message) throws IOException {
        Map<String, String> messageMap = new HashMap<>();
        messageMap.put("message", message);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        Gson gson = new Gson();
        response.getWriter().println(gson.toJson(messageMap));
        response.getWriter().close();
    }

//    private String generateNavHeader(List<SysAppVo> sysAppList) {
//        StringBuffer buffer = new StringBuffer();
//        if (sysAppList != null) {
//            for (SysAppVo system : sysAppList) {
//                buffer.append(" <li style=\"padding: 20px;\">");
//                buffer.append("<a href=\"http://" + system.getDomain() + "\">" + system.getAppName() + "</a>");
//                buffer.append("</li>");
//            }
//        }
//        return buffer.toString();
//    }
}
