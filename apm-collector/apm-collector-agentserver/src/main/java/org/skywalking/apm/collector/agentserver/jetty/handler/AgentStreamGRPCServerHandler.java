package org.skywalking.apm.collector.agentserver.jetty.handler;

import com.google.gson.JsonArray;
import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.skywalking.apm.collector.agentstream.grpc.AgentStreamGRPCDataListener;
import org.skywalking.apm.collector.cluster.ClusterModuleGroupDefine;
import org.skywalking.apm.collector.core.cluster.ClusterModuleContext;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationReader;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.server.jetty.JettyHandler;

/**
 * @author pengys5
 */
public class AgentStreamGRPCServerHandler extends JettyHandler {

    @Override public String pathSpec() {
        return "/agentstream/grpc";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        ClusterModuleRegistrationReader reader = ((ClusterModuleContext)CollectorContextHelper.INSTANCE.getContext(ClusterModuleGroupDefine.GROUP_NAME)).getReader();
        List<String> servers = reader.read(AgentStreamGRPCDataListener.PATH);
        JsonArray serverArray = new JsonArray();
        servers.forEach(server -> {
            serverArray.add(server);
        });

        reply(resp, serverArray, HttpServletResponse.SC_OK);
    }
}
