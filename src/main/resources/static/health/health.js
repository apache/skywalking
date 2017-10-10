/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking-ui
 */

requirejs(['/main.js'], function () {
    requirejs(['jquery', 'vue', 'head', 'applicationList', 'appInstance', 'timeAxis', 'bootstrap'],
        function ($, Vue, head, applicationList, appInstance, timeAxis) {
            var vueData = {
                list: [],
                applicationList: [],
                handler: undefined,
                endTime: 0
            };

            applicationList.registryItemClickHandler(function (applicationList) {
                vueData.applicationList = applicationList;

                if (vueData.applicationList.length > 0) {
                    appInstance.loadInstancesData(vueData.endTime, vueData.applicationList);
                }
                appInstance.drawCanvas();
            }).render("applicationListDiv");

            var i = 2;
            timeAxis.second().autoUpdate().load().registryTimeChangedHandler(function (timeBucketType, startTime, endTime) {
                console.log("time changed, start time: " + startTime + ", end time: " + endTime);
                vueData.endTime = endTime;

                if (i == 2) {
                    applicationList.load(timeBucketType, startTime, endTime);
                    i = 0;
                }
                i++;

                if (vueData.applicationList.length > 0) {
                    appInstance.loadInstancesData(endTime, vueData.applicationList);
                }
                appInstance.drawCanvas();
            }).render("timeAxisDiv");
        });
});
