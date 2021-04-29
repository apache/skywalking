/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.core.alarm.provider.welink;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.core.alarm.provider.Rules;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class WeLinkHookCallbackTest implements Servlet {

    private Server server;
    private int port;
    private volatile boolean isSuccess = false;
    private int count;

    @Before
    public void init() throws Exception {
        server = new Server(new InetSocketAddress("127.0.0.1", 0));
        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletContextHandler.setContextPath("/welinkhook");
        server.setHandler(servletContextHandler);
        ServletHolder servletHolder = new ServletHolder();
        servletHolder.setServlet(this);
        servletContextHandler.addServlet(servletHolder, "/api/auth/v2/tickets");
        servletContextHandler.addServlet(servletHolder, "/api/welinkim/v1/im-service/chat/group-chat");
        server.start();
        port = server.getURI().getPort();
        assertTrue(port > 0);
    }

    @Test
    public void testWeLinkDoAlarm() {
        List<WeLinkSettings.WebHookUrl> webHooks = new ArrayList<>();
        webHooks.add(new WeLinkSettings.WebHookUrl("clientId", "clientSecret",
                                                   "http://127.0.0.1:" + port + "/welinkhook/api/auth/v2/tickets",
                                                   "http://127.0.0.1:" + port + "/welinkhook/api/welinkim/v1/im-service/chat/group-chat",
                                                   "robotName", "1,2,3"
        ));
        Rules rules = new Rules();
        String template = "Apache SkyWalking Alarm: \n %s.";
        rules.setWelinks(WeLinkSettings.builder().webhooks(webHooks).textTemplate(template).build());

        AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(rules, null);
        WeLinkHookCallback welinkHookCallback = new WeLinkHookCallback(alarmRulesWatcher);
        List<AlarmMessage> alarmMessages = new ArrayList<>(2);
        AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessage.setScopeId(DefaultScopeDefine.ALL);
        alarmMessage.setRuleName("service_resp_time_rule");
        alarmMessage.setAlarmMessage("alarmMessage with [DefaultScopeDefine.All]");
        alarmMessages.add(alarmMessage);
        AlarmMessage anotherAlarmMessage = new AlarmMessage();
        anotherAlarmMessage.setRuleName("service_resp_time_rule_2");
        anotherAlarmMessage.setScopeId(DefaultScopeDefine.ENDPOINT);
        anotherAlarmMessage.setAlarmMessage("anotherAlarmMessage with [DefaultScopeDefine.Endpoint]");
        alarmMessages.add(anotherAlarmMessage);
        welinkHookCallback.doAlarm(alarmMessages);
        Assert.assertTrue(isSuccess);
    }

    @After
    public void stop() throws Exception {
        server.stop();
    }

    @Override
    public void init(ServletConfig servletConfig) {
    }

    @Override
    public ServletConfig getServletConfig() {
        return null;
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) throws IOException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        if (httpServletRequest.getContentType().equals("application/json")) {
            InputStream inputStream = request.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int readCntOnce;

            while ((readCntOnce = inputStream.read(buffer)) >= 0) {
                out.write(buffer, 0, readCntOnce);
            }
            JsonObject jsonObject = new Gson().fromJson(new String(out.toByteArray()), JsonObject.class);

            if (count == 0) {
                String clientId = jsonObject.get("client_id").getAsString();
                count = clientId == null ? count : count + 1;
                ((HttpServletResponse) response).setStatus(200);
            } else if (count >= 1) {
                String appMsgId = jsonObject.get("app_msg_id").getAsString();
                count = appMsgId == null ? count : count + 1;
                ((HttpServletResponse) response).setStatus(200);
            } else {
                ((HttpServletResponse) response).setStatus(500);
            }
            if (count == 2) {
                isSuccess = true;
            }
        }
    }

    @Override
    public String getServletInfo() {
        return null;
    }

    @Override
    public void destroy() {
    }

}
