package org.skywalking.apm.agent.core.client;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * This is a small application, test for http restful service.
 * Use APACHE HttpClient as client, nanohttpd as server.
 *
 * @author wusheng
 */
public class HTTPRestServiceTestApp {
    public static void main(String[] args) throws Exception {
        CloseableHttpClient client = null;
        Server server = null;
        try {
            HTTPRestServiceTestApp test = new HTTPRestServiceTestApp();
            server = test.startServer();
            client = test.send();
        } finally {
            if (client != null) {
                client.close();
            }
            if (server != null) {
                server.stop();
            }
        }

    }

    private CloseableHttpClient send() {
        CloseableHttpClient httpclient = HttpClients.custom()
            .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())
            .build();
        HttpPost post = new HttpPost("http://localhost:7000/segments");
        StringEntity entity = new StringEntity("[{'abc'}]", ContentType.APPLICATION_JSON);
        post.setEntity(entity);
        try {
            CloseableHttpResponse httpResponse = httpclient.execute(post);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return httpclient;
    }

    private Server startServer() throws Exception {
        Server server = new Server(7000);

        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request,
                               HttpServletResponse response) throws IOException, ServletException {
                BufferedReader br = request.getReader();
                String str, wholeStr = "";
                while ((str = br.readLine()) != null) {
                    wholeStr += str;
                }
                response.setContentType("text/html; charset=utf-8");
                response.setStatus(HttpServletResponse.SC_OK);
                baseRequest.setHandled(true);
            }
        });
        server.start();
        return server;
    }

}
