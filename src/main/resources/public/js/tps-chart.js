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
    function TPSMetricChart(chartContext, startTime) {
        metricChart.MetricChart.apply(this, arguments);
        this.chartConfig = function (labels) {
            return {
                type: 'bar',
                data: {
                    labels: labels,
                    datasets: [{
                        label: "TPS",
                        borderWidth: 1,
                        borderColor: window.chartColors.tpsBorder,
                        backgroundColor: window.chartColors.tpsBorder,
                        data: [],
                        yAxisID: "y-axis-tps",
                        type: "line",
                        fill: false,
                        radius: 0,
                    },
                        {
                            label: "Response Time (ms)",
                            borderWidth: 0,
                            backgroundColor: window.chartColors.respTimeColor,
                            data: [],
                            pointHoverRadius: 1,
                            radius: 0,
                            yAxisID: "y-axis-responseTime",
                        }
                    ]
                },
                options: {
                    scales: {
                        xAxes: [{
                            gridLines: {
                                display: false
                            },
                            ticks: {
                                callback: function (dataLabel, index) {
                                    return index % 20 === 0 ? dataLabel : null;
                                },
                                maxRotation: 0,
                                padding: 20
                            }
                        }],
                        yAxes: [{
                            display: false,
                            id: 'y-axis-responseTime',
                            stacked: true,
                            position: "right",
                            scaleLabel: {
                                display: true
                            },
                            gridLines: {
                                display: true
                            },
                            ticks: {
                                callback: function (dataLabel, index) {
                                    var label = dataLabel.toString();
                                    for (var index = label.length; index <= 5; index++) {
                                        label = label + " ";
                                    }
                                    return label;
                                }
                            }
                        },
                            {

                                id: 'y-axis-tps',
                                stacked: true,
                                position: "left",
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
                            },
                        ]
                    },
                    maintainAspectRatio: false,
                    legend: {
                        display: true,
                        position: 'top'
                    },
                    tooltips: {
                        mode: 'index',
                        intersect: false,
                    }
                }
            };
        };
        this.fillData = function (data, startTime, endTime) {
            var beginIndexOfFillData = (this.toUnixTimestamp(startTime) - this.toUnixTimestamp(this.chartStartTime)) / 1000;
            var endIndexOfFillData = (this.toUnixTimestamp(endTime) - this.toUnixTimestamp(this.chartStartTime)) / 1000;
            console.log("beginIndexOfFillData : " + beginIndexOfFillData + " endIndexOfFillData : " + endIndexOfFillData);
            for (var index = beginIndexOfFillData, dataIndex = 0; index <= endIndexOfFillData ; index++, dataIndex++) {
                if (index > 299) {
                    console.log("update chart x axes");
                    this.updateXAxes();
                    this.chartObject.data.datasets[0].data.shift();
                    this.chartObject.data.datasets[1].data.shift();
                }
                this.fillResponseTimeData(data.respTime[dataIndex]);
                this.fillTpsData(data.tps[dataIndex]);
                this.updateChartStartTime(index);
            }
            this.chartObject.update();
        };
        this.fillResponseTimeData = function (data) {
            this.chartObject.data.datasets[1].data.push(data);
        };
        this.fillTpsData = function (data) {
            this.chartObject.data.datasets[0].data.push(data);
        };
    }

    function createChart(chartContext, startTime) {
        var chart = metricChart.createMetricChart(TPSMetricChart, chartContext, startTime);
        return chart;
    }

    return {
        createChart: createChart
    }
})
