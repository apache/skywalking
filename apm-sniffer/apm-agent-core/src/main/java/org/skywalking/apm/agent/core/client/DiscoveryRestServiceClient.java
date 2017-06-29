package org.skywalking.apm.agent.core.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Random;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

import static org.skywalking.apm.agent.core.conf.RemoteDownstreamConfig.Collector.GRPC_SERVERS;

/**
 * The <code>DiscoveryRestServiceClient</code> try to get the collector's grpc-server list
 * in every 60 seconds,
 * and override {@link org.skywalking.apm.agent.core.conf.RemoteDownstreamConfig.Collector#GRPC_SERVERS}.
 *
 * @author wusheng
 */
public class DiscoveryRestServiceClient implements Runnable {
    private static final ILog logger = LogManager.getLogger(DiscoveryRestServiceClient.class);
    private String[] serverList;
    private volatile int selectedServer = -1;

    public DiscoveryRestServiceClient() {
        serverList = Config.Collector.SERVERS.split(",");
        Random r = new Random();
        if (serverList.length > 0) {
            selectedServer = r.nextInt(serverList.length);
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                try2Sleep(60 * 1000);
                findServerList();
            } catch (Throwable t) {
                logger.error(t, "Find server list fail.");
            }
        }
    }

    private void findServerList() throws RESTResponseStatusError, IOException {
        CloseableHttpClient httpClient = HttpClients.custom().build();
        try {
            HttpGet httpGet = buildGet();
            if (httpGet != null) {
                CloseableHttpResponse httpResponse = httpClient.execute(httpGet);
                int statusCode = httpResponse.getStatusLine().getStatusCode();
                if (200 != statusCode) {
                    findBackupServer();
                    throw new RESTResponseStatusError(statusCode);
                } else {
                    JsonArray serverList = new Gson().fromJson(EntityUtils.toString(httpResponse.getEntity()), JsonArray.class);
                    if (serverList != null && serverList.size() > 0) {
                        LinkedList<String> newServerList = new LinkedList<String>();
                        for (JsonElement element : serverList) {
                            newServerList.add(element.getAsString());
                        }
                        if (!newServerList.equals(GRPC_SERVERS)) {
                            logger.debug("Refresh GRPC server list: {}", GRPC_SERVERS);
                        } else {
                            logger.debug("GRPC server list remain unchanged: {}", GRPC_SERVERS);
                        }
                    }
                }
            }
        } catch (IOException e) {
            findBackupServer();
            throw e;
        } finally {
            httpClient.close();
        }
    }

    /**
     * Prepare the given message for HTTP Post service.
     *
     * @return {@link HttpGet}, when is ready to send. otherwise, null.
     */
    private HttpGet buildGet() {
        if (selectedServer == -1) {
            //no available server
            return null;
        }
        HttpGet httpGet = new HttpGet("http://" + serverList[selectedServer] + Config.Collector.DISCOVERY_SERVICE_NAME);

        return httpGet;
    }

    /**
     * Choose the next server in {@link #serverList}, by moving {@link #selectedServer}.
     */
    private void findBackupServer() {
        selectedServer++;
        if (selectedServer == serverList.length) {
            selectedServer = 0;
        }
    }

    /**
     * Try to sleep, and ignore the {@link InterruptedException}
     *
     * @param millis the length of time to sleep in milliseconds
     */
    private void try2Sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {

        }
    }
}
