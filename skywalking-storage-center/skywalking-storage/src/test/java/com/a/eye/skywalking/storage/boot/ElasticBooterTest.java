package com.a.eye.skywalking.storage.boot;

import org.junit.After;
import org.junit.Test;

import java.io.File;

import static com.a.eye.skywalking.storage.boot.ElasticBooter.DATA_INDEX_HOME;
import static org.junit.Assert.assertEquals;

public class ElasticBooterTest {
    private String bastPath = ElasticBooterTest.class.getResource("/").getPath() + ".." + File.separator;


    @Test
    public void fetchElasticHomeWithoutProperty() {
        ElasticBooter booter = new ElasticBooter();
        assertEquals("Elastic Home :", booter.fetchElasticHome(), bastPath + "install/data/index");
    }

    @Test
    public void fetchElasticHomeWithProperty() {
        System.setProperty(DATA_INDEX_HOME, "/test/test");
        ElasticBooter fetcher = new ElasticBooter();
        assertEquals("Elastic Home :", fetcher.fetchElasticHome(), "/test/test");
    }

    @After
    public void tearUp() {
        System.clearProperty(DATA_INDEX_HOME);
    }
}
