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

package org.apache.skywalking.oap.server.library.server.jetty;

import com.google.gson.JsonElement;
import java.io.*;
import java.util.Enumeration;
import javax.servlet.*;
import javax.servlet.http.*;
import org.slf4j.*;

import static java.util.Objects.nonNull;

/**
 * @author wusheng
 */
public abstract class JettyJsonHandler extends JettyHandler {
    private static final Logger logger = LoggerFactory.getLogger(JettyHandler.class);

    @Override
    protected final void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            reply(resp, doGet(req));
        } catch (ArgumentsParseException | IOException e) {
            try {
                replyError(resp, e.getMessage(), HttpServletResponse.SC_BAD_REQUEST);
            } catch (IOException replyException) {
                logger.error(replyException.getMessage(), e);
            }
        }
    }

    protected abstract JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException;

    @Override
    protected final void doPost(HttpServletRequest req, HttpServletResponse resp) {
        try {
            reply(resp, doPost(req));
        } catch (ArgumentsParseException | IOException e) {
            try {
                replyError(resp, e.getMessage(), HttpServletResponse.SC_BAD_REQUEST);
            } catch (IOException replyException) {
                logger.error(replyException.getMessage(), e);
            }
        }
    }

    protected abstract JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException, IOException;

    @Override
    protected final void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doHead(req, resp);
    }

    @Override protected final long getLastModified(HttpServletRequest req) {
        return super.getLastModified(req);
    }

    @Override
    protected final void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPut(req, resp);
    }

    @Override
    protected final void doDelete(HttpServletRequest req,
        HttpServletResponse resp) throws ServletException, IOException {
        super.doDelete(req, resp);
    }

    @Override
    protected final void doOptions(HttpServletRequest req,
        HttpServletResponse resp) throws ServletException, IOException {
        super.doOptions(req, resp);
    }

    @Override
    protected final void doTrace(HttpServletRequest req,
        HttpServletResponse resp) throws ServletException, IOException {
        super.doTrace(req, resp);
    }

    @Override
    protected final void service(HttpServletRequest req,
        HttpServletResponse resp) throws ServletException, IOException {
        super.service(req, resp);
    }

    @Override public final void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        super.service(req, res);
    }

    @Override public final void destroy() {
        super.destroy();
    }

    @Override public final String getInitParameter(String name) {
        return super.getInitParameter(name);
    }

    @Override public final Enumeration<String> getInitParameterNames() {
        return super.getInitParameterNames();
    }

    @Override public final ServletConfig getServletConfig() {
        return super.getServletConfig();
    }

    @Override public final ServletContext getServletContext() {
        return super.getServletContext();
    }

    @Override public final String getServletInfo() {
        return super.getServletInfo();
    }

    @Override public final void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override public final void init() throws ServletException {
        super.init();
    }

    @Override public final void log(String msg) {
        super.log(msg);
    }

    @Override public final void log(String message, Throwable t) {
        super.log(message, t);
    }

    @Override public final String getServletName() {
        return super.getServletName();
    }

    private void reply(HttpServletResponse response, JsonElement resJson) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
        response.setStatus(HttpServletResponse.SC_OK);

        PrintWriter out = response.getWriter();
        if (nonNull(resJson)) {
            out.print(resJson);
        }
        out.flush();
        out.close();
    }

    private void replyError(HttpServletResponse response, String errorMessage, int status) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
        response.setStatus(status);
        response.setHeader("error-message", errorMessage);

        PrintWriter out = response.getWriter();
        out.flush();
        out.close();
    }
}
