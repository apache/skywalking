package org.skywalking.apm.collector.server.jetty;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import org.skywalking.apm.collector.core.framework.Handler;

/**
 * @author pengys5
 */
public abstract class JettyHandler extends HttpServlet implements Handler {

    public abstract String pathSpec();

    protected final void reply(HttpServletResponse response, JsonElement resJson, int status) throws IOException {
        response.setContentType("text/json");
        response.setCharacterEncoding("utf-8");
        response.setStatus(status);

        PrintWriter out = response.getWriter();
        out.print(resJson);
        out.flush();
        out.close();
    }
}
