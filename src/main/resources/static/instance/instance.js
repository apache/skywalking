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

requirejs(['/main.js'], function (main) {
    requirejs(['rangeSlider', 'head', 'moment', 'daterangepicker', 'machineInfo', 'metricCharts', 'jsCookie'],
        function (rangeSlider, head, moment, dataRangePicker, machineInfo, metricCharts, jsCookie) {
            var instanceId = jsCookie.get("instanceId");

            var config = {
                serverTimeBucket: undefined,
                differentTime: undefined,
            };
            $.ajax({
                url: "/time/sync/oneInstance?instanceId=" + instanceId,
                async: false,
                timeout: 1000,
                success: function (data) {
                    config.serverTimeBucket = data.timeBucket;
                    config.differentTime = moment().format("YYYYMMDDHHmmss") - data.timeBucket;
                }
            });
            console.log("serverTimeBucket: " + config.serverTimeBucket);
            console.log("differentTime: " + config.differentTime);

            machineInfo.loadMachineInfo(instanceId);
            metricCharts.drawMetricSelector().initPageCharts(instanceId, config.serverTimeBucket);

            $('#startTimeInput').daterangepicker({
                singleDatePicker: true,
                timePicker: true,
                autoApply: false,
                startDate: moment(),
                timePickerIncrement: 1,
                timePicker24Hour: true,
                local: {
                    format: 'YYYY-MM-DD H:mm'
                }
            });

            $("#startTimeInput").val(moment(config.serverTimeBucket, "YYYYMMDDHHmmss").format('YYYY-MM-DD H:mm'));
            $('#startTimeInput').on('apply.daterangepicker', function (ev, picker) {
                $(this).val(picker.startDate.format('YYYY-MM-DD H:mm'));
                $(this).attr("timestamp", picker.startDate);
                metricCharts.updateMetricCharts(moment($(this).attr("timestamp"), "x").format("YYYYMMDDHHmmss"));
            });

            $("#autoUpdate").change(function () {
                if ($("#autoUpdate").prop('checked')) {
                    $("#startTimeInput").prop("disabled", true);
                    $("#startTimeInput").val("");
                    $("#startTimeInput").attr("timestamp", "");
                    metricCharts.updateMetricCharts(moment().subtract(config.differentTime, "seconds").format("YYYYMMDDHHmmss"));
                    window.updateChartTimeTask = setInterval(function () {
                        metricCharts.autoUpdateMetricCharts(moment().subtract(config.differentTime, "seconds").format("YYYYMMDDHHmmss"));
                    }, 1000);
                } else {
                    window.stopAutoUpdateMetricCharts();
                }
            });

            window.stopAutoUpdateMetricCharts = function () {
                $("#startTimeInput").prop('disabled', false);
                clearInterval(window.updateChartTimeTask);
                $("#autoUpdate").prop("checked", false);
            }
        });
});
