/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.testcase.zookeeper.controller;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/case")
@PropertySource("classpath:application.properties")
public class ZookeeperController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZookeeperController.class);

    @Autowired
    private ZooKeeper zooKeeper;

    @RequestMapping("/zookeeper-case")
    @ResponseBody
    public String zookeeperCase() throws KeeperException, InterruptedException {
        zooKeeper.create("/path", "data".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        zooKeeper.exists("/path", watcher);
        zooKeeper.delete("/path", -1);
        return "Success";
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() {
        return "healthCheck";
    }

    private Watcher watcher = new Watcher() {
        @Override
        public void process(WatchedEvent event) {
            try {
                zooKeeper.exists("/path", this);
            } catch (Exception e) {
                LOGGER.error("error", e);
            }
        }
    };
}
