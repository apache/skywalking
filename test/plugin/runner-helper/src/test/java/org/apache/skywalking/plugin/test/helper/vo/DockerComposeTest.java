///*
// * Licensed to the Apache Software Foundation (ASF) under one or more
// * contributor license agreements.  See the NOTICE file distributed with
// * this work for additional information regarding copyright ownership.
// * The ASF licenses this file to You under the Apache License, Version 2.0
// * (the "License"); you may not use this file except in compliance with
// * the License.  You may obtain a copy of the License at
// *
// *    http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package org.apache.skywalking.plugin.test.helper.vo;
//
//import java.io.File;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.io.InputStream;
//import org.junit.After;
//import org.junit.Before;
//import org.junit.Test;
//import org.yaml.snakeyaml.DumperOptions;
//import org.yaml.snakeyaml.Yaml;
//import org.yaml.snakeyaml.introspector.Property;
//import org.yaml.snakeyaml.nodes.NodeTuple;
//import org.yaml.snakeyaml.nodes.Tag;
//import org.yaml.snakeyaml.representer.Representer;
//
//import static org.hamcrest.CoreMatchers.is;
//import static org.junit.Assert.assertNotNull;
//import static org.junit.Assert.assertThat;
//import static org.junit.Assert.assertTrue;
//
//public class DockerComposeTest {
//    private InputStream dockerCompose;
//    private String writeFile;
//
//    @Before
//    public void setUp() {
//        writeFile = CaseIConfigurationTest.class.getResource("/").getFile() + "/file.yaml";
//        dockerCompose = CaseIConfigurationTest.class.getResourceAsStream("/docker-compose-test.yml");
//        assertNotNull(dockerCompose);
//    }
//
//    @Test
//    public void readDockerCompose() {
//        Yaml yaml = new Yaml();
//        DockerCompose dockerfile = yaml.loadAs(dockerCompose, DockerCompose.class);
//        assertNotNull(dockerfile);
//        assertThat(dockerfile.getServices().size(), is(2));
//    }
//
//    @Test
//    public void writeDockerCompose() throws IOException {
//        DumperOptions dumperOptions = new DumperOptions();
//        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
//        Representer representer = new Representer() {
//            @Override
//            protected NodeTuple representJavaBeanProperty(Object javaBean, Property property, Object propertyValue,
//                Tag customTag) {
//                if (propertyValue == null) {
//                    return null;
//                } else {
//                    return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
//                }
//            }
//        };
//
//        representer.addClassTag(DockerCompose.class, Tag.MAP);
//        Yaml yaml = new Yaml(representer, dumperOptions);
//
//        DockerCompose dockerfile = yaml.loadAs(dockerCompose, DockerCompose.class);
//        FileWriter writer = new FileWriter(writeFile);
//        yaml.dump(dockerfile, writer);
//
//        assertTrue(new File(writeFile).exists());
//    }
//
//    @After
//    public void tearDown(){
//        new File(writeFile).deleteOnExit();
//    }
//}
