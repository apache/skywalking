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

package org.apache.skywalking.oap.server.core.alarm.provider.dingtalk;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.skywalking.apm.util.StringUtil;
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

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class DingtalkHookCallbackTest implements Servlet {

    private Server server;
    private int port;
    private volatile boolean isSuccess = false;
    private int count;
    private volatile boolean checkSign = false;
    private final String secret = "dummy-secret";

    @Before
    public void init() throws Exception {
        server = new Server(new InetSocketAddress("127.0.0.1", 0));
        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletContextHandler.setContextPath("/dingtalkhook");
        server.setHandler(servletContextHandler);
        ServletHolder servletHolder = new ServletHolder();
        servletHolder.setServlet(this);
        servletContextHandler.addServlet(servletHolder, "/receiveAlarm");
        server.start();
        port = server.getURI().getPort();
        assertTrue(port > 0);
    }

    @Test
    public void testDingtalkWebhookWithoutSign() {
        List<DingtalkSettings.WebHookUrl> webHooks = new ArrayList<>();
        webHooks.add(new DingtalkSettings.WebHookUrl("", "http://127.0.0.1:" + port + "/dingtalkhook/receiveAlarm?token=dummy_token"));
        Rules rules = new Rules();
        String template = "{\"msgtype\":\"text\",\"text\":{\"content\":\"Skywaling alarm: %s\"}}";
        rules.setDingtalks(DingtalkSettings.builder().webhooks(webHooks).textTemplate(template).build());

        AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(rules, null);
        DingtalkHookCallback dingtalkCallBack = new DingtalkHookCallback(alarmRulesWatcher);
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
        dingtalkCallBack.doAlarm(alarmMessages);
        Assert.assertTrue(isSuccess);
    }

    @Test
    public void testDingtalkWebhookWithSign() {
        checkSign = true;
        List<DingtalkSettings.WebHookUrl> webHooks = new ArrayList<>();
        webHooks.add(new DingtalkSettings.WebHookUrl(secret, "http://127.0.0.1:" + port + "/dingtalkhook/receiveAlarm?token=dummy_token"));
        Rules rules = new Rules();
        String template = "{\"msgtype\":\"text\",\"text\":{\"content\":\"Skywaling alarm: %s\"}}";
        rules.setDingtalks(DingtalkSettings.builder().webhooks(webHooks).textTemplate(template).build());

        AlarmRulesWatcher alarmRulesWatcher = new AlarmRulesWatcher(rules, null);
        DingtalkHookCallback dingtalkCallBack = new DingtalkHookCallback(alarmRulesWatcher);
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
        dingtalkCallBack.doAlarm(alarmMessages);
        Assert.assertTrue(isSuccess);
    }

    @After
    public void stop() throws Exception {
        server.stop();
    }

    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
    }

    @Override
    public ServletConfig getServletConfig() {
        return null;
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
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
            String type = jsonObject.get("msgtype").getAsString();
            if (checkSign) {
                String timestamp = request.getParameter("timestamp");
                String sign = request.getParameter("sign");
                if (StringUtil.isEmpty(timestamp) || StringUtil.isEmpty(sign)) {
                    ((HttpServletResponse) response).setStatus(500);
                    return;
                }
            }
            if (type.equalsIgnoreCase("text")) {
                ((HttpServletResponse) response).setStatus(200);
                count = count + 1;
                if (count == 2) {
                    isSuccess = true;
                }
                return;
            }

            ((HttpServletResponse) response).setStatus(500);
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
