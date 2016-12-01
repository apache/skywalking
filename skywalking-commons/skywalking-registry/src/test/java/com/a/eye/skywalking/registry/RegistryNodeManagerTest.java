package com.a.eye.skywalking.registry;

import com.a.eye.skywalking.registry.api.RegistryNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 2016/12/1.
 */
public class RegistryNodeManagerTest {

    RegistryNodeManager connectionURLManager = new RegistryNodeManager();
    private List<RegistryNode> connectionURLS;
    private List<String> url;

    @Before
    public void setUp() {
        url = new ArrayList<String>() {{
            add("127.0.0.1:34000");
            add("127.0.0.1:35000");
        }};

        connectionURLS = connectionURLManager.calculateChangeOfConnectionURL(url);
    }

    @Test
    public void calculateInitCandition() throws Exception {

        Assert.assertEquals(connectionURLS.size(), 2);
        for (RegistryNode connectionURL : connectionURLS) {
            Assert.assertEquals(connectionURL.getChangeType(), RegistryNode.ChangeType.ADDED);
        }

        Assert.assertEquals(2, connectionURLManager.getConnectionURLOfPreviousChanged().size());
    }


    @Test
    public void calculateContainAdd() throws Exception {
        url.add("127.0.0.1:36000");
        url.add("127.0.0.1:37000");
        connectionURLS = connectionURLManager.calculateChangeOfConnectionURL(url);
        Assert.assertEquals(4, connectionURLManager.getConnectionURLOfPreviousChanged().size());
        Assert.assertEquals(2, connectionURLS.size());
        for (RegistryNode connectionURL : connectionURLS) {
            Assert.assertEquals(connectionURL.getChangeType(), RegistryNode.ChangeType.ADDED);
        }
    }


}