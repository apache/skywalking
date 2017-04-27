package com.a.eye.skywalking.collector.worker.tools;

import com.a.eye.skywalking.collector.worker.segment.entity.Span;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
public class SpanPeersToolsTestCase {

    @Test
    public void testNotEmptyPeers() {
        Span span = mock(Span.class);
        when(span.getStrTag("peers")).thenReturn("Test");

        String peers = SpanPeersTools.INSTANCE.getPeers(span);
        Assert.assertEquals("[Test]", peers);
    }

    @Test
    public void testEmptyPeers() {
        Span span = mock(Span.class);
        when(span.getStrTag("peers")).thenReturn(null);
        when(span.getStrTag("peer.host")).thenReturn("localhost");
        when(span.getStrTag("peer.PORT")).thenReturn("8080");

        String peers = SpanPeersTools.INSTANCE.getPeers(span);
        Assert.assertEquals("[localhost:0]", peers);
    }
}
