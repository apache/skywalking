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

package test.apache.skywalking.testcase.okhttp.controller;

import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/case")
public class CaseController {

    @RequestMapping("/receiveContext-1")
    @ResponseBody
    public String receiveContextService1() throws InterruptedException {
        return "receiveContext-1";
    }

    @RequestMapping("/receiveContext-0")
    @ResponseBody
    public String receiveContextService0() throws InterruptedException {
        return "receiveContext-0";
    }

    @RequestMapping("/okhttp-case")
    @ResponseBody
    public String okHttpScenario() {
        // Like gateway forward trace header.
        Request request = new Request.Builder().url("http://127.0.0.1:8080/okhttp-case/case/receiveContext-0")
                                               .header("sw8", "123456").build();

        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                //Never do this
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Request request = new Request.Builder().url("http://127.0.0.1:8080/okhttp-case/case/receiveContext-1")
                                                       .header("sw8", "123456")
                                                       .build();
                new OkHttpClient().newCall(request).execute();
            }
        });

        return "Success";
    }

    @RequestMapping(value = "/healthCheck")
    @ResponseBody
    public String healthCheck() {
        return "Success";
    }
}
