define(['chartJs', 'moment', 'metric-chart'], function (Chart, moment, metricChart) {

    function MemoryMetricChart(chartContext, startTime) {
        metricChart.MetricChart.apply(this, arguments);
        this.chartConfig = function (labels) {
            return {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        label: "Used (MB)",
                        borderWidth: 1,
                        borderColor: window.chartColors.jvmUsedColor,
                        backgroundColor: window.chartColors.jvmUsedBgColor,
                        data: [],
                        fill: true,
                        pointHoverRadius: 1,
                        radius: 0,
                    },
                        {
                            label: "Max (MB)",
                            borderWidth: 1,
                            borderColor: window.chartColors.jvmMaxColor,
                            backgroundColor: window.chartColors.jvmMaxColor,
                            data: [],
                            fill: false,
                            radius: 0,
                        }
                    ]
                },
                options: {
                    maintainAspectRatio: false,
                    legend: {
                        display: true,
                        position: 'top'
                    },
                    tooltips: {
                        mode: 'index',
                        intersect: false,
                    },
                    scales: {
                        xAxes: [{
                            gridLines: {
                                display: false
                            },
                            ticks: {
                                maxRotation: 0,
                                padding: 20,
                                callback: function (dataLabel, index) {
                                    return index % 20 === 0 ? dataLabel : '';
                                },
                                autoSkip: true
                            }
                        }],
                        yAxes: [{
                            position: 'left',
                            display: true,
                            scaleLabel: {
                                display: true
                            },
                            gridLines: {
                                display: true
                            },
                            ticks: {
                                callback: function (dataLabel, index) {
                                    var label = dataLabel.toString();
                                    var blank = "";
                                    for (var index = label.length; index <= 5; index++) {
                                        blank = blank + " ";
                                    }

                                    return label + blank;
                                }
                            }
                        }]
                    }
                }
            };
        };

        this.updateYAxesData = function (index, data) {
            this.chartObject.data.datasets[0].data[index] = data.used;
            this.chartObject.data.datasets[1].data[index] = data.max;
            if (this.max == undefined && data.max != undefined) {
                this.max = data.max;
            }
            if (this.init == undefined && data.init != undefined) {
                this.init = data.init;
            }
            this.chartObject.options.scales.yAxes[0].ticks.min = this.init == undefined ? 0 : this.init;
            this.chartObject.options.scales.yAxes[0].ticks.max = this.max == undefined ? 0 : this.max + 200;
        };

        this.fillLostData = function (baseTime, dataCount) {
            for (var j = 1; j < dataCount; j++) {
                var index = this.calculateDataIndex(baseTime.addSeconds(j));
                this.updateYAxesData(index, {
                    used: 0,
                    max: this.max == undefined ? undefined : this.max,
                    init: this.init == undefined ? undefined : this.init
                });
            }
        };
    }

    function createChart(chartContext, startTime) {
        return metricChart.createMetricChart(MemoryMetricChart, chartContext, startTime);
    }

    return {
        createChart: createChart
    }
})
