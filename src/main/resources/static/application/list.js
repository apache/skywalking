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

define(['jquery', 'vue', 'text!applicationListHtml'], function ($, Vue, applicationListHtml) {
    var vueData = {
        list: [],
        handler: undefined,
        applicationMap: {}
    };
    var vue;

    function render(renderToDivId) {
        $("#" + renderToDivId).html(applicationListHtml);
        vue = new Vue({
            el: '#applicationListVueDiv',
            data: vueData,
            methods: {
                itemClick: function (applicationId, selected) {
                    console.log("selected: " + selected);
                    if (selected) {
                        vueData.applicationMap[applicationId] = false;
                    } else {
                        vueData.applicationMap[applicationId] = true;
                    }

                    var applicationList = [];
                    for (var application in vueData.applicationMap) {
                        console.log(application);
                        console.log(vueData.applicationMap[application]);

                        if (vueData.applicationMap[application]) {
                            applicationList.push(application);
                        }
                    }
                    vueData.handler(applicationList);
                }
            }
        });
        return this;
    }

    function load(timeBucketType, startTime, endTime) {
        $.ajaxSettings.async = false;
        $.getJSON("/applications", {
            timeBucketType: timeBucketType,
            startTime: startTime,
            endTime: endTime
        }, function (data) {
            console.log(data);
            vueData.list = data;
            for (var i = 0; i < vueData.list.length; i++) {
                if (vueData.applicationMap.hasOwnProperty(vueData.list[i].applicationId)) {
                    vueData.list[i].selected = vueData.applicationMap[vueData.list[i].applicationId];
                } else {
                    vueData.list[i].selected = false;
                }
            }
        });
        return this;
    }

    function registryItemClickHandler(handler) {
        vueData.handler = handler;
        return this;
    }

    return {
        load: load,
        render: render,
        registryItemClickHandler: registryItemClickHandler
    }
});
