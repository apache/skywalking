package com.a.eye.skywalking.collector.worker.httpserver;

import com.google.gson.JsonObject;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author pengys5
 */
public abstract class AbstractHttpServlet extends HttpServlet {

    final public void reply(HttpServletResponse response, JsonObject resJson, int status) throws IOException {
        response.setContentType("text/json");
        response.setCharacterEncoding("utf-8");
        response.setStatus(status);

        PrintWriter out = response.getWriter();
        out.print(resJson);
        out.flush();
        out.close();
    }
}
