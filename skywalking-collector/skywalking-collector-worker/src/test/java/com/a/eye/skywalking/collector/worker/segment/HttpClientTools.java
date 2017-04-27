package com.a.eye.skywalking.collector.worker.segment;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * @author pengys5
 */
public enum HttpClientTools {
    INSTANCE;

    private Logger logger = LogManager.getFormatterLogger(HttpClientTools.class);

    public String get(String url, List<NameValuePair> params) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            HttpGet httpget = new HttpGet(url);
            String paramStr = EntityUtils.toString(new UrlEncodedFormEntity(params));
            httpget.setURI(new URI(httpget.getURI().toString() + "?" + paramStr));
            logger.debug("executing get request %s", httpget.getURI());

            try (CloseableHttpResponse response = httpClient.execute(httpget)) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    return EntityUtils.toString(entity);
                }
            }
        } catch (Exception e) {
            logger.error(e);
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                logger.error(e);
            }
        }
        return null;
    }

    public String post(String url, String data) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            HttpPost httppost = new HttpPost(url);
            httppost.setEntity(new StringEntity(data, Consts.UTF_8));
            logger.debug("executing post request %s", httppost.getURI());
            try (CloseableHttpResponse response = httpClient.execute(httppost)) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    return EntityUtils.toString(entity);
                }
            }
        } catch (Exception e) {
            logger.error(e);
        } finally {
            try {
                httpClient.close();
            } catch (Exception e) {
                logger.error(e);
            }
        }
        return null;
    }
}
