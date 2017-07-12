package org.skywalking.apm.collector.core.cluster;

import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.framework.DataInitializer;

/**
 * @author pengys5
 */
public abstract class ClusterDataInitializer implements DataInitializer {

    public static final String BASE_CATALOG = "skywalking";
    public static final String FOR_UI_CATALOG = BASE_CATALOG + ".ui";
    public static final String FOR_AGENT_CATALOG = BASE_CATALOG + ".agent";

    @Override public final void initialize(Client client) throws ClientException {
        if (!existItem(client, FOR_UI_CATALOG)) {
            addItem(client, FOR_UI_CATALOG);
        }
        if (!existItem(client, FOR_AGENT_CATALOG)) {
            addItem(client, FOR_AGENT_CATALOG);
        }
    }
}
