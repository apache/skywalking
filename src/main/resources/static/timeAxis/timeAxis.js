/**
 * @author pengys5
 */
define(["jquery", "moment", "text!timeAxisHtml", "rangeSlider", "daterangepicker"], function ($, moment, timeAxisHtml, ionRangeSlider, daterangepicker) {
    var vueData = {
        timeBucket: 0,
        handler: undefined
    };
    var slider;

    function load() {
        $.ajaxSettings.async = false;
        $.getJSON("/time/sync/allInstance", function (data) {
            vueData.timeBucket = data.timeBucket;
        });
        return this;
    }

    function render(renderToDivId) {
        $("#" + renderToDivId).html(timeAxisHtml);
        var endTimeStr = moment(vueData.timeBucket, "YYYYMMDDHHmmss").format("YYYYMMDDHHmm");
        var startTimeStr = moment(vueData.timeBucket, "YYYYMMDDHHmmss").subtract(1, "hours").format("YYYYMMDDHHmm");

        $("#timeAxisInput").ionRangeSlider({
            min: moment(startTimeStr, "YYYYMMDDHHmm").format("x"),
            max: moment(endTimeStr, "YYYYMMDDHHmm").format("x"),
            from: moment(endTimeStr, "YYYYMMDDHHmm").format("x"),
            grid: true,
            force_edges: true,
            prettify: function (num) {
                var m = moment(num, "x").locale("ru");
                return m.format("MM/DD HH:mm");
            },
            onStart: function (data) {
                var startTime = moment(data.min).format("YYYYMMDDHHmm");
                var endTime = moment(data.from).format("YYYYMMDDHHmm");
                console.log("startTime: " + startTime + ", endTime: " + endTime);
                vueData.handler(startTime, endTime);
            },
            onChange: function (data) {
                var startTime = moment(data.min).format("YYYYMMDDHHmm");
                var endTime = moment(data.from).format("YYYYMMDDHHmm");
                console.log("startTime: " + startTime + ", endTime: " + endTime);
                vueData.handler(startTime, endTime);
            }
        });
        slider = $("#timeAxisInput").data("ionRangeSlider");
    }

    function registryTimeChangedHandler(handler) {
        vueData.handler = handler;
        return this;
    }

    function updateTimeAxis(from, to) {
        console.log("update time axis");
        slider.update({
            min: moment(from, "YYYYMMDDHHmm").format("x"),
            max: moment(to, "YYYYMMDDHHmm").format("x"),
            from: +moment().subtract(0, "minutes").format("x")
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
        registryTimeChangedHandler: registryTimeChangedHandler
    }
});