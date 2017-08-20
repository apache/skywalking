requirejs(['/main.js'], function (main) {
    requirejs(['jquery', 'applications', 'appInstance', 'healthTimeAxis', 'moment'],
        function ($, applications, appInstance, timeAxis, moment) {
            var config = {
                serverTimestamp: undefined,
                differentTime: undefined,
            };
            $.ajax({
                url: "/syncTime",
                async: false,
                success: function (data) {
                    config.serverTimestamp = data.timestamp;
                    config.differentTime = moment() - data.timestamp;
                }
            })

            timeAxis.draw(config.differentTime).registryTimerHandle(function (timestamp, applicationIds) {
                appInstance.loadInstancesData(timestamp, applicationIds)
            });

            applications.draw().loadApplications(config.serverTimestamp).registryAppIdOperationHandler(function (applicationId, isRemove) {
                if (isRemove) {
                    timeAxis.removeAppId(applicationId);
                } else {
                    timeAxis.addAppId(applicationId);
                }
            }).startTimeTask();

            appInstance.drawCanvas();
        });
});
