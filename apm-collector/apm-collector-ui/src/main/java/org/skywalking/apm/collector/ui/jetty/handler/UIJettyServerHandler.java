package org.skywalking.apm.collector.ui.jetty.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.skywalking.apm.collector.cluster.ClusterModuleGroupDefine;
import org.skywalking.apm.collector.core.cluster.ClusterModuleContext;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationReader;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.skywalking.apm.collector.server.jetty.JettyHandler;
import org.skywalking.apm.collector.ui.jetty.UIJettyDataListener;

/**
 * @author pengys5
 */
public class UIJettyServerHandler extends JettyHandler {

    @Override public String pathSpec() {
        return "/ui/jetty";
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        ClusterModuleRegistrationReader reader = ((ClusterModuleContext)CollectorContextHelper.INSTANCE.getContext(ClusterModuleGroupDefine.GROUP_NAME)).getReader();
        List<String> servers = reader.read(UIJettyDataListener.PATH);
        JsonArray serverArray = new JsonArray();
        servers.forEach(server -> {
            serverArray.add(server);
        });
        return serverArray;
    }

    @Override protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }
}
