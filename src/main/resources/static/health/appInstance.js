define(['jquery', 'vue', 'text!appInstanceHtml', 'instanceChart', 'chartJs'], function ($, Vue, segmentHtml, instanceChart, Chart) {
    var showingInstances = {
        appInstances: [],
        indexApplication: function (application) {
            var applicationIndex = -1;
            $.each(this.appInstances, function (index, object) {
                if (this.applicationId == application.applicationId) {
                    applicationIndex = index;
                    return;
                }
            });
            return applicationIndex;
        },
        indexInstance: function (applicationIndex, instanceId) {
            var application = this.appInstances[applicationIndex];
            var instanceIndex = -1;
            $.each(application.instances, function (index, object) {
                if (this.id == instanceId) {
                    instanceIndex = index;
                    return;
                }
            });
            return instanceIndex;
        }
    }

    var instanceChartChart = {
        charts: {},
        contain: function (instanceId) {
            for (var key in this.charts) {
                if (key == instanceId) {
                    return true;
                }
            }
            return false;
        },
        getChart: function (instanceId) {
            return this.charts[instanceId + ""];
        },
        addChart: function (instanceId, chart) {
            this.charts[instanceId + ""] = chart;
        }
    }

    Vue.directive('instance', {
        bind: function (el, binding) {
            var instance = binding.value;
            var config = instanceChart.createCanvasConfig(binding.value);
            var ctx = el.getContext("2d");
            var chart = new Chart(ctx, config);
            instanceChartChart.addChart(instance.id, chart);
        },
        update: function (el, binding) {
            var instance = binding.value;
            var chart = instanceChartChart.getChart(instance.id);
            instanceChart.updateCanvas(chart, instance);
        }
    })

    function drawCanvas() {
        $("#instances").html(segmentHtml);
        vue = new Vue({
            el: '#instances',
            data: showingInstances,
            methods: {
                showMore: function (application) {
                    application.showCount += 12;
                }
            }
        });
        return this;
    }

    function findInstance(instances, instanceId) {
        var instanceIndex = -1;
        $.each(instances, function (index) {
            if (this.id == instanceId) {
                instanceIndex = index;
                return;
            }
        });
        return instanceIndex;
    }

    function loadInstancesData(timeBucket, applicationIds) {
        console.log("applicationIds: " + applicationIds);
        showingInstances.appInstances = [];
        $.getJSON("/health/instances", {timeBucket: timeBucket, applicationIds: applicationIds}, function (data) {
            $.each(data.appInstances, function () {
                var applicationIndex = showingInstances.indexApplication(this);
                if (applicationIndex == -1) {
                    this.showCount = 12;
                    showingInstances.appInstances.push(this);
                } else {
                    var showInstances = showingInstances.appInstances[applicationIndex].instances;
                    $.each(this.instances, function (index, object) {
                        var instanceIndex = showingInstances.indexInstance(applicationIndex, this.id);
                        if (instanceIndex == -1) {
                            showInstances.push(this);
                        } else {
                            showInstances[instanceIndex].status = this.status;
                            showInstances[instanceIndex].tps = this.tps;
                            showInstances[instanceIndex].avg = this.avg;
                            showInstances[instanceIndex].ygc = this.ygc;
                            showInstances[instanceIndex].healthLevel = this.healthLevel;
                            showInstances[instanceIndex].status = this.status;
                            showInstances[instanceIndex].ogc = this.ogc;
                        }
                    });
                    $.each(showInstances, function (index, object) {
                        var instanceIndex = findInstance(this.instances, object.id);
                        if (instanceIndex == -1) {
                            showInstances.splice[index];
                        }
                    });
                }
            });
        });
    }


    return {
        loadInstancesData: loadInstancesData,
        drawCanvas: drawCanvas
    }
});