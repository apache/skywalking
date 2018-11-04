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

package org.apache.skywalking.oap.server.core.alarm.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.source.Scope;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class WebhookCallbackTest implements Servlet {
    private Server server;
    private volatile boolean isSuccess = false;

    @Before
    public void init() throws Exception {
        server = new Server(new InetSocketAddress("127.0.0.1", 8778));
        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletContextHandler.setContextPath("/webhook");

        server.setHandler(servletContextHandler);

        ServletHolder servletHolder = new ServletHolder();
        servletHolder.setServlet(this);
        servletContextHandler.addServlet(servletHolder, "/receiveAlarm");

        server.start();
    }

    @After
    public void stop() throws Exception {
        server.stop();
    }

    @Test
    public void testWebhook() {
        List<String> remoteEndpoints = new ArrayList<>();
        remoteEndpoints.add("http://127.0.0.1:8778/webhook/receiveAlarm");
        WebhookCallback webhookCallback = new WebhookCallback(remoteEndpoints);
        List<AlarmMessage> alarmMessages = new ArrayList<>(2);
        AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessage.setScope(Scope.All);
        alarmMessage.setAlarmMessage("alarmMessage with [Scope.All]");
        alarmMessages.add(alarmMessage);
        AlarmMessage anotherAlarmMessage = new AlarmMessage();
        anotherAlarmMessage.setScope(Scope.Endpoint);
        anotherAlarmMessage.setAlarmMessage("anotherAlarmMessage with [Scope.Endpoint]");
        alarmMessages.add(anotherAlarmMessage);
        webhookCallback.doAlarm(alarmMessages);

        Assert.assertTrue(isSuccess);
    }

    @Override public void init(ServletConfig config) throws ServletException {

    }

    @Override public ServletConfig getServletConfig() {
        return null;
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        HttpServletRequest httpServletRequest = (HttpServletRequest)request;
        if (httpServletRequest.getContentType().equals("application/json")) {
            InputStream inputStream = request.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int readCntOnce;

            while ((readCntOnce = inputStream.read(buffer)) >= 0) {
                out.write(buffer, 0, readCntOnce);
            }

            JsonArray elements = new Gson().fromJson(new String(out.toByteArray()), JsonArray.class);
            if (elements.size() == 2) {
                ((HttpServletResponse)response).setStatus(200);
                isSuccess = true;
                return;
            }

            ((HttpServletResponse)response).setStatus(500);
        }
    }

    @Override public String getServletInfo() {
        return null;
    }

    @Override public void destroy() {

    }

}
