requirejs(['/main.js'], function (main) {
    requirejs(['rangeSlider', 'moment', 'daterangepicker', 'machineInfo', 'metricCharts'],
        function (rangeSlider, moment, dataRangePicker, machineInfo, metricCharts) {
            var instanceId = window.instanceId;
            var startTime = window.startTime;

            var config = {
                serverTimestamp: undefined,
                differentTime: undefined,
            };
            $.ajax({
                url: "/syncTime",
                async: false,
                timeout: 1000,
                success: function (data) {
                    config.serverTimestamp = data.timestamp;
                    config.differentTime = moment() - data.timestamp;
                }
            });

            machineInfo.loadMachineInfo(instanceId);
            metricCharts.drawMetricSelector().initPageCharts(instanceId, startTime);


            $('#startTimeInput').daterangepicker({
                singleDatePicker: true,
                timePicker: true,
                autoApply: false,
                startDate: moment(),
                timePickerIncrement: 1,
                timePicker24Hour: true,
                local: {
                    format: 'YYYY-MM-DD H:mm'
                }
            });

            $("#startTimeInput").val(moment(startTime, "YYYYMMDDHHmmss").format('YYYY-MM-DD H:mm'));
            $('#startTimeInput').on('apply.daterangepicker', function (ev, picker) {
                $(this).val(picker.startDate.format('YYYY-MM-DD H:mm'));
                $(this).attr("timestamp", picker.startDate);
                metricCharts.updateMetricCharts(parseInt($(this).attr("timestamp")));
            });

            $("#autoUpdate").change(function () {
                if ($("#autoUpdate").prop('checked')) {
                    $("#startTimeInput").prop("disabled", true);
                    $("#startTimeInput").val("");
                    $("#startTimeInput").attr("timestamp", "");
                    metricCharts.redrawChart(moment().subtract(config.differentTime));
                    window.updateChartTimeTask = setInterval(function () {
                        metricCharts.autoUpdateMetricCharts(moment().subtract(config.differentTime).format("x"));
                    }, 1000);
                } else {
                    window.stopAutoUpdateMetricCharts();
                }
            });


            window.stopAutoUpdateMetricCharts = function () {
                $("#startTimeInput").prop('disabled', false);
                clearInterval(window.updateChartTimeTask);
                $("#autoUpdate").prop("checked", false);
            }

        });
});
