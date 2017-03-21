package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.worker.tools.HttpClientTools;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.TraceSegmentRef;
import com.a.eye.skywalking.trace.tag.Tags;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;

/**
 * @author pengys5
 */
public class SegmentPostTestCase {

    @Test
    public void testPostSegment() throws Exception {
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

        TraceSegment webSegment = new TraceSegment("WebApplication");

        Span span = new Span(0, "/Web/GetUser", new Date().getTime());
        Tags.SPAN_LAYER.asHttp(span);
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);
        Tags.URL.set(span, "http://10.218.9.86:8080/Web/GetUser");
        Tags.STATUS_CODE.set(span, 200);
        Tags.COMPONENT.set(span, "Tomcat");
        Tags.PEERS.set(span, "202.135.4.12");
        Thread.sleep(300);
        webSegment.archive(span);
        Thread.sleep(300);

        Span span1 = new Span(1, span, "com.a.eye.skywalking.demo.services.GetUserService.findUser(String, String)", new Date().getTime());
        Tags.SPAN_LAYER.asRPCFramework(span1);
        Tags.URL.set(span1, "motan://10.20.3.15:3000/com.a.eye.skywalking.demo.services.GetUserService.findUser(String, String)");
        Tags.SPAN_KIND.set(span1, Tags.SPAN_KIND_CLIENT);
        Tags.COMPONENT.set(span1, "Motan");
        Tags.PEERS.set(span1, "10.20.3.15:3000");
        Thread.sleep(300);
        webSegment.archive(span1);
        Thread.sleep(300);

        Thread.sleep(300);
        webSegment.finish();
        Thread.sleep(300);

        String webJsonStr = gson.toJson(webSegment);
        HttpClientTools.INSTANCE.post("http://localhost:7001/segment", webJsonStr);

        TraceSegment motanSegment = new TraceSegment("MotanServiceApplication");

        TraceSegmentRef segmentRef = new TraceSegmentRef();
        segmentRef.setApplicationCode("WebApplication");
        segmentRef.setPeerHost("10.20.3.15:3000");
        segmentRef.setTraceSegmentId(webSegment.getTraceSegmentId());
        motanSegment.ref(segmentRef);

        Span span2 = new Span(0, "com.a.eye.skywalking.demo.services.GetUserService.findUser(String, String)", new Date().getTime());
        Tags.SPAN_LAYER.asRPCFramework(span2);
        Tags.SPAN_KIND.set(span2, Tags.SPAN_KIND_SERVER);
        Tags.URL.set(span2, "motan://10.20.3.15:3000/com.a.eye.skywalking.demo.services.GetUserService.findUser(String, String)");
        Tags.COMPONENT.set(span2, "Motan");
        Tags.PEERS.set(span2, "10.218.9.86");
        Thread.sleep(300);
        motanSegment.archive(span2);
        Thread.sleep(300);

        Span span3 = new Span(1, span2, "com.a.eye.skywalking.demo.services.GetUserService.findUser(String, String)", new Date().getTime());
        Tags.SPAN_LAYER.asDB(span3);
        Tags.SPAN_KIND.set(span3, Tags.SPAN_KIND_CLIENT);
        Tags.COMPONENT.set(span3, "Mysql");
        Tags.PEERS.set(span3, "10.5.34.18");
        Thread.sleep(300);
        motanSegment.archive(span3);
        Thread.sleep(300);

        Thread.sleep(300);
        motanSegment.finish();
        Thread.sleep(300);

        String motanJsonStr = gson.toJson(motanSegment);
        HttpClientTools.INSTANCE.post("http://localhost:7001/segment", motanJsonStr);
    }

    @Test
    public void testPostSample1Segment() throws Exception {
        HttpClientTools.INSTANCE.post("http://localhost:7001/segment", sample1);
//        HttpClientTools.INSTANCE.post("http://localhost:7001/segment", sample2);
//        HttpClientTools.INSTANCE.post("http://localhost:7001/segment", sample3);
    }

    private String sample1 = "[{\"ts\":\"Segment.1490064072962.-2099929254.16777.68.1\",\"st\":1490064072962,\"et\":1490064073000,\"ss\":[{\"si\":2,\"ps\":1,\"st\":1490064072965,\"et\":1490064072970,\"on\":\"com.a.eye.skywalking.test.cache.CacheService.findCache(java.lang.String)\",\"ta\":{\"span.layer\":\"rpc\",\"component\":\"Motan\",\"span.kind\":\"client\",\"peer.host\":\"127.0.0.1\",\"peer.port\":8002,\"url\":\"motan://127.0.0.1:8002/default_rpc/com.a.eye.skywalking.test.cache.CacheService/1.0/referer\"},\"lo\":[]},{\"si\":1,\"ps\":0,\"st\":0,\"et\":1490064072970,\"on\":\"Motan_default_rpc_com.a.eye.skywalking.test.cache.CacheService.findCache(java.lang.String)\",\"ta\":{\"requestId\":1562445425373347845},\"lo\":[]},{\"si\":4,\"ps\":3,\"st\":1490064072970,\"et\":1490064072991,\"on\":\"/persistence/query\",\"ta\":{\"span.layer\":\"http\",\"component\":\"HttpClient\",\"status_code\":200,\"span.kind\":\"client\",\"peer.host\":\"10.128.35.80\",\"peer.port\":20880,\"url\":\"http://10.128.35.80:20880/persistence/query\"},\"lo\":[]},{\"si\":3,\"ps\":0,\"st\":1490064072970,\"et\":1490064072993,\"on\":\"com.a.eye.skywalking.test.persistence.PersistenceService.query(String)\",\"ta\":{\"span.layer\":\"rpc\",\"component\":\"Dubbo\",\"span.kind\":\"client\",\"peer.host\":\"10.128.35.80\",\"peer.port\":20880,\"url\":\"rest://10.128.35.80:20880/com.a.eye.skywalking.test.persistence.PersistenceService.query(String)\"},\"lo\":[]},{\"si\":6,\"ps\":5,\"st\":1490064072994,\"et\":1490064072997,\"on\":\"com.a.eye.skywalking.test.cache.CacheService.updateCache(java.lang.String,java.lang.String)\",\"ta\":{\"span.layer\":\"rpc\",\"component\":\"Motan\",\"span.kind\":\"client\",\"peer.host\":\"127.0.0.1\",\"peer.port\":8002,\"url\":\"motan://127.0.0.1:8002/default_rpc/com.a.eye.skywalking.test.cache.CacheService/1.0/referer\"},\"lo\":[]},{\"si\":5,\"ps\":0,\"st\":0,\"et\":1490064072997,\"on\":\"Motan_default_rpc_com.a.eye.skywalking.test.cache.CacheService.updateCache(java.lang.String,java.lang.String)\",\"ta\":{\"requestId\":1562445425402707974},\"lo\":[]},{\"si\":0,\"ps\":-1,\"st\":1490064072963,\"et\":1490064073000,\"on\":\"/portal/\",\"ta\":{\"span.layer\":\"http\",\"component\":\"Tomcat\",\"status_code\":200,\"span.kind\":\"server\",\"peer.host\":\"0:0:0:0:0:0:0:1\",\"peer.port\":51735,\"url\":\"http://localhost:8080/portal/\"},\"lo\":[]}],\"ac\":\"portal-service\",\"gt\":[\"Trace.1490064072962.-2099929254.16777.68.2\"]}]";
    private String sample2 = "[{\"ts\":\"Segment.1490064072966.-306444718.16761.28.1\",\"st\":1490064072966,\"et\":1490064072969,\"rs\":[{\"ts\":\"Segment.1490064072962.-2099929254.16777.68.1\",\"si\":2,\"ac\":\"portal-service\",\"ph\":\"127.0.0.1\"}],\"ss\":[{\"si\":2,\"ps\":1,\"st\":1490064072966,\"et\":1490064072966,\"on\":\"Jedis/getClient\",\"ta\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"peer.port\":6379},\"lo\":[]},{\"si\":3,\"ps\":1,\"st\":1490064072966,\"et\":1490064072966,\"on\":\"Jedis/getClient\",\"ta\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"peer.port\":6379},\"lo\":[]},{\"si\":4,\"ps\":1,\"st\":1490064072966,\"et\":1490064072966,\"on\":\"Jedis/isConnected\",\"ta\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"peer.port\":6379},\"lo\":[]},{\"si\":5,\"ps\":1,\"st\":1490064072966,\"et\":1490064072967,\"on\":\"Jedis/ping\",\"ta\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"peer.port\":6379},\"lo\":[]},{\"si\":6,\"ps\":1,\"st\":1490064072967,\"et\":1490064072968,\"on\":\"Jedis/get\",\"ta\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"db.statement\":\"get test\",\"peer.host\":\"127.0.0.1\",\"peer.port\":6379},\"lo\":[]},{\"si\":7,\"ps\":1,\"st\":1490064072969,\"et\":1490064072969,\"on\":\"H2/JDBI/PreparedStatement/executeQuery\",\"ta\":{\"db.instance\":\"dataSource\",\"span.layer\":\"db\",\"db.type\":\"sql\",\"component\":\"H2\",\"db.statement\":\"SELECT id,CACHE_VALUE, CACHE_KEY FROM CACHE_TABLE WHERE CACHE_KEY \\u003d ?\",\"peer.host\":\"localhost\",\"peer.port\":-1},\"lo\":[]},{\"si\":8,\"ps\":1,\"st\":1490064072969,\"et\":1490064072969,\"on\":\"H2/JDBI/Connection/close\",\"ta\":{\"db.instance\":\"dataSource\",\"span.layer\":\"db\",\"db.type\":\"sql\",\"component\":\"H2\",\"db.statement\":\"\",\"peer.host\":\"localhost\",\"peer.port\":-1},\"lo\":[]},{\"si\":1,\"ps\":0,\"st\":0,\"et\":1490064072969,\"on\":\"Motan_default_rpc_com.a.eye.skywalking.test.cache.CacheService.findCache(java.lang.String)\",\"ta\":{\"requestId\":1562445425373347845},\"lo\":[]},{\"si\":0,\"ps\":-1,\"st\":1490064072966,\"et\":1490064072969,\"on\":\"com.a.eye.skywalking.test.cache.CacheService.findCache(java.lang.String)\",\"ta\":{\"span.layer\":\"rpc\",\"component\":\"Motan\",\"span.kind\":\"server\"},\"lo\":[]}],\"ac\":\"cache-service\",\"gt\":[\"Trace.1490064072962.-2099929254.16777.68.2\"]},{\"ts\":\"Segment.1490064072994.-306444718.16761.29.1\",\"st\":1490064072994,\"et\":1490064072997,\"rs\":[{\"ts\":\"Segment.1490064072962.-2099929254.16777.68.1\",\"si\":6,\"ac\":\"portal-service\",\"ph\":\"127.0.0.1\"}],\"ss\":[{\"si\":2,\"ps\":1,\"st\":1490064072994,\"et\":1490064072994,\"on\":\"Jedis/getClient\",\"ta\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"peer.port\":6379},\"lo\":[]},{\"si\":3,\"ps\":1,\"st\":1490064072994,\"et\":1490064072994,\"on\":\"Jedis/getClient\",\"ta\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"peer.port\":6379},\"lo\":[]},{\"si\":4,\"ps\":1,\"st\":1490064072994,\"et\":1490064072994,\"on\":\"Jedis/isConnected\",\"ta\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"peer.port\":6379},\"lo\":[]},{\"si\":5,\"ps\":1,\"st\":1490064072994,\"et\":1490064072995,\"on\":\"Jedis/ping\",\"ta\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"peer.port\":6379},\"lo\":[]},{\"si\":6,\"ps\":1,\"st\":1490064072995,\"et\":1490064072995,\"on\":\"Jedis/set\",\"ta\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"db.statement\":\"set test_NEW\",\"peer.host\":\"127.0.0.1\",\"peer.port\":6379},\"lo\":[]},{\"si\":7,\"ps\":1,\"st\":1490064072996,\"et\":1490064072997,\"on\":\"H2/JDBI/PreparedStatement/executeUpdate\",\"ta\":{\"db.instance\":\"dataSource\",\"span.layer\":\"db\",\"db.type\":\"sql\",\"component\":\"H2\",\"db.statement\":\"INSERT INTO CACHE_TABLE(CACHE_VALUE, CACHE_KEY) VALUES(?, ?)\",\"peer.host\":\"localhost\",\"peer.port\":-1},\"lo\":[]},{\"si\":8,\"ps\":1,\"st\":1490064072997,\"et\":1490064072997,\"on\":\"H2/JDBI/Connection/close\",\"ta\":{\"db.instance\":\"dataSource\",\"span.layer\":\"db\",\"db.type\":\"sql\",\"component\":\"H2\",\"db.statement\":\"\",\"peer.host\":\"localhost\",\"peer.port\":-1},\"lo\":[]},{\"si\":1,\"ps\":0,\"st\":0,\"et\":1490064072997,\"on\":\"Motan_default_rpc_com.a.eye.skywalking.test.cache.CacheService.updateCache(java.lang.String,java.lang.String)\",\"ta\":{\"requestId\":1562445425402707974},\"lo\":[]},{\"si\":0,\"ps\":-1,\"st\":1490064072994,\"et\":1490064072997,\"on\":\"com.a.eye.skywalking.test.cache.CacheService.updateCache(java.lang.String,java.lang.String)\",\"ta\":{\"span.layer\":\"rpc\",\"component\":\"Motan\",\"span.kind\":\"server\"},\"lo\":[]}],\"ac\":\"cache-service\",\"gt\":[\"Trace.1490064072962.-2099929254.16777.68.2\"]}]";
    private String sample3 = "[{\"ts\":\"Segment.1490064072982.-788773094.16770.42.1\",\"st\":1490064072982,\"et\":1490064072988,\"rs\":[{\"ts\":\"Segment.1490064072962.-2099929254.16777.68.1\",\"si\":4,\"ac\":\"portal-service\",\"ph\":\"10.128.35.80\"}],\"ss\":[{\"si\":2,\"ps\":1,\"st\":1490064072984,\"et\":1490064072985,\"on\":\"Mysql/JDBI/PreparedStatement/executeQuery\",\"ta\":{\"db.instance\":\"skywalking-test\",\"span.layer\":\"db\",\"db.type\":\"sql\",\"component\":\"Mysql\",\"db.statement\":\"SELECT * FROM information_schema.tables WHERE table_schema \\u003d ? AND table_name \\u003d ? LIMIT 1;\",\"peer.host\":\"127.0.0.1\",\"peer.port\":3307},\"lo\":[]},{\"si\":3,\"ps\":1,\"st\":1490064072987,\"et\":1490064072987,\"on\":\"Mysql/JDBI/PreparedStatement/executeQuery\",\"ta\":{\"db.instance\":\"skywalking-test\",\"span.layer\":\"db\",\"db.type\":\"sql\",\"component\":\"Mysql\",\"db.statement\":\"SELECT id, CACHE_KEY, CACHE_VALUE FROM CACHE_TABLE WHERE CACHE_KEY\\u003d ?\",\"peer.host\":\"127.0.0.1\",\"peer.port\":3307},\"lo\":[]},{\"si\":1,\"ps\":0,\"st\":1490064072983,\"et\":1490064072988,\"on\":\"com.a.eye.skywalking.test.persistence.PersistenceService.query(String)\",\"ta\":{\"span.layer\":\"rpc\",\"component\":\"Dubbo\",\"span.kind\":\"server\",\"peer.host\":\"10.128.35.80\",\"peer.port\":20880,\"url\":\"rest://10.128.35.80:20880/com.a.eye.skywalking.test.persistence.PersistenceService.query(String)\"},\"lo\":[]},{\"si\":0,\"ps\":-1,\"st\":1490064072982,\"et\":1490064072988,\"on\":\"/persistence/query\",\"ta\":{\"span.layer\":\"http\",\"component\":\"Tomcat\",\"status_code\":200,\"span.kind\":\"server\",\"peer.host\":\"10.128.35.80\",\"peer.port\":51736,\"url\":\"http://10.128.35.80:20880/persistence/query\"},\"lo\":[]}],\"ac\":\"persistence-service\",\"gt\":[\"Trace.1490064072962.-2099929254.16777.68.2\"]}]";
}
