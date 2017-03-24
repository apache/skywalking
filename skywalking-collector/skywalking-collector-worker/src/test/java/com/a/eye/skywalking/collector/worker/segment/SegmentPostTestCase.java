package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.worker.tools.HttpClientTools;
import com.a.eye.skywalking.trace.SegmentsMessage;
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

//    @Test
    public void loopPostSegment() throws Exception {
        for (int i = 0; i < 100; i++) {
            testPostSegment();
        }
    }

//    @Test
    public void testPostSegment() throws Exception {
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

        SegmentsMessage segmentsMessage = new SegmentsMessage();

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

        segmentsMessage.append(webSegment);
//        String webJsonStr = gson.toJson(webSegment);
//        HttpClientTools.INSTANCE.post("http://localhost:7001/segment", webJsonStr);

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

        segmentsMessage.append(motanSegment);
        String segmentJsonStr = gson.toJson(segmentsMessage);
        HttpClientTools.INSTANCE.post("http://localhost:7001/segment", segmentJsonStr);
    }

//    @Test
    public void testPostSample1Segment() throws Exception {
//        HttpClientTools.INSTANCE.post("http://localhost:7001/segments", sample1);
//        HttpClientTools.INSTANCE.post("http://localhost:7001/segments", sample2);
//        HttpClientTools.INSTANCE.post("http://localhost:7001/segments", sample3);
        HttpClientTools.INSTANCE.post("http://localhost:7001/segments", test);
    }

    private String test = "[{\"ts\":\"Segment.1490346133016.1756883859.5046.1.1\",\"st\":1490346133015,\"et\":1490346133041,\"ss\":[{\"si\":0,\"ps\":-1,\"st\":1490346133020,\"et\":1490346133041,\"on\":\"H2/JDBI/Statement/execute\",\"ts\":{\"db.instance\":\"dataSource\",\"span.layer\":\"db\",\"db.type\":\"sql\",\"component\":\"H2\",\"span.kind\":\"client\",\"db.statement\":\"CREATE TABLE CACHE_TABLE ( id INTEGER PRIMARY KEY AUTO_INCREMENT, CACHE_KEY VARCHAR(30), CACHE_VALUE VARCHAR(50) )\",\"peer.host\":\"localhost\"},\"tb\":{},\"ti\":{\"peer.port\":-1},\"lo\":[]}],\"ac\":\"cache-service\",\"gt\":[\"Trace.1490346133019.1756883859.5046.1.2\"],\"sampled\":true,\"minute\":201703241702,\"hour\":201703241700,\"day\":201703240000,\"aggId\":null}]";
    private String sample1 = "[{\"ts\":\"Segment.1490166134993.56087145.45017.26.1\",\"st\":1490166134993,\"et\":1490166134996,\"rs\":[{\"ts\":\"Segment.1490166134989.142811427.45029.64.1\",\"si\":2,\"ac\":\"portal-service\",\"ph\":\"127.0.0.1:8002\"}],\"ss\":[{\"si\":2,\"ps\":1,\"st\":1490166134993,\"et\":1490166134993,\"on\":\"Jedis/getClient\",\"ts\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"span.kind\":\"client\"},\"tb\":{},\"ti\":{\"peer.port\":6379},\"lo\":[]},{\"si\":3,\"ps\":1,\"st\":1490166134993,\"et\":1490166134993,\"on\":\"Jedis/getClient\",\"ts\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"span.kind\":\"client\"},\"tb\":{},\"ti\":{\"peer.port\":6379},\"lo\":[]},{\"si\":4,\"ps\":1,\"st\":1490166134993,\"et\":1490166134993,\"on\":\"Jedis/isConnected\",\"ts\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"span.kind\":\"client\"},\"tb\":{},\"ti\":{\"peer.port\":6379},\"lo\":[]},{\"si\":5,\"ps\":1,\"st\":1490166134993,\"et\":1490166134994,\"on\":\"Jedis/ping\",\"ts\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"span.kind\":\"client\"},\"tb\":{},\"ti\":{\"peer.port\":6379},\"lo\":[]},{\"si\":6,\"ps\":1,\"st\":1490166134994,\"et\":1490166134994,\"on\":\"Jedis/get\",\"ts\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"span.kind\":\"client\",\"db.statement\":\"get test\"},\"tb\":{},\"ti\":{\"peer.port\":6379},\"lo\":[]},{\"si\":7,\"ps\":1,\"st\":1490166134996,\"et\":1490166134996,\"on\":\"H2/JDBI/PreparedStatement/executeQuery\",\"ts\":{\"db.instance\":\"dataSource\",\"span.layer\":\"db\",\"db.type\":\"sql\",\"component\":\"H2\",\"span.kind\":\"client\",\"db.statement\":\"SELECT id,CACHE_VALUE, CACHE_KEY FROM CACHE_TABLE WHERE CACHE_KEY \\u003d ?\",\"peer.host\":\"localhost\"},\"tb\":{},\"ti\":{\"peer.port\":-1},\"lo\":[]},{\"si\":8,\"ps\":1,\"st\":1490166134996,\"et\":1490166134996,\"on\":\"H2/JDBI/Connection/close\",\"ts\":{\"db.instance\":\"dataSource\",\"span.layer\":\"db\",\"db.type\":\"sql\",\"component\":\"H2\",\"span.kind\":\"client\",\"db.statement\":\"\",\"peer.host\":\"localhost\"},\"tb\":{},\"ti\":{\"peer.port\":-1},\"lo\":[]},{\"si\":1,\"ps\":0,\"st\":0,\"et\":1490166134996,\"on\":\"Motan_default_rpc_com.a.eye.skywalking.test.cache.CacheService.findCache(java.lang.String)\",\"ts\":{\"requestId\":\"1562552445165371397\"},\"tb\":{},\"ti\":{},\"lo\":[]},{\"si\":0,\"ps\":-1,\"st\":1490166134993,\"et\":1490166134996,\"on\":\"com.a.eye.skywalking.test.cache.CacheService.findCache(java.lang.String)\",\"ts\":{\"span.layer\":\"rpc\",\"component\":\"Motan\",\"span.kind\":\"server\"},\"tb\":{},\"ti\":{},\"lo\":[]}],\"ac\":\"cache-service\",\"gt\":[\"Trace.1490166134989.142811427.45029.64.2\"]},{\"ts\":\"Segment.1490166135023.56087145.45017.27.1\",\"st\":1490166135023,\"et\":1490166135026,\"rs\":[{\"ts\":\"Segment.1490166134989.142811427.45029.64.1\",\"si\":6,\"ac\":\"portal-service\",\"ph\":\"127.0.0.1:8002\"}],\"ss\":[{\"si\":2,\"ps\":1,\"st\":1490166135023,\"et\":1490166135023,\"on\":\"Jedis/getClient\",\"ts\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"span.kind\":\"client\"},\"tb\":{},\"ti\":{\"peer.port\":6379},\"lo\":[]},{\"si\":3,\"ps\":1,\"st\":1490166135023,\"et\":1490166135023,\"on\":\"Jedis/getClient\",\"ts\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"span.kind\":\"client\"},\"tb\":{},\"ti\":{\"peer.port\":6379},\"lo\":[]},{\"si\":4,\"ps\":1,\"st\":1490166135023,\"et\":1490166135023,\"on\":\"Jedis/isConnected\",\"ts\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"span.kind\":\"client\"},\"tb\":{},\"ti\":{\"peer.port\":6379},\"lo\":[]},{\"si\":5,\"ps\":1,\"st\":1490166135023,\"et\":1490166135024,\"on\":\"Jedis/ping\",\"ts\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"span.kind\":\"client\"},\"tb\":{},\"ti\":{\"peer.port\":6379},\"lo\":[]},{\"si\":6,\"ps\":1,\"st\":1490166135024,\"et\":1490166135024,\"on\":\"Jedis/set\",\"ts\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"span.kind\":\"client\",\"db.statement\":\"set test_NEW\"},\"tb\":{},\"ti\":{\"peer.port\":6379},\"lo\":[]},{\"si\":7,\"ps\":1,\"st\":1490166135025,\"et\":1490166135025,\"on\":\"H2/JDBI/PreparedStatement/executeUpdate\",\"ts\":{\"db.instance\":\"dataSource\",\"span.layer\":\"db\",\"db.type\":\"sql\",\"component\":\"H2\",\"span.kind\":\"client\",\"db.statement\":\"INSERT INTO CACHE_TABLE(CACHE_VALUE, CACHE_KEY) VALUES(?, ?)\",\"peer.host\":\"localhost\"},\"tb\":{},\"ti\":{\"peer.port\":-1},\"lo\":[]},{\"si\":8,\"ps\":1,\"st\":1490166135026,\"et\":1490166135026,\"on\":\"H2/JDBI/Connection/close\",\"ts\":{\"db.instance\":\"dataSource\",\"span.layer\":\"db\",\"db.type\":\"sql\",\"component\":\"H2\",\"span.kind\":\"client\",\"db.statement\":\"\",\"peer.host\":\"localhost\"},\"tb\":{},\"ti\":{\"peer.port\":-1},\"lo\":[]},{\"si\":1,\"ps\":0,\"st\":0,\"et\":1490166135026,\"on\":\"Motan_default_rpc_com.a.eye.skywalking.test.cache.CacheService.updateCache(java.lang.String,java.lang.String)\",\"ts\":{\"requestId\":\"1562552445196828678\"},\"tb\":{},\"ti\":{},\"lo\":[]},{\"si\":0,\"ps\":-1,\"st\":1490166135023,\"et\":1490166135026,\"on\":\"com.a.eye.skywalking.test.cache.CacheService.updateCache(java.lang.String,java.lang.String)\",\"ts\":{\"span.layer\":\"rpc\",\"component\":\"Motan\",\"span.kind\":\"server\"},\"tb\":{},\"ti\":{},\"lo\":[]}],\"ac\":\"cache-service\",\"gt\":[\"Trace.1490166134989.142811427.45029.64.2\"]}]";
    private String sample2 = "[{\"ts\":\"Segment.1490166135008.-790808105.45019.39.1\",\"st\":1490166135008,\"et\":1490166135014,\"rs\":[{\"ts\":\"Segment.1490166134989.142811427.45029.64.1\",\"si\":4,\"ac\":\"portal-service\",\"ph\":\"10.128.35.80:20880\"}],\"ss\":[{\"si\":2,\"ps\":1,\"st\":1490166135010,\"et\":1490166135010,\"on\":\"Mysql/JDBI/PreparedStatement/executeQuery\",\"ts\":{\"db.instance\":\"skywalking-test\",\"span.layer\":\"db\",\"db.type\":\"sql\",\"component\":\"Mysql\",\"span.kind\":\"client\",\"db.statement\":\"SELECT * FROM information_schema.tables WHERE table_schema \\u003d ? AND table_name \\u003d ? LIMIT 1;\",\"peer.host\":\"127.0.0.1\"},\"tb\":{},\"ti\":{\"peer.port\":3307},\"lo\":[]},{\"si\":3,\"ps\":1,\"st\":1490166135012,\"et\":1490166135013,\"on\":\"Mysql/JDBI/PreparedStatement/executeQuery\",\"ts\":{\"db.instance\":\"skywalking-test\",\"span.layer\":\"db\",\"db.type\":\"sql\",\"component\":\"Mysql\",\"span.kind\":\"client\",\"db.statement\":\"SELECT id, CACHE_KEY, CACHE_VALUE FROM CACHE_TABLE WHERE CACHE_KEY\\u003d ?\",\"peer.host\":\"127.0.0.1\"},\"tb\":{},\"ti\":{\"peer.port\":3307},\"lo\":[]},{\"si\":1,\"ps\":0,\"st\":1490166135009,\"et\":1490166135013,\"on\":\"com.a.eye.skywalking.test.persistence.PersistenceService.query(String)\",\"ts\":{\"span.layer\":\"rpc\",\"component\":\"Dubbo\",\"peer.host\":\"10.128.35.80\",\"span.kind\":\"server\",\"url\":\"rest://10.128.35.80:20880/com.a.eye.skywalking.test.persistence.PersistenceService.query(String)\"},\"tb\":{},\"ti\":{\"peer.port\":20880},\"lo\":[]},{\"si\":0,\"ps\":-1,\"st\":1490166135008,\"et\":1490166135014,\"on\":\"/persistence/query\",\"ts\":{\"span.layer\":\"http\",\"component\":\"Tomcat\",\"peer.host\":\"10.128.35.80\",\"span.kind\":\"server\",\"url\":\"http://10.128.35.80:20880/persistence/query\"},\"tb\":{},\"ti\":{\"peer.port\":61968,\"status_code\":200},\"lo\":[]}],\"ac\":\"persistence-service\",\"gt\":[\"Trace.1490166134989.142811427.45029.64.2\"]}]";
    private String sample3 = "[{\"ts\":\"Segment.1490166134989.142811427.45029.64.1\",\"st\":1490166134989,\"et\":1490166135028,\"ss\":[{\"si\":2,\"ps\":1,\"st\":1490166134992,\"et\":1490166134996,\"on\":\"com.a.eye.skywalking.test.cache.CacheService.findCache(java.lang.String)\",\"ts\":{\"span.layer\":\"rpc\",\"peer.host\":\"127.0.0.1\",\"component\":\"Motan\",\"span.kind\":\"client\",\"url\":\"motan://127.0.0.1:8002/default_rpc/com.a.eye.skywalking.test.cache.CacheService/1.0/referer\"},\"tb\":{},\"ti\":{\"peer.port\":8002},\"lo\":[]},{\"si\":1,\"ps\":0,\"st\":0,\"et\":1490166134996,\"on\":\"Motan_default_rpc_com.a.eye.skywalking.test.cache.CacheService.findCache(java.lang.String)\",\"ts\":{\"requestId\":\"1562552445165371397\"},\"tb\":{},\"ti\":{},\"lo\":[]},{\"si\":4,\"ps\":3,\"st\":1490166134997,\"et\":1490166135019,\"on\":\"/persistence/query\",\"ts\":{\"span.layer\":\"http\",\"peer.host\":\"10.128.35.80\",\"component\":\"HttpClient\",\"span.kind\":\"client\",\"url\":\"http://10.128.35.80:20880/persistence/query\"},\"tb\":{},\"ti\":{\"peer.port\":20880,\"status_code\":200},\"lo\":[]},{\"si\":3,\"ps\":0,\"st\":1490166134997,\"et\":1490166135022,\"on\":\"com.a.eye.skywalking.test.persistence.PersistenceService.query(String)\",\"ts\":{\"span.layer\":\"rpc\",\"component\":\"Dubbo\",\"peer.host\":\"10.128.35.80\",\"span.kind\":\"client\",\"url\":\"rest://10.128.35.80:20880/com.a.eye.skywalking.test.persistence.PersistenceService.query(String)\"},\"tb\":{},\"ti\":{\"peer.port\":20880},\"lo\":[]},{\"si\":6,\"ps\":5,\"st\":1490166135022,\"et\":1490166135026,\"on\":\"com.a.eye.skywalking.test.cache.CacheService.updateCache(java.lang.String,java.lang.String)\",\"ts\":{\"span.layer\":\"rpc\",\"peer.host\":\"127.0.0.1\",\"component\":\"Motan\",\"span.kind\":\"client\",\"url\":\"motan://127.0.0.1:8002/default_rpc/com.a.eye.skywalking.test.cache.CacheService/1.0/referer\"},\"tb\":{},\"ti\":{\"peer.port\":8002},\"lo\":[]},{\"si\":5,\"ps\":0,\"st\":0,\"et\":1490166135026,\"on\":\"Motan_default_rpc_com.a.eye.skywalking.test.cache.CacheService.updateCache(java.lang.String,java.lang.String)\",\"ts\":{\"requestId\":\"1562552445196828678\"},\"tb\":{},\"ti\":{},\"lo\":[]},{\"si\":0,\"ps\":-1,\"st\":1490166134989,\"et\":1490166135028,\"on\":\"/portal/\",\"ts\":{\"span.layer\":\"http\",\"component\":\"Tomcat\",\"peer.host\":\"0:0:0:0:0:0:0:1\",\"span.kind\":\"server\",\"url\":\"http://localhost:8080/portal/\"},\"tb\":{},\"ti\":{\"peer.port\":61982,\"status_code\":200},\"lo\":[]}],\"ac\":\"portal-service\",\"gt\":[\"Trace.1490166134989.142811427.45029.64.2\"]}]";
}
