package org.skywalking.apm.collector.ui.jetty.handler;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.skywalking.apm.collector.server.jetty.JettyHandler;

/**
 * @author pengys5
 */
public class TraceDagGetHandler extends JettyHandler {

    @Override public String pathSpec() {
        return "/traceDag";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    }
}
