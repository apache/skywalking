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

package org.apache.skywalking.apm.testcase.thrift.client.controller;

import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.skywalking.apm.testcase.thrift.client.service.IClient;
import org.apache.skywalking.apm.testcase.thrift.client.service.SyncClient;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/case")
public class CaseController {
    private static final Logger logger = LogManager.getLogger(CaseController.class);

    private static final String SUCCESS = "Success";
    private IClient client = new SyncClient(new TSocket("localhost", 9091));
    private IClient client = new SyncClient(new TSocket("localhost", 9091));
    private IClient client = new SyncClient(new TSocket("localhost", 9091));
    private IClient client = new SyncClient(new TSocket("localhost", 9091));

    @RequestMapping("/thrift-scenario")
    @ResponseBody
    public String testcase() throws IOException, TException {
        client.echo("skywalking");
        return SUCCESS;
    }

    @RequestMapping("/healthCheck")
    @ResponseBody
    public String healthCheck() throws IOException, TTransportException {
        client.start();
        return SUCCESS;
    }

}