/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.testcase.feign.controller;

import feign.Body;
import feign.Feign;
import feign.Headers;
import feign.Logger;
import feign.Param;
import feign.RequestLine;
import feign.codec.Decoder;
import feign.gson.GsonDecoder;
import org.apache.skywalking.apm.testcase.feign.entity.User;

public interface RestRequest {

    @RequestLine("GET /get/{id}")
    User getById(@Param("id") int id);

    @RequestLine("POST /create/")
    @Headers("Content-Type: application/json")
    @Body("%7B\"id\": \"{id}\", \"userName\": \"{userName}\"%7D")
    void createUser(@Param("id") int id, @Param("userName") String userName);

    @RequestLine("PUT /update/{id}")
    @Headers("Content-Type: application/json")
    @Body("%7B\"id\": \"{id}\", \"userName\": \"{userName}\"%7D")
    User updateUser(@Param("id") int id, @Param("userName") String userName);

    @RequestLine("DELETE /delete/{id}")
    void deleteUser(@Param("id") int id);

    static RestRequest connect() {
        Decoder decoder = new GsonDecoder();
        return Feign.builder()
            .decoder(decoder)
            .logger(new Logger.ErrorLogger())
            .logLevel(Logger.Level.BASIC)
            .target(RestRequest.class, "http://localhost:8080/feign-scenario");
    }
}
