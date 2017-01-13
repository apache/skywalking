package com.a.eye.skywalking.routing.http;


import com.a.eye.skywalking.routing.http.module.ResponseMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;


public class RestfulAPIServiceTest {

    private final static int REST_SERVER_PORT = 54333;
    private final static String REST_URL_PREFIX = "http://localhost:" + REST_SERVER_PORT;
    private RestfulAPIService restfulAPIService;

    @Before
    public void setUp() throws Exception {
        restfulAPIService = new RestfulAPIService("localhost", REST_SERVER_PORT);
        restfulAPIService.doStart();
    }


    @Test
    public void testRequestMethodWithGet() throws IOException {
        ResponseResult responseResult = HttpClientUtil.doGet(REST_URL_PREFIX);
        assertEquals(200, responseResult.getStatusCode());
        ResponseMessage responseMessage = responseResult.getResponseMessage();
        assertEquals(403, responseMessage.getCode());
    }

    @Test
    public void testRequestMethodWithWrongURL() throws IOException {
        ResponseResult responseResult = HttpClientUtil.doPost(REST_URL_PREFIX, "{}");
        assertEquals(200, responseResult.getStatusCode());
        ResponseMessage responseMessage = responseResult.getResponseMessage();
        assertEquals(404, responseMessage.getCode());
    }

    @Test
    public void testAddAckSpans() throws IOException {
        ResponseResult responseResult = HttpClientUtil.doPost
                (REST_URL_PREFIX + "/spans/ack", "[{\n" +
                        "\"traceId\":\"212017.1484100963000.-215172798.6571.52.2\",\n" +
                        "\"parentLevelId\":\"\",\n" +
                        "\"levelId\":0,\n" +
                        "\"cost\":14,\n" +
                        "\"routeKey\":123456,\n" +
                        "\"tags\": {\n" +
                        "    \"viewpoint\":\"http://localhost:8080/skywalking/test\",\n" +
                        "    \"error.status\":\"0\",\n" +
                        "    \"applicationCode\":\"test\",\n" +
                        "    \"username\":\"test\"\n" +
                        "    }\n" +
                        "}]");
        assertEquals(200, responseResult.getStatusCode());
        ResponseMessage responseMessage = responseResult.getResponseMessage();
        assertEquals(200, responseMessage.getCode());
    }

    @Test
    public void testAddRequestSpans() throws IOException {
        ResponseResult responseResult = HttpClientUtil.doPost
                (REST_URL_PREFIX + "/spans/request", "[{\n" +
                        "\"traceId\":\"212017.1484100963000.-215172798.6571.52.2\",\n" +
                        "\"parentLevelId\":\"\",\n" +
                        "\"levelId\":0,\n" +
                        "\"startTime\":0,\n" +
                        "\"routeKey\":123456,\n" +
                        "\"tags\":{\n" +
                        "    \"viewpoint\":\"http://localhost:8080/skywalking/test\",\n" +
                        "    \"hostname\":\"192.168.1.1\",\n" +
                        "    \"error.status\":\"0\",\n" +
                        "    \"process_no\":\"123456\",\n" +
                        "    \"applicationCode\":\"test\",\n" +
                        "    \"call.desc\":\"W\",\n" +
                        "    \"call.type\":\"S\",\n" +
                        "    \"username\":\"test\"\n" +
                        "    }\n" +
                        "}]");

        assertEquals(200, responseResult.getStatusCode());
        ResponseMessage responseMessage = responseResult.getResponseMessage();
        assertEquals(200, responseMessage.getCode());
    }


    @Test
    public void testAddWithErrorRequestSpanJson() throws IOException {
        ResponseResult responseResult = HttpClientUtil.doPost
                (REST_URL_PREFIX + "/spans/request", "{\n" +
                        "\"traceId\":\"212017.1484100963000.-215172798.6571.52.2\",\n" +
                        "\"parentLevelId\":\"\",\n" +
                        "\"levelId\":0,\n" +
                        "\"startTime\":0,\n" +
                        "\"routeKey\":123456,\n" +
                        "\"tags\":{\n" +
                        "    \"viewpoint\":\"http://localhost:8080/skywalking/test\",\n" +
                        "    \"hostname\":\"192.168.1.1\",\n" +
                        "    \"error.status\":\"0\",\n" +
                        "    \"process_no\":\"123456\",\n" +
                        "    \"applicationCode\":\"test\",\n" +
                        "    \"call.desc\":\"W\",\n" +
                        "    \"call.type\":\"S\",\n" +
                        "    \"username\":\"test\"\n" +
                        "    }\n" +
                        "}");

        assertEquals(200, responseResult.getStatusCode());
        ResponseMessage responseMessage = responseResult.getResponseMessage();
        assertEquals(500, responseMessage.getCode());
    }

    @After
    public void tearDown() throws Exception {
        restfulAPIService.doStop();
    }

}