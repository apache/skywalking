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

package org.apache.skywalking.apm.testcase.xxljob.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.skywalking.apm.testcase.xxljob.Utils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class XXLJobServerControlService {

    @Value("${xxl.job.admin.addresses}")
    private String xxlJobAdminAddresses;
    @Value("${xxl.job.executor.appname}")
    private String appName;

    private String cookie;

    private void login() throws Exception {
        Request request = new Request.Builder()
                .url(String.format("%s/login", xxlJobAdminAddresses))
                .method("POST", RequestBody.create(Utils.FORM_DATA, "userName=admin&password=123456"))
                .build();
        Response response = Utils.OK_CLIENT.newCall(request).execute();

        if (response.isSuccessful()) {
            JobResult result = Utils.JSON.readValue(response.body().byteStream(), JobResult.class);
            if (result.getCode() == 200) {
                this.cookie = response.headers().get("Set-Cookie");
                return;
            }
        }

        throw new IllegalStateException("xxl-job login error!");
    }

    public void checkCurrentExecutorRegistered() throws Exception {
        login();

        Request request = new Request.Builder()
                .url(String.format("%s/jobgroup/pageList", xxlJobAdminAddresses))
                .method("POST", RequestBody.create(Utils.FORM_DATA, String.format("appname=%s", appName)))
                .header("Cookie", cookie)
                .build();
        Response response = Utils.OK_CLIENT.newCall(request).execute();
        if (response.isSuccessful()) {
            JobGroup jobGroup = Utils.JSON.readValue(response.body().byteStream(), JobGroup.class);
            JobGroupData[] jobGroupDatas = jobGroup.getData();
            if (jobGroupDatas != null && jobGroupDatas.length == 1) {
                JobGroupData jobGroupData = jobGroupDatas[0];
                if (!StringUtils.isEmpty(jobGroupData.getAddressList())
                        && jobGroupData.getRegistryList() != null
                        && jobGroupData.getRegistryList().length > 0) {
                    return;
                }
            }

        }

        throw new IllegalStateException("current executor unregistered");
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class JobResult {
        @JsonProperty
        private int code;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class JobGroup {
        @JsonProperty
        private JobGroupData[] data;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class JobGroupData {
        @JsonProperty
        private int id;
        @JsonProperty
        private String appname;
        @JsonProperty
        private String addressList;
        @JsonProperty
        private String[] registryList;
    }
}
