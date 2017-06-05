package org.skywalking.apm.agent.core.collector.sender;

import java.io.IOException;
import java.util.Random;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.skywalking.apm.agent.core.conf.Config;

public abstract class HttpPostSender<T> extends AbstractSender<T> {

    private String[] serverList;
    private volatile int selectedServer = -1;

    public HttpPostSender() {
        serverList = Config.Collector.SERVERS.split(",");
        Random r = new Random();
        if (serverList.length > 0) {
            selectedServer = r.nextInt(serverList.length);
        }
    }

    public abstract String url();

    public abstract String serializeData(T data);

    public void send(T message) throws Exception {
        CloseableHttpClient httpClient = HttpClients.custom().build();
        try {
            if (message != null) {
                HttpPost httpPost = ready2Send(serializeData(message));
                if (httpPost != null) {
                    CloseableHttpResponse httpResponse = httpClient.execute(httpPost);
                    int statusCode = httpResponse.getStatusLine().getStatusCode();
                    if (200 != statusCode) {
                        findBackupServer();
                    }

                    dealWithResponse(statusCode, EntityUtils.toString(httpResponse.getEntity()));
                }
            }
        } catch (IOException e) {
            findBackupServer();
            throw e;
        } finally {
            httpClient.close();
        }
    }

    protected abstract void dealWithResponse(int statusCode, String responseBody);

    private HttpPost ready2Send(String messageJson) {
        if (selectedServer == -1) {
            return null;
        }
        HttpPost post = new HttpPost("http://" + serverList[selectedServer] + url());
        StringEntity entity = new StringEntity(messageJson, ContentType.APPLICATION_JSON);
        post.setEntity(entity);

        return post;
    }

    private void findBackupServer() {
        selectedServer++;
        if (selectedServer == serverList.length) {
            selectedServer = 0;
        }
    }
}
