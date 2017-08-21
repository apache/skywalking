define(['jquery', 'vue', 'text!responseTimeConditionHtml'], function ($, Vue, segmentHtml) {

    var config = {
        data: {
            responseTimes: [
                {value: "slow", id: "slow", style: "text-danger"},
                {value: "5 s", id: "slow", style: "text-warning"},
                {value: "3 s", id: "slow", style: "text-primary"},
                {value: "1 s", id: "slow", style: "text-info"},
            ]
        },
        selectedSize: 0,
        handler: undefined
    };

    function draw() {
        $("#responseTimes").html(segmentHtml);
        vue = new Vue({
            el: '#responseTimes',
            data: config.data,
            methods: {
                operateQueryResponseTime: function (queryValue, event) {
                    if ($(event.target).is(".operation-selected")) {
                        $(event.target).removeClass("operation-selected");
                        config.handler(queryValue, true);
                        config.selectedSize--;
                    } else {
                        if (config.selectedSize == 0) {
                            $(event.target).addClass("operation-selected");
                            config.handler(queryValue, false);
                            config.selectedSize++;
                        }
                    }
                }
            }
        });

        return this;
    }

    function registryResponseTimeHandler(handler) {
        config.handler = handler;
    }


    return {
        draw: draw,
        registryResponseTimeHandler: registryResponseTimeHandler
    }
});
