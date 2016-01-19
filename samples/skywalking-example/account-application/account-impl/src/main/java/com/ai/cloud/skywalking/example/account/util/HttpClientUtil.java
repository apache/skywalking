package com.ai.cloud.skywalking.example.account.util;

import com.ai.cloud.skywalking.plugin.httpclient.v42x.SWTracingHttpClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HttpClientUtil {
    private static final Log logger = LogFactory.getLog(HttpClientUtil.class);

    public static String sendPostRequest(String url, Map<String, String> parametersMap) throws IOException, URISyntaxException {
        HttpClient httpclient = new SWTracingHttpClient(new DefaultHttpClient());
        HttpPost httpPost = new HttpPost(new URL(url).toURI());
        List formparams = new ArrayList();
        for (Map.Entry<String, String> entry : parametersMap.entrySet()) {
            formparams.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }

        httpPost.setEntity(new UrlEncodedFormEntity(formparams, "UTF-8"));
        HttpResponse response = httpclient.execute(httpPost);
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
    }
}
