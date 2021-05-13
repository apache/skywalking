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

package org.apache.skywalking.apm.testcase.xxljob.job;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.skywalking.apm.testcase.xxljob.Utils;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class MethodJob {

    @XxlJob("MethodJobHandler")
    public ReturnT<String> work(String param) throws IOException {

        log.info("MethodJobHandler execute. param: {}", param);

        Request request = new Request.Builder().url("http://localhost:8080/xxl-job-2.x-scenario/case/methodJob").build();
        Response response = Utils.OK_CLIENT.newCall(request).execute();
        response.body().close();

        return ReturnT.SUCCESS;
    }
}
