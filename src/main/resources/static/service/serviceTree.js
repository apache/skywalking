requirejs(['/main.js'], function () {
    requirejs(['jquery', 'vue', 'head', 'timeAxis', "applicationList", "entryServiceList", "serviceTreeList"], function ($, Vue, head, timeAxis, applicationList, entryServiceList, serviceTreeList) {
        var vueData = {
            list: [],
            applicationId: 0,
            entryServiceName: "",
            handler: undefined,
            startTime: 0,
            endTime: 0
        };
        var vue = new Vue({
            el: '#applicationListVueDiv',
            data: vueData,
            methods: {
                goClick: function () {
                    entryServiceReLoad(vueData.startTime, vueData.endTime);
                }
            }
        });

        timeAxis.load().registryTimeChangedHandler(function (timeBucketType, startTime, endTime) {
            console.log("time changed, start time: " + startTime + ", end time: " + endTime);
            load(timeBucketType, startTime, endTime);

            vueData.startTime = startTime;
            vueData.endTime = endTime;
        }).render("timeAxisDiv");

        function entryServiceReLoad(startTime, endTime) {
            entryServiceList.load(vueData.applicationId, vueData.entryServiceName, startTime, endTime).registryIdItemClickHandler(function (entryServiceId) {
                console.log("entryServiceId: " + entryServiceId);
                serviceTreeList.loadByEntryServiceId(entryServiceId, startTime, endTime).render("serviceTreeListDiv");
            }).registryNameItemClickHandler(function (entryApplicationId, entryServiceName) {
                console.log("entryApplicationId: " + entryApplicationId + ", entryServiceName: " + entryServiceName);
                serviceTreeList.loadByEntryServiceName(entryApplicationId, entryServiceName, startTime, endTime).render("serviceTreeListDiv");
            }).render("entryServiceListDiv");
        };

        function load(timeBucketType, startTime, endTime) {
            $.ajaxSettings.async = false;
            vueData.list = [];
            $.getJSON("/applications?timeBucketType=minute&startTime=" + startTime + "&endTime=" + endTime, function (data) {
                console.log(data);
                vueData.list.push({applicationId: 0, applicationCode: "All"});
                for (var i = 0; i < data.length; i++) {
                    vueData.list.push({applicationId: data[i].applicationId, applicationCode: data[i].applicationCode});
                }
            });

            return this;
        }
    });
});
