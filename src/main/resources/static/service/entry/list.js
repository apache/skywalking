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

define(['jquery', 'vue', 'text!entryServiceListHtml'], function ($, Vue, entryServiceListHtml) {
    var vueData = {
        list: [],
        size: 10,
        pageSize: 0,
        currentPage: 0,
        hasForward: true,
        backward: true,
        applicationId: 0,
        entryServiceName: "",
        startTime: 0,
        endTime: 0,
        idHandler: undefined,
        nameHandler: undefined
    };
    var vue;

    function render(renderToDivId) {
        $("#" + renderToDivId).html(entryServiceListHtml);
        vue = new Vue({
            el: '#entryServiceListVueDiv',
            data: vueData,
            methods: {
                idItemClick: function (entryServiceId, event) {
                    if ($(event.target).is(".operation-selected")) {
                        $(event.target).removeClass("operation-selected");
                    } else {
                        $(event.target).addClass("operation-selected");
                    }
                    vueData.idHandler(entryServiceId);
                },
                nameItemClick: function (entryApplicationId, entryServiceName, event) {
                    if ($(event.target).is(".operation-selected")) {
                        $(event.target).removeClass("operation-selected");
                    } else {
                        $(event.target).addClass("operation-selected");
                    }
                    vueData.nameHandler(entryApplicationId, entryServiceName);
                },
                gotoPage: function (pageNum) {
                    vueData.currentPage = pageNum;
                    load(vueData.applicationId, vueData.entryServiceName, vueData.startTime, vueData.endTime);
                }
            }
        });

        return this;
    }

    function load(applicationId, entryServiceName, startTime, endTime) {
        var path = "/service/entry/entryServiceList?timeBucketType=minute";
        path = path + "&applicationId=" + applicationId;
        path = path + "&entryServiceName=" + entryServiceName;
        path = path + "&startTime=" + startTime;
        path = path + "&endTime=" + endTime;
        path = path + "&from=" + vueData.currentPage * vueData.size;
        path = path + "&size=" + vueData.size;

        vueData.applicationId = applicationId;
        vueData.entryServiceName = entryServiceName;
        vueData.startTime = startTime;
        vueData.endTime = endTime;

        $.getJSON(path, function (data) {
            console.log(data);
            vueData.list = data.array;
            vueData.pageSize = data.total % vueData.size;
            console.log("page size: " + vueData.pageSize);
        });

        if (vueData.currentPage == 0) {
            vueData.hasForward = false;
        } else {
            vueData.hasForward = true;
        }

        if (vueData.currentPage + 1 == vueData.pageSize) {
            vueData.backward = false;
        } else {
            vueData.backward = true;
        }
        return this;
    }

    function registryIdItemClickHandler(handler) {
        vueData.idHandler = handler;
        return this;
    }

    function registryNameItemClickHandler(handler) {
        vueData.nameHandler = handler;
        return this;
    }

    return {
        load: load,
        render: render,
        registryIdItemClickHandler: registryIdItemClickHandler,
        registryNameItemClickHandler: registryNameItemClickHandler
    }
});
