package com.ai.cloud.skywalking.plugin.httpclient.v43x;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

public class SWTracingCloseableHttpClient extends CloseableHttpClient {
    private static final String DEFAULT_TRACE_NAME = "SkyWalking-TRACING-NAME";

    private CloseableHttpClient client;
    private String traceHeaderName;

    public SWTracingCloseableHttpClient(CloseableHttpClient client, String traceHeaderName) {
        this.client = client;
        if (traceHeaderName == null || traceHeaderName.length() <= 0) {
            throw new IllegalArgumentException("Trace header name can not be null");
        }
        this.traceHeaderName = traceHeaderName;
    }

    public SWTracingCloseableHttpClient(CloseableHttpClient client) {
        this.client = client;
        this.traceHeaderName = DEFAULT_TRACE_NAME;
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    @Override
    public HttpParams getParams() {
        return client.getParams();
    }

    @Override
    public ClientConnectionManager getConnectionManager() {
        return client.getConnectionManager();
    }

    @Override
    public CloseableHttpResponse execute(final HttpUriRequest request) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(request.getURI().toString(), traceHeaderName, request, new HttpClientTracing.Executor<CloseableHttpResponse>() {
            @Override
            public CloseableHttpResponse execute() throws IOException {
                return client.execute(request);
            }
        });
    }

    @Override
    public CloseableHttpResponse execute(final HttpUriRequest request, final HttpContext context) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(request.getURI().toString(), traceHeaderName, request, new HttpClientTracing.Executor<CloseableHttpResponse>() {
            @Override
            public CloseableHttpResponse execute() throws IOException {
                return client.execute(request, context);
            }
        });
    }

    @Override
    public CloseableHttpResponse execute(final HttpHost target, final HttpRequest request) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(target.toURI().toString(), traceHeaderName, request, new HttpClientTracing.Executor<CloseableHttpResponse>() {
            @Override
            public CloseableHttpResponse execute() throws IOException {
                return client.execute(target, request);
            }
        });
    }

    @Override
    protected CloseableHttpResponse doExecute(final HttpHost target, final HttpRequest request, final HttpContext context) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(target.toURI().toString(), traceHeaderName, request, new HttpClientTracing.Executor<CloseableHttpResponse>() {
            @Override
            public CloseableHttpResponse execute() throws IOException {
                return client.execute(target, request, context);
            }
        });
    }

    @Override
    public CloseableHttpResponse execute(final HttpHost target, final HttpRequest request, final HttpContext context) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(target.toURI().toString(), traceHeaderName, request, new HttpClientTracing.Executor<CloseableHttpResponse>() {
            @Override
            public CloseableHttpResponse execute() throws IOException {
                return client.execute(target, request, context);
            }
        });
    }

    @Override
    public <T> T execute(final HttpUriRequest request, final ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(request.getURI().toString(), traceHeaderName, request, new HttpClientTracing.Executor<T>() {
            @Override
            public T execute() throws IOException {
                return client.execute(request, responseHandler);
            }
        });
    }

    @Override
    public <T> T execute(final HttpUriRequest request, final ResponseHandler<? extends T> responseHandler, final HttpContext context) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(request.getURI().toString(), traceHeaderName, request, new HttpClientTracing.Executor<T>() {
            @Override
            public T execute() throws IOException {
                return client.execute(request, responseHandler, context);
            }
        });
    }

    @Override
    public <T> T execute(final HttpHost target, final HttpRequest request, final ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(target.toURI().toString(), traceHeaderName, request, new HttpClientTracing.Executor<T>() {
            @Override
            public T execute() throws IOException {
                return client.execute(target, request, responseHandler);
            }
        });
    }

    @Override
    public <T> T execute(final HttpHost target, final HttpRequest request, final ResponseHandler<? extends T> responseHandler, final HttpContext context) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(target.toURI().toString(), traceHeaderName, request, new HttpClientTracing.Executor<T>() {
            @Override
            public T execute() throws IOException {
                return client.execute(target, request, responseHandler, context);
            }
        });
    }
}
