/**
 * @author pengys5
 * @author ascrutae
 */
requirejs(['/main.js'], function () {
    require(["jquery", "vue", "head", "dagDraw", "timeAxis", "bootstrap"], function ($, Vue, head, dagDraw, timeAxis) {
        timeAxis.autoUpdate().load().registryTimeChangedHandler(function (timeBucketType, startTime, endTime) {
            console.log("time changed, start time: " + startTime + ", end time: " + endTime);
            dagDraw.startNetwork("dagViewDiv").load(timeBucketType, startTime, endTime);
        }).render("timeAxisDiv");
    });
})
