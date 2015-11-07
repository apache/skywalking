package com.ai.cloud.skywalking.conf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.Test;

import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.conf.ConfigInitializer;

public class ConfigInitailizerTest {
	@Test
    public void testInitialize2() throws IOException, IllegalAccessException{
		Properties p = new Properties();  
    	InputStream in = ConfigInitializer.class.getResourceAsStream("/config.properties");
    	p.load(in);  
        in.close();
        
        ConfigInitializer.initialize(p, Config.class);
	}
}
