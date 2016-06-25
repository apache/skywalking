package org.skywalking.httpClient.v4.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Test;

import com.ai.cloud.skywalking.plugin.TracingBootstrap;

public class TestHttpClientV42 {
	@Test
	public void testsql() throws IllegalAccessException,
			IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException, ClassNotFoundException {
		TracingBootstrap
				.main(new String[] { "org.skywalking.httpClient.v4.plugin.TestHttpClientV42" });
	}

	public static void main(String[] args) throws ClassNotFoundException,
			SQLException, InterruptedException, ClientProtocolException,
			IOException {
		// 默认的client类。
		HttpClient client = new DefaultHttpClient();
		// 设置为get取连接的方式.
		HttpGet get = new HttpGet("http://www.baidu.com");
		try {
			// 得到返回的response.
			HttpResponse response = client.execute(get);
			// 得到返回的client里面的实体对象信息.
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				System.out.println("内容编码是：" + entity.getContentEncoding());
				System.out.println("内容类型是：" + entity.getContentType());
				// 得到返回的主体内容.
				InputStream instream = entity.getContent();
				try {
					BufferedReader reader = new BufferedReader(
							new InputStreamReader(instream, "UTF-8"));
					System.out.println(reader.readLine());
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					instream.close();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			client.getConnectionManager().shutdown();
		}

		Thread.sleep(5 * 1000);
	}
}
