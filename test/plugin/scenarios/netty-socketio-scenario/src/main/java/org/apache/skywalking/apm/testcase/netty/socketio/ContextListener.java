package org.apache.skywalking.apm.testcase.netty.socketio;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * @author MrPro
 */
public class ContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        // start socket io server on tomcat start
        SocketIOStarter.startServer();

        // start client
        try {
            SocketIOStarter.startClientAndWaitConnect();
        } catch (Exception e) {
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        SocketIOStarter.server.stop();
    }
}
