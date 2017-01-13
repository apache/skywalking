package com.a.eye.skywalking.routing.http;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;

/**
 * Created by xin on 2017/1/10.
 */
public class HttpClientUtil {

    public static ResponseResult doPost(String url, String bodyJson) throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        EntityBuilder entityBuilder = EntityBuilder.create()
                .setText(bodyJson)
                .setContentType(ContentType.APPLICATION_JSON.withCharset("utf-8"));
        httpPost.setEntity(entityBuilder.build());

        CloseableHttpResponse response = client.execute(httpPost);
        response.getStatusLine().getStatusCode();
        StringWriter writer = new StringWriter();
        IOUtils.copy(response.getEntity().getContent(), writer, Charset.forName("UTF-8"));
        return new ResponseResult(writer.toString(), response.getStatusLine().getStatusCode());
    }

    public static ResponseResult doGet(String url) throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");
        httpGet.setHeader("Content-type", "application/json");

        CloseableHttpResponse response = client.execute(httpGet);
        response.getStatusLine().getStatusCode();
        StringWriter writer = new StringWriter();
        IOUtils.copy(response.getEntity().getContent(), writer, Charset.forName("UTF-8"));
        return new ResponseResult(writer.toString(), response.getStatusLine().getStatusCode());
    }

}
