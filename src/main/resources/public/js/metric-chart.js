define(['chartJs', 'moment'], function (Chart, moment) {
    function MetricChart(chartContext, startTime) {
        this.chartStartTime = startTime;
        this.chartContext = chartContext;
        this.previousTime = undefined;
        this.chartObject = function (labels) {
        };
        this.fillData = function (data) {
        }
    };

    MetricChart.prototype.buildChartObject = function (startTime) {
        this.chartObject = new Chart(this.chartContext, this.chartConfig(generateChartLabels(startTime, 300)));
    };

    function generateChartLabels(startTime, maxSize) {
        var labels = [];
        for (var i = 0; i < maxSize; i++) {
            labels.push(moment(startTime, "YYYYMMDDHHmmss").add(i, "seconds").format("HH:mm:ss"));
        }
        return labels;
    };

    function createMetricChart(ChartProtoType, chartContext, startTime) {
        function F() {
        }

        F.prototype = MetricChart.prototype;
        ChartProtoType.prototype = new F();
        ChartProtoType.prototype.constructor = ChartProtoType;

        var chart = new ChartProtoType(chartContext, startTime);
        chart.buildChartObject(startTime);

        return chart;
    };

    return {
        createMetricChart: createMetricChart,
        MetricChart: MetricChart
    }
});
