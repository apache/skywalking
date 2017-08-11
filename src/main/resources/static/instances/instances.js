requirejs(['/main.js'], function (main) {
    requirejs(['jquery', 'applicationList', 'appInstance', 'instanceTimeAxis'],
        function ($, applicationList, appInstance, timeAxis) {
            window.autoUpdateAppInstanceCharts = function () {
                window.updateChartTimeTask = setTimeout("window.autoUpdateappInstanceCharts()", 1000);
            }

            window.stopAutoUpdateAppInstanceCharts = function () {
            }

            timeAxis.draw();
            applicationList.loadApplications();
            appInstance.drawCanvas().loadInstancesData();

        });
});
