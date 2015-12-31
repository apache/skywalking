package com.ai.cloud.skywalking.plugin.httpclient.v42x;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

public class SWTracingHttpClient implements HttpClient {

    private static final String DEFAULT_TRACE_NAME = "SkyWalking-TRACING-NAME";

    private HttpClient client;
    private String traceHeaderName;

    public SWTracingHttpClient(HttpClient client, String traceHeaderName) {
        this.client = client;
        if (traceHeaderName == null || traceHeaderName.length() <= 0) {
            throw new IllegalArgumentException("Trace header name can not be null");
        }
        this.traceHeaderName = traceHeaderName;
    }

    public SWTracingHttpClient(HttpClient client) {
        this.client = client;
        this.traceHeaderName = DEFAULT_TRACE_NAME;
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
    public HttpResponse execute(final HttpUriRequest httpUriRequest) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(httpUriRequest.getURI().toString(), traceHeaderName, httpUriRequest, new HttpClientTracing.Executor<HttpResponse>() {
            @Override
            public HttpResponse execute() throws IOException {
                return client.execute(httpUriRequest);
            }
        });
    }

    @Override
    public HttpResponse execute(final HttpUriRequest httpUriRequest, final HttpContext httpContext) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(httpUriRequest.getURI().toString(), traceHeaderName, httpUriRequest, new HttpClientTracing.Executor<HttpResponse>() {
            @Override
            public HttpResponse execute() throws IOException {
                return client.execute(httpUriRequest, httpContext);
            }
        });
    }

    @Override
    public HttpResponse execute(final HttpHost httpHost, final HttpRequest httpRequest) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(httpHost.toURI(), traceHeaderName, httpRequest, new HttpClientTracing.Executor<HttpResponse>() {
            @Override
            public HttpResponse execute() throws IOException {
                return client.execute(httpHost, httpRequest);
            }
        });
    }

    @Override
    public HttpResponse execute(final HttpHost httpHost, final HttpRequest httpRequest, final HttpContext httpContext) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(httpHost.toURI(), traceHeaderName, httpRequest, new HttpClientTracing.Executor<HttpResponse>() {
            @Override
            public HttpResponse execute() throws IOException {
                return client.execute(httpHost, httpRequest, httpContext);
            }
        });
    }

    @Override
    public <T> T execute(final HttpUriRequest httpUriRequest, final ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(httpUriRequest.getURI().toString(), traceHeaderName, httpUriRequest, new HttpClientTracing.Executor<T>() {
            @Override
            public T execute() throws IOException {
                return client.execute(httpUriRequest, responseHandler);
            }
        });

    }

    @Override
    public <T> T execute(final HttpUriRequest httpUriRequest, final ResponseHandler<? extends T> responseHandler, final HttpContext httpContext) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(httpUriRequest.getURI().toString(), traceHeaderName, httpUriRequest, new HttpClientTracing.Executor<T>() {
            @Override
            public T execute() throws IOException {
                return client.execute(httpUriRequest, responseHandler, httpContext);
            }
        });

    }

    @Override
    public <T> T execute(final HttpHost httpHost, final HttpRequest httpRequest, final ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(httpHost.toURI(), traceHeaderName, httpRequest, new HttpClientTracing.Executor<T>() {
            @Override
            public T execute() throws IOException {
                return client.execute(httpHost, httpRequest, responseHandler);
            }
        });

    }

    @Override
    public <T> T execute(final HttpHost httpHost, final HttpRequest httpRequest, final ResponseHandler<? extends T> responseHandler, HttpContext httpContext) throws IOException, ClientProtocolException {
        return HttpClientTracing.execute(httpHost.toURI(), traceHeaderName, httpRequest, new HttpClientTracing.Executor<T>() {
            @Override
            public T execute() throws IOException {
                return client.execute(httpHost, httpRequest, responseHandler);
            }
        });
    }
}
