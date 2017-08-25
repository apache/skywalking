/**
 * @author pengys5
 */
define(["jquery", "moment", "text!timeAxisHtml", "rangeSlider", "daterangepicker", "alarm", "dagDraw", "text!dagHtml", "timers"], function ($, moment, timeAxisHtml, rangeSlider, daterangepicker, alarm, dagDraw, dagHtml) {
    var isAutoUpdate = false;
    var slider;

    function create(divId) {
        $("#" + divId).html(timeAxisHtml);

        $("#isUpdateBtn").click(function () {
            autoUpdate();
        });

        _resize();
        createTimeAxis();
        bindDatePicker();
    }

    function _startAutoUpdate() {
        console.log("start auto update");
        $('body').everyTime('5s', function () {
        })
    }

    function _stopAutoUpdate() {
        $('body').stopTime();
    }

    function autoUpdate() {
        if (isAutoUpdate) {
            $("#isUpdateBtn").removeClass("btn-info").addClass("btn-default");
            isAutoUpdate = false;
            _stopAutoUpdate();
        } else {
            $("#isUpdateBtn").removeClass("btn-default").addClass("btn-info");
            isAutoUpdate = true;
            _startAutoUpdate();
        }
    }

    function createTimeAxis() {
        var endTimeStr = moment().format("YYYYMMDDHHmm");
        var startTimeStr = moment().subtract(1, "hours").format("YYYYMMDDHHmm");

        $("#timeAxisComponentDiv").ionRangeSlider({
            min: moment(startTimeStr, "YYYYMMDDHHmm").format("x"),
            max: moment(endTimeStr, "YYYYMMDDHHmm").format("x"),
            from: +moment().subtract(0, "minutes").format("x"),
            grid: true,
            force_edges: true,
            prettify: function (num) {
                var m = moment(num, "x").locale("ru");
                return m.format("MM/DD HH:mm");
            },
            onChange: function (data) {
                console.log(data);

                var startTime = moment(data.from).format("YYYYMMDDHHmm");
                var endTime = moment(data.to).format("YYYYMMDDHHmm");
                console.log("startTime: " + startTime + ", endTime: " + endTime);
                dagDraw.loadDateRangeDag(startTime, endTime);
            }
        });
        slider = $("#timeAxisComponentDiv").data("ionRangeSlider");

        dagDraw.loadDateRangeDag(startTimeStr, endTimeStr);
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
            dagDraw.loadDateRangeDag(startTimeStr, endTimeStr);
        });

        $("#dataRangeBtn").click(function () {
            var drp = $('#dateRangeInput').data('daterangepicker');
            drp.show();
        });
    }

    function _resize() {
        var width = $("#axisRowDiv").width();
        $("#axisDiv").width(width - 150);
    }

    $(window).resize(function () {
        _resize();
    });

    return {
        create: create
    }
});