define(['jquery', 'rangeSlider', 'moment', 'appInstance', 'text!instanceTimeAxisHtml'], function ($, rangeSlider, moment, appInstance, segmentHtml) {
    var config = {
        timeAxis: {
            timer: undefined,
            differentTime: undefined,
            rangeSlider: undefined,
            dTime: undefined
        },
        queryParam: {
            applicationIds: []
        },
        timeHandler: undefined,
        start: function () {
            var that = config;
            that.timeAxis.dTime = +moment() - $("#timeAxis").val();
            that.timeAxis.timerTask = window.setInterval(function () {
                that.timeAxis.rangeSlider.update({
                    disable: true,
                    max: +moment().subtract(that.timeAxis.differentTime).subtract(10, "seconds").format("x"),
                    from: +moment().subtract(that.timeAxis.dTime).format("x")
                });

                that.timeHandler($("#timeAxis").val(), config.queryParam.applicationIds);
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

    function draw(differentTime) {
        $("#timeAxisDiv").html(segmentHtml);
        config.timeAxis.rangeSlider = initRangeSlider();
        bindButtonEvent(differentTime);
        config.timeAxis.differentTime = differentTime;
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

    return {
        draw: draw,
        registryTimerHandle: registryTimerHandle,
        addAppId: addAppId,
        removeAppId: removeAppId
    }
});
