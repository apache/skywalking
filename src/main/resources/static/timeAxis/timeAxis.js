/**
 * @author pengys5
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
    var timeBucket;
    var sliderFormat = "MM/DD HH:mm";
    var subtract = "hours";
    var subtractValue = 1;
    var timeBucketType = "minute";

    function load() {
        $.ajaxSettings.async = false;
        $.getJSON("/time/sync/allInstance", function (data) {
            vueData.timeBucket = data.timeBucket;
            timeBucket = data.timeBucket;
        });
        return this;
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
            timeBucket = moment(timeBucket, "YYYYMMDDHHmmss").add(2, "seconds").format("YYYYMMDDHHmmss");
            vueData.timeBucket = moment(timeBucket, "YYYYMMDDHHmmss").format(dateFormat);
            console.log("timeBucket : " + timeBucket + " vue time bucket : " + vueData.timeBucket);

            var endTimeStr = moment(vueData.timeBucket, "YYYYMMDDHHmmss").format(dateFormat);
            var startTimeStr = moment(vueData.timeBucket, "YYYYMMDDHHmmss").subtract(subtractValue, subtract).format(dateFormat);
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
                        $("#timeAxisButton").removeClass("fa-pause").addClass("fa-play");
                        stopAutoUpdate();
                    } else {
                        vueData.starting = true;
                        $("#timeAxisButton").removeClass("fa-play").addClass("fa-pause");
                        autoUpdate();
                    }
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
            timePickerSeconds: false,
            maxDate: moment(),
            "opens": "left",
            locale: {
                format: 'MM/DD/YYYY HH:mm'
            }
        }, function (start) {
            var endTimeStr = start.format("YYYYMMDDHHmm");
            var startTimeStr = start.subtract(1, "hours").format("YYYYMMDDHHmm");

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