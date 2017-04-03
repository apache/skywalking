/**
 * @author pengys5
 */
define(["jquery", "text!alarmHtml", "echarts", "walden"], function ($, alarmHtml, echarts, walden) {
    function create(divId) {
        $("#" + divId).html(alarmHtml);
        resizeAlarmListDivSize();
    }

    function resizeAlarmListDivSize() {
        var height = $(document).height();
        console.log("height: " + height);
        // $("#alarmContent").height(height - 30);
    }

    function loadCostMetric(data) {
        var costChart = echarts.init(document.getElementById('costMetricDiv'), 'walden');
        var option = {
            title: {
                text: 'Cost',
                textStyle: {fontStyle: 'normal', fontWeight: 'normal', fontSize: 14}
            },
            grid: {top: 40},
            tooltip: {
                trigger: 'axis'
            },
            toolbox: {
                right: 30,
                feature: {
                    myMore: {
                        show: true,
                        title: 'more',
                        icon: 'image://./public/img/more_red.png',
                        onclick: function () {
                            window.open("/trace/trace.html");
                        }
                    }
                }
            },
            legend: {
                data: ['1s', '3s', '5s', 'slow'],
                bottom: 10
            },
            xAxis: [
                {
                    type: 'category',
                    boundaryGap: false,
                    data: data.xAxis
                }
            ],
            yAxis: [
                {
                    type: 'value'
                }
            ],
            series: [
                {
                    name: '1s',
                    type: 'line',
                    smooth: true,
                    itemStyle: {normal: {areaStyle: {type: 'default'}}},
                    data: data.s1Axis
                },
                {
                    name: '3s',
                    type: 'line',
                    smooth: true,
                    itemStyle: {normal: {areaStyle: {type: 'default'}}},
                    data: data.s3Axis
                },
                {
                    name: '5s',
                    type: 'line',
                    smooth: true,
                    itemStyle: {normal: {areaStyle: {type: 'default'}}},
                    data: data.s5Axis
                },
                {
                    name: 'slow',
                    type: 'line',
                    smooth: true,
                    itemStyle: {normal: {areaStyle: {type: 'default'}}},
                    data: data.slowAxis
                }
            ],
            color: ["#3fb1e3", "#6be6c1", "#626c91", "#a0a7e6", "#FA8072"],
        };
        costChart.setOption(option);
    }

    function loadExceptionMetric(data) {
        var exceptionChart = echarts.init(document.getElementById('exceptionMetricDiv'), 'walden');
        var option = {
            title: {
                text: 'Exception',
                textStyle: {fontStyle: 'normal', fontWeight: 'normal', fontSize: 14}
            },
            grid: {top: 40},
            tooltip: {
                trigger: 'axis'
            },
            toolbox: {
                right: 30,
                feature: {
                    myMore: {
                        show: true,
                        title: 'more',
                        icon: 'image://./public/img/more_red.png',
                        onclick: function () {
                            window.open("/trace/trace.html");
                        }
                    }
                }
            },
            legend: {
                data: ['success', 'error'],
                bottom: 10
            },
            xAxis: [
                {
                    type: 'category',
                    boundaryGap: false,
                    data: data.xAxis
                }
            ],
            yAxis: [
                {
                    type: 'value'
                }
            ],
            series: [
                {
                    name: 'success',
                    type: 'line',
                    smooth: true,
                    itemStyle: {normal: {areaStyle: {type: 'default'}}},
                    data: data.successAxis
                },
                {
                    name: 'error',
                    type: 'line',
                    smooth: true,
                    itemStyle: {normal: {areaStyle: {type: 'default'}}},
                    data: data.errorAxis
                }
            ]
        };
        exceptionChart.setOption(option);
    }

    function loadCostData(slice, startTimeStr, endTimeStr) {
        $.getJSON("costDataLoad?timeSliceType=" + slice + "&startTime=" + startTimeStr + "&endTime=" + endTimeStr, function (data) {
            loadCostMetric(data);
            loadExceptionMetric(data);
        });
    }

    return {
        create: create,
        loadCostData: loadCostData,
        resizeAlarmListDivSize: resizeAlarmListDivSize
    }
});


window.onresize = function () {
    require(["alarm"], function (alarm) {
        alarm.resizeAlarmListDivSize();
    });
}