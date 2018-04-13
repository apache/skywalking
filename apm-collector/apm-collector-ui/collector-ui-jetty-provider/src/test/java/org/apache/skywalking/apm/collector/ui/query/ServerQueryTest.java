package org.apache.skywalking.apm.collector.ui.query;

import org.apache.skywalking.apm.collector.storage.ui.common.Duration;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.ui.service.ServerService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import java.text.ParseException;

/**
 * @author lican
 * @date 2018/4/13
 */
public class ServerQueryTest {

    private ServerService serverService;
    private ServerQuery serverQuery;

    @Before
    public void setUp() throws Exception {
        serverQuery = new ServerQuery(null);
        serverService = Mockito.mock(ServerService.class);
        Whitebox.setInternalState(serverQuery, "serverService", serverService);
    }

    @Test
    public void searchServer() throws ParseException {

        Mockito.when(serverService.searchServer(Mockito.anyString(),
                Mockito.anyLong(), Mockito.anyLong())).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(20170100000000L, arguments[1]);
            Assert.assertEquals(20170299999999L, arguments[2]);
            return null;
        });
        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);

        serverQuery.searchServer("keyword", duration);
    }

    @Test
    public void getAllServer() throws ParseException {
        Mockito.when(serverService.getAllServer(Mockito.anyInt(),
                Mockito.anyLong(), Mockito.anyLong())).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(20170100000000L, arguments[1]);
            Assert.assertEquals(20170299999999L, arguments[2]);
            return null;
        });
        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);

        serverQuery.getAllServer(-1, duration);
    }

    @Test
    public void getServerResponseTimeTrend() throws ParseException {
        Mockito.when(serverService.getServerResponseTimeTrend(Mockito.anyInt(), Mockito.anyObject(),
                Mockito.anyLong(), Mockito.anyLong())).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(201701L, arguments[2]);
            Assert.assertEquals(201702L, arguments[3]);
            return null;
        });
        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);

        serverQuery.getServerResponseTimeTrend(-1, duration);
    }

    @Test
    public void getServerTPSTrend() throws ParseException {
        Mockito.when(serverService.getServerTPSTrend(Mockito.anyInt(), Mockito.anyObject(),
                Mockito.anyLong(), Mockito.anyLong())).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(201701L, arguments[2]);
            Assert.assertEquals(201702L, arguments[3]);
            return null;
        });
        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);

        serverQuery.getServerTPSTrend(-1, duration);
    }

    @Test
    public void getCPUTrend() throws ParseException {
        Mockito.when(serverService.getCPUTrend(Mockito.anyInt(), Mockito.anyObject(),
                Mockito.anyLong(), Mockito.anyLong())).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(201701L, arguments[2]);
            Assert.assertEquals(201702L, arguments[3]);
            return null;
        });
        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);

        serverQuery.getCPUTrend(-1, duration);
    }

    @Test
    public void getGCTrend() throws ParseException {
        Mockito.when(serverService.getGCTrend(Mockito.anyInt(), Mockito.anyObject(),
                Mockito.anyLong(), Mockito.anyLong())).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(201701L, arguments[2]);
            Assert.assertEquals(201702L, arguments[3]);
            return null;
        });
        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);

        serverQuery.getGCTrend(-1, duration);
    }

    @Test
    public void getMemoryTrend() throws ParseException {
        Mockito.when(serverService.getMemoryTrend(Mockito.anyInt(), Mockito.anyObject(),
                Mockito.anyLong(), Mockito.anyLong())).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(201701L, arguments[2]);
            Assert.assertEquals(201702L, arguments[3]);
            return null;
        });
        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);

        serverQuery.getMemoryTrend(-1, duration);
    }
}