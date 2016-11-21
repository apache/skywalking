package com.a.eye.skywalking.storage.boot;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ElasticConfigModifierTest {

    private File configDir = new File(ElasticConfigModifierTest.class.getResource("/").getFile(), "test");
    File elasticSearchConfigFile = new File(configDir + File.separator + "config", "elasticsearch.yml");

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {
        Files.delete(elasticSearchConfigFile.toPath());
        Files.delete(new File(configDir, "config").toPath());
        Files.delete(configDir.toPath());
    }

    @Test
    public void testReplaceConfig() throws Exception {
        ElasticConfigModifier modifier = new ElasticConfigModifier(configDir.getPath());
        modifier.replaceConfig(18080);

        assertTrue(elasticSearchConfigFile.exists());

        Yaml yaml = new Yaml();
        HashMap<String, Integer> config = (HashMap<String, Integer>) yaml.load(new FileInputStream
                (elasticSearchConfigFile));
        assertEquals(18080,config.get("transport.tcp.port").intValue());
    }
}
