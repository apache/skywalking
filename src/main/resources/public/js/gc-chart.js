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
                                    min:0,
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
                        },
                        legend: {
                            display: true,
                            position: 'top'
                        },
                    }
                };
            };
            this.fillData = function (data, startTime, endTime) {
                console.log(data);
                var beginIndexOfFillData = (this.toUnixTimestamp(startTime) - this.toUnixTimestamp(this.chartStartTime)) / 1000;
                var endIndexOfFillData = (this.toUnixTimestamp(endTime) - this.toUnixTimestamp(this.chartStartTime)) / 1000;
                var maxGcCount = 0
                for (var index = beginIndexOfFillData, dataIndex = 0; index <= endIndexOfFillData; index++, dataIndex++) {
                    if (index > 299) {
                        console.log("update chart x axes");
                        this.updateXAxes();
                        this.chartObject.data.datasets[0].data.shift();
                        this.chartObject.data.datasets[1].data.shift();
                    }
                    this.chartObject.data.datasets[0].data.push(data[dataIndex].ygc);
                    this.chartObject.data.datasets[1].data.push(data[dataIndex].ogc);
                    if (maxGcCount < (data[dataIndex].ygc + data[dataIndex].ogc)){
                        maxGcCount = data[dataIndex].ygc + data[dataIndex].ogc;
                    }
                    this.updateChartStartTime(index);
                }
                this.chartObject.options.scales.yAxes[0].ticks.max = maxGcCount + 10;
                this.chartObject.update();
            };
        }

        function createChart(chartContext, startTime) {
            var chart = metricChart.createMetricChart(GCMetricChart, chartContext, startTime);
            return chart;
        }

        return {
            createChart: createChart
        }
    }
)
