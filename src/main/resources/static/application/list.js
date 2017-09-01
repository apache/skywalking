define(['jquery', 'vue', 'text!applicationListHtml'], function ($, Vue, applicationListHtml) {
    var vueData = {
        list: [],
        handler: undefined,
        applicationMap: {}
    };
    var vue;

    function render(renderToDivId) {
        $("#" + renderToDivId).html(applicationListHtml);
        vue = new Vue({
            el: '#applicationListVueDiv',
            data: vueData,
            methods: {
                itemClick: function (applicationId, selected) {
                    console.log("selected: " + selected);
                    if (selected) {
                        vueData.applicationMap[applicationId] = false;
                    } else {
                        vueData.applicationMap[applicationId] = true;
                    }

                    var applicationList = [];
                    for (var application in vueData.applicationMap) {
                        console.log(application);
                        console.log(vueData.applicationMap[application]);

                        if (vueData.applicationMap[application]) {
                            applicationList.push(application);
                        }
                    }
                    vueData.handler(applicationList);
                }
            }
        });
        return this;
    }

    function load(timeBucketType, startTime, endTime) {
        $.ajaxSettings.async = false;
        $.getJSON("/applications", {
            timeBucketType: timeBucketType,
            startTime: startTime,
            endTime: endTime
        }, function (data) {
            console.log(data);
            vueData.list = data;
            for (var i = 0; i < vueData.list.length; i++) {
                if (vueData.applicationMap.hasOwnProperty(vueData.list[i].applicationId)) {
                    vueData.list[i].selected = vueData.applicationMap[vueData.list[i].applicationId];
                } else {
                    vueData.list[i].selected = false;
                }
            }
        });
        return this;
    }

    function registryItemClickHandler(handler) {
        vueData.handler = handler;
        return this;
    }

    return {
        load: load,
        render: render,
        registryItemClickHandler: registryItemClickHandler
    }
});
