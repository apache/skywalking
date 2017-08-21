define(['jquery', 'rangeSlider', 'moment', 'appInstance', 'text!instanceTimeAxisHtml'], function ($, rangeSlider, moment, appInstance, segmentHtml) {
    var config = {
        timeAxis: {
            timer: undefined,
            rangeSlider: undefined,
            differentTime: undefined,
            queryInstanceTime: 0
        },
        queryParam: {
            applicationIds: [],
            responseTime: undefined
        },
        timeHandler: undefined,
        start: function () {
            var that = config;
            var dTime = +moment() - $("#timeAxis").val();
            var isFirstEntry = true;
            var queryInstanceTime = $("#timeAxis").val();
            that.timeAxis.timerTask = window.setInterval(function () {
                that.timeAxis.rangeSlider.update({
                    disable: true,
                    max: +moment().subtract(that.timeAxis.differentTime).subtract(10, "seconds").format("x"),
                    from: +moment().subtract(dTime).format("x")
                });

                var currentTime = $("#timeAxis").val();
                if (isFirstEntry || currentTime - that.timeAxis.queryInstanceTime >= 5000) {
                    that.timeHandler(that.timeAxis.queryInstanceTime, config.queryParam);
                    that.timeAxis.queryInstanceTime += 5000;
                    isFirstEntry = false;
                }

            }, 1000);
        },
        stop: function () {
            if (this.timeAxis.timerTask != undefined) {
                window.clearInterval(this.timeAxis.timerTask);
            }
            this.timeAxis.rangeSlider.update({
                disable: false,
            });
        }
    }

    function draw(serverTime, differentTime) {
        $("#timeAxisDiv").html(segmentHtml);
        config.timeAxis.rangeSlider = initRangeSlider();
        bindButtonEvent(differentTime);
        config.timeAxis.differentTime = differentTime;
        config.timeAxis.queryInstanceTime = serverTime;
        console.log(window.applicationId);
        config.queryParam.applicationIds.push(window.applicationId);
        config.start();
        return this;
    }

    function initRangeSlider(differentTime) {
        $("#timeAxis").ionRangeSlider({
            min: +moment().subtract(1, "hours").format("x"),
            max: +moment().subtract(differentTime).subtract(10, "seconds").format("x"),
            from: +moment().subtract(10, "seconds").format("x"),
            grid: true,
            step: 60000,
            prettify: function (num) {
                var m = moment(num, "x").locale("zh-cn");
                return m.format("MM/DD H:mm:ss");
            }
        });

        return $("#timeAxis").data("ionRangeSlider");
    }

    function bindButtonEvent() {
        $("#timeAxisButton").click(function () {
            if ($(this).is(".fa-play")) {
                $(this).removeClass("fa-play").addClass("fa-pause");
                config.start();
            } else {
                $(this).removeClass("fa-pause").addClass("fa-play");
                config.stop();
            }
        });
    }

    function registryTimerHandle(handler) {
        config.timeHandler = handler;
        return this;
    }

    function addAppId(applicationId) {
        config.queryParam.applicationIds.push(parseInt(applicationId));
        console.log("add application Id : " + applicationId);
    }

    function removeAppId(applicationId) {
        var applicationIdInt = parseInt(applicationId);
        var index = 0
        for (; index < config.queryParam.applicationIds.length; index++) {
            if (config.queryParam.applicationIds[index] == applicationIdInt) {
                break;
            }
        }
        config.queryParam.applicationIds.splice(index, 1);
        console.log("remainder applicationIds:" + config.queryParam.applicationIds.toString());
    }
    function addResponseTimeQueryParam(responseTime) {
        config.queryParam.responseTime = responseTime;
    }

    return {
        draw: draw,
        registryTimerHandle: registryTimerHandle,
        addAppId: addAppId,
        removeAppId: removeAppId,
        addResponseTimeQueryParam: addResponseTimeQueryParam
    }
});
