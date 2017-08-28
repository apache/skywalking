define(['chartJs', 'moment', 'metric-chart'], function (Chart, moment, metricChart) {

        function GCMetricChart(chartContext, startTime) {
            metricChart.MetricChart.apply(this, arguments);
            this.chartConfig = function (labels) {
                return {
                    type: 'bar',
                    data: {
                        labels: labels,
                        datasets: [{
                            label: "Young GC",
                            backgroundColor: window.chartColors.ygcDataColor,
                            borderColor: window.chartColors.ygcDataColor,
                            data: [],
                        },
                            {
                                label: "Old GC",
                                backgroundColor: window.chartColors.ogcDataColor,
                                borderColor: window.chartColors.ogcDataColor,
                                data: [],
                            }
                        ]
                    },
                    options: {
                        maintainAspectRatio: false,
                        tooltips: {
                            mode: 'index',
                            intersect: false,
                        },
                        scales: {
                            xAxes: [{
                                stacked: true,
                                gridLines: {
                                    display: false
                                },
                                ticks: {
                                    callback: function (dataLabel, index) {
                                        return index % 20 == 0 ? dataLabel : '';
                                    },
                                    maxRotation: 0,
                                    padding: 20
                                }
                            }],
                            yAxes: [{
                                stacked: true,
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
                                    },
                                }
                            }]
                        },
                        legend: {
                            display: true,
                            position: 'top'
                        },
                    }
                };
            };
            this.updateYAxesData = function (index, data) {
                this.chartObject.data.datasets[0].data[index] = data.ygc;
                this.chartObject.data.datasets[1].data[index] = data.ogc;
            }

            this.fillLostData = function (baseTime, dataCount) {
                for (var j = 1; j < dataCount; j++) {
                    var index = this.calculateDataIndex(baseTime.addSeconds(j));
                    this.updateYAxesData(index, {ygc: 0, ogc: 0});
                }
            }
        }

        function createChart(chartContext, startTime) {
            return metricChart.createMetricChart(GCMetricChart, chartContext, startTime);
        }

        return {
            createChart: createChart
        }
    }
)
