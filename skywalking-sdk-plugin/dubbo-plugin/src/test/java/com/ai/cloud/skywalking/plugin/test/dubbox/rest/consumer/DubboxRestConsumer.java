package com.ai.cloud.skywalking.plugin.test.dubbox.rest.consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class DubboxRestConsumer {
    private static final Log logger = LogFactory.getLog(DubboxRestConsumer.class);

    public static String sendPostRequest(String url, String data, Map<String, String> header) throws IOException,
            URISyntaxException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(new URL(url).toURI());
        for (Map.Entry<String, String> entry : header.entrySet()) {
            httpPost.setHeader(entry.getKey(), entry.getValue());
        }
        StringEntity dataEntity = new StringEntity(data, ContentType.APPLICATION_JSON);
        httpPost.setEntity(dataEntity);
        CloseableHttpResponse response = httpclient.execute(httpPost);
        try {
            if (response.getStatusLine().getStatusCode() == 200) {
                HttpEntity entity = response.getEntity();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        entity.getContent()));
                StringBuffer buffer = new StringBuffer();
                String tempStr;
                while ((tempStr = reader.readLine()) != null)
                    buffer.append(tempStr);
                return buffer.toString();
            } else {
                throw new RuntimeException("error code " + response.getStatusLine().getStatusCode()
                        + ":" + response.getStatusLine().getReasonPhrase());
            }
        } finally {
            response.close();
            httpclient.close();
        }
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        String url = "http://192.168.1.102:20880/skywalking/rest-a/doBusiness";

        String data = "{\"paramA\":\"BBBB\"}";
        sendPostRequest(url, data, new HashMap<String, String>());
    }
}
