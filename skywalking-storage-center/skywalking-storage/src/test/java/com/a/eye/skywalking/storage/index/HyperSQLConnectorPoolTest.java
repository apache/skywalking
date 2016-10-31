package com.a.eye.skywalking.storage.index;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by xin on 2016/10/31.
 */
@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({HyperSQLConnector.class, HyperSQLConnectorPool.class})
public class HyperSQLConnectorPoolTest {



    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void getOrCreateConnector() throws Exception {

    }

    @Test
    public void createConnector() throws Exception {
        for (int i = 0; i < 100; i++) {
            long index = System.currentTimeMillis();
            HyperSQLConnector connector = HyperSQLConnectorPool.createConnector(index);
            assertNotNull(connector);
            if (i < 30) {
                assertEquals(i + 1, HyperSQLConnectorPool.getCurrentConnectorsSize());
            } else {
                assertEquals(30, HyperSQLConnectorPool.getCurrentConnectorsSize());
            }

            assertEquals(HyperSQLConnectorPool.getIndex()[0], index);
        }

    }

}
