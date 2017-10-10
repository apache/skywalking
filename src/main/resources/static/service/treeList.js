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

define(['jquery', 'text!serviceTreeListHtml', "treeTable"], function ($, serviceTreeListHtml, treetable) {
        var vueData = {
            list: [],
            handler: undefined
        };

        function render(renderToDivId) {
            $("#" + renderToDivId).html(serviceTreeListHtml);
            $("#serviceTreeTableId").treetable({
                expandable: true,
                onInitialized: function () {
                    var rows = "";
                    for (var i = 0; i < vueData.list.length; i++) {
                        var row = vueData.list[i];
                        if (row.frontServiceId == 1) {
                            rows = rows + "<tr data-tt-id=" + row.behindServiceId + "><td>" + row.behindServiceName + "</td>";
                        } else {
                            rows = rows + "<tr data-tt-id=" + row.behindServiceId + " data-tt-parent-id=" + row.frontServiceId + "><td>" + row.behindServiceName + "</td>";
                        }
                        rows = rows + "<td>" + row.s1Lte + "</td>";
                        rows = rows + "<td>" + row.s3Lte + "</td>";
                        rows = rows + "<td>" + row.s5Lte + "</td>";
                        rows = rows + "<td>" + row.s5Gt + "</td>";
                        rows = rows + "<td>" + row.error + "</td>";
                        rows = rows + "<td>" + row.summary + "</td>";
                        rows = rows + "<td>" + (row.costSummary / row.summary).toFixed(2) + "</td>";
                        rows = rows + "<td>" + (((row.summary - row.error) / row.summary) * 100).toFixed(2) + "%</td>";
                        rows = rows + "</tr>";
                    }
                    $("#serviceTreeTableId").treetable("loadBranch", null, rows);
                }
            });
            return this;
        }

        function loadByEntryServiceId(entryServiceId, startTime, endTime) {
            var param = {
                timeBucketType: "minute",
                entryServiceId: entryServiceId,
                startTime: startTime,
                endTime: endTime
            };

            $.getJSON("/service/tree/entryServiceId", param, function (data) {
                vueData.list = data;
            });
            return this;
        }

        function loadByEntryServiceName(entryApplicationId, entryServiceName, startTime, endTime) {
            var param = {
                timeBucketType: "minute",
                entryApplicationId: entryApplicationId,
                entryServiceName: entryServiceName,
                startTime: startTime,
                endTime: endTime
            };

            $.getJSON("/service/tree/entryServiceName", param, function (data) {
                vueData.list = data;
            });
            return this;
        }

        function registryItemClickHandler(handler) {
            vueData.handler = handler;
            return this;
        }

        return {
            loadByEntryServiceId: loadByEntryServiceId,
            loadByEntryServiceName: loadByEntryServiceName,
            render: render,
            registryItemClickHandler: registryItemClickHandler
        }
    }
);
