package test.apache.skywalking.testcase.jettyserver;

import java.net.InetSocketAddress;
import test.apache.skywalking.testcase.jettyserver.servlet.CaseServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

public class Application {

    public static void main(String[] args) throws Exception {
        Server jettyServer = new Server(new InetSocketAddress("0.0.0.0",
            Integer.valueOf(18080)));
        String contextPath = "/jettyserver-case";
        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletContextHandler.setContextPath(contextPath);
        servletContextHandler.addServlet(CaseServlet.class, CaseServlet.SERVLET_PATH);
        jettyServer.setHandler(servletContextHandler);
        jettyServer.start();
    }
}
