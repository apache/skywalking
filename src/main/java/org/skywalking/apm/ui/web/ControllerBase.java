package org.skywalking.apm.ui.web;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;

public abstract class ControllerBase {
    private static final String CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final String UTF_8 = "UTF-8";

    public void reply(String result, HttpServletResponse response) throws IOException {
        response.setContentType(CONTENT_TYPE);
        response.setCharacterEncoding(UTF_8);
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter writer = response.getWriter();
        writer.write(result);
    }
}
