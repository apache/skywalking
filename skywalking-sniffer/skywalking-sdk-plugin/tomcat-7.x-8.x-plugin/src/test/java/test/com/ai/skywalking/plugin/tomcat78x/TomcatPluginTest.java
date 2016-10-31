package test.com.ai.skywalking.plugin.tomcat78x;

import com.a.eye.skywalking.plugin.PluginException;
import com.a.eye.skywalking.plugin.TracingBootstrap;
import com.a.eye.skywalking.testframework.api.RequestSpanAssert;
import org.apache.catalina.LifecycleException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class TomcatPluginTest {

    @Test
    public void test() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, PluginException {
        TracingBootstrap.main(new String[] {TomcatPluginTest.class.getName()});
    }

    public static void main(String[] args) throws ServletException, LifecycleException, IOException, InterruptedException {
        // start tomcat
        EmbedTomcat.start();
        // curl httpclient
        visitURL("http://127.0.0.1:8080/hello");
        // assert client
        RequestSpanAssert.assertEquals(new String[][] {{"0", "http://127.0.0.1:8080/hello", ""}});
    }

    public static void visitURL(String url) throws IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            HttpGet httpget = new HttpGet(url);
            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
                @Override
                public String handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? EntityUtils.toString(entity) : null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }

            };
            String responseBody = httpclient.execute(httpget, responseHandler);
        } finally {
            httpclient.close();
        }
    }
}
