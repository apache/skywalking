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

/**
 * @author peng-yongsheng
 */
define(["jquery", "vue", "moment", "text!timeAxisHtml", "rangeSlider", "daterangepicker", "timers"], function ($, Vue, moment, timeAxisHtml, ionRangeSlider, daterangepicker) {
    var vueData = {
        timeBucket: 0,
        hasAutoUpdate: false,
        handler: undefined,
        starting: true,
    };
    var vue;
    var slider;
    var dateFormat = "YYYYMMDDHHmm";
    var sliderFormat = "MM/DD HH:mm";
    var subtract = "hours";
    var subtractValue = 1;
    var timeBucketType = "minute";
    var timeDifferent = 0;

    function load() {
        $.ajaxSettings.async = false;
        $.getJSON("/time/sync/allInstance", function (data) {
            timeDifferent = moment().format("x") - moment(data.timeBucket, "YYYYMMDDHHmmss").format("x");
            vueData.timeBucket = moment(data.timeBucket, "YYYYMMDDHHmmss").format(dateFormat);
            console.log("timeDifferent: " + timeDifferent);
        });
        return this;
    }

    function _calCurrentTimeBucket() {
        return moment(moment().format("x") - timeDifferent, "x");
    }

    function minute() {
        dateFormat = "YYYYMMDDHHmm";
        sliderFormat = "MM/DD HH:mm";
        subtract = "hours";
        subtractValue = 1;
        timeBucketType = "minute";
        return this;
    }

    function second() {
        dateFormat = "YYYYMMDDHHmmss";
        sliderFormat = "MM/DD HH:mm:ss";
        subtract = "minutes";
        subtractValue = 3;
        timeBucketType = "second";
        return this;
    }

    function autoUpdate() {
        vueData.hasAutoUpdate = true;
        $('body').everyTime('2s', function () {
            var bucketMoment = _calCurrentTimeBucket();
            vueData.timeBucket = bucketMoment.format("YYYYMMDDHHmmss");
            console.log("auto update, end time: " + vueData.timeBucket);
            var endTimeStr = bucketMoment.format(dateFormat);
            var startTimeStr = bucketMoment.subtract(subtractValue, subtract).format(dateFormat);
            updateTimeAxis(startTimeStr, endTimeStr);
        });
        return this;
    }

    function stopAutoUpdate() {
        $('body').stopTime();
    }

    function render(renderToDivId) {
        $("#" + renderToDivId).html(timeAxisHtml);
        var endTimeStr = moment(vueData.timeBucket, "YYYYMMDDHHmmss").format(dateFormat);
        var startTimeStr = moment(vueData.timeBucket, "YYYYMMDDHHmmss").subtract(subtractValue, subtract).format(dateFormat);

        $("#timeAxisInput").ionRangeSlider({
            min: moment(startTimeStr, dateFormat).format("x"),
            max: moment(endTimeStr, dateFormat).format("x"),
            from: moment(endTimeStr, dateFormat).format("x"),
            grid: true,
            force_edges: true,
            prettify: function (num) {
                var m = moment(num, "x").locale("ru");
                return m.format(sliderFormat);
            },
            onStart: function (data) {
                _callHandler(data);
            },
            onChange: function (data) {
                _callHandler(data);
            },
            onUpdate: function (data) {
                _callHandler(data);
            }
        });
        slider = $("#timeAxisInput").data("ionRangeSlider");

        vue = new Vue({
            el: '#timeAxisButton',
            data: vueData,
            methods: {
                startOrStop: function () {
                    if (vueData.starting) {
                        vueData.starting = false;
                        $("#timeAxisButtonIcon").removeClass("fa-pause").addClass("fa-play");
                        stopAutoUpdate();
                    } else {
                        vueData.starting = true;
                        $("#timeAxisButtonIcon").removeClass("fa-play").addClass("fa-pause");
                        autoUpdate();
                    }
                }
            }
        });

        new Vue({
            el: '#dataRangeBtn',
            data: vueData,
            methods: {
                openCalendar: function () {
                    bindDatePicker();
                }
            }
        });
    }

    function registryTimeChangedHandler(handler) {
        vueData.handler = handler;
        return this;
    }

    function _callHandler(data) {
        var startTime = moment(data.min).format(dateFormat);
        var endTime = moment(data.from).format(dateFormat);
        console.log("startTime: " + startTime + ", endTime: " + endTime);
        vueData.handler(timeBucketType, startTime, endTime);
    }

    function updateTimeAxis(from, to) {
        console.log("update time axis, from:" + from + ", to:" + to);
        slider.update({
            min: moment(from, dateFormat).format("x"),
            max: moment(to, dateFormat).format("x"),
            from: moment(to, dateFormat).format("x")
        });
    }

    function bindDatePicker() {
        $("#dateRangeInput").val(moment().format("MM/DD/YYYY HH:mm"));

        $('#dateRangeInput').daterangepicker({
            singleDatePicker: true,
            timePicker: true,
            timePicker24Hour: true,
            timePickerSeconds: true,
            maxDate: moment(),
            "opens": "left",
            locale: {
                format: 'MM/DD/YYYY HH:mm:ss'
            }
        }, function (start) {
            vueData.starting = false;
            $("#timeAxisButtonIcon").removeClass("fa-pause").addClass("fa-play");
            stopAutoUpdate();

            var endTimeStr = start.format(dateFormat);
            var startTimeStr = start.subtract(subtractValue, subtract).format(dateFormat);
            updateTimeAxis(startTimeStr, endTimeStr);
        });

        $("#dataRangeBtn").click(function () {
            var drp = $('#dateRangeInput').data('daterangepicker');
            drp.show();
        });
    }

    return {
        load: load,
        render: render,
        autoUpdate: autoUpdate,
        minute: minute,
        second: second,
        registryTimeChangedHandler: registryTimeChangedHandler
    }
});