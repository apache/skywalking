package org.skywalking.apm.collector.ui.jetty.handler;

import com.google.gson.JsonArray;
import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.skywalking.apm.collector.cluster.ClusterModuleGroupDefine;
import org.skywalking.apm.collector.core.cluster.ClusterModuleContext;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationReader;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.skywalking.apm.collector.ui.jetty.UIJettyDataListener;

/**
 * @author pengys5
 */
public class UIJettyServerHandler extends JettyHandler {

    @Override public String pathSpec() {
        return "/ui/jetty";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        ClusterModuleRegistrationReader reader = ((ClusterModuleContext)CollectorContextHelper.INSTANCE.getContext(ClusterModuleGroupDefine.GROUP_NAME)).getReader();
        List<String> servers = reader.read(UIJettyDataListener.PATH);
        JsonArray serverArray = new JsonArray();
        servers.forEach(server -> {
            serverArray.add(server);
        });

        reply(resp, serverArray, HttpServletResponse.SC_OK);
    }
}
