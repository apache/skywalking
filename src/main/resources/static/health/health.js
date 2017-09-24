requirejs(['/main.js'], function () {
    requirejs(['jquery', 'vue', 'head', 'applicationList', 'appInstance', 'timeAxis', 'bootstrap'],
        function ($, Vue, head, applicationList, appInstance, timeAxis) {
            var vueData = {
                list: [],
                applicationList: [],
                handler: undefined
            };

            applicationList.registryItemClickHandler(function (applicationList) {
                vueData.applicationList = applicationList;
            }).render("applicationListDiv");

            var i = 2;
            timeAxis.second().autoUpdate().load().registryTimeChangedHandler(function (timeBucketType, startTime, endTime) {
                console.log("time changed, start time: " + startTime + ", end time: " + endTime);

                if (i == 2) {
                    applicationList.load(timeBucketType, startTime, endTime);
                    i = 0;
                }
                i++;

                if (vueData.applicationList.length > 0) {
                    appInstance.loadInstancesData(endTime, vueData.applicationList);
                }
                appInstance.drawCanvas();
            }).render("timeAxisDiv");
        });
});
