/**
 * @author pengys5
 * @author ascrutae
 */
requirejs(['/main.js'], function (main) {
    require(["jquery", "dagDraw", "timeAxis", "alarm"], function ($, dagDraw, timeAxis, alarm) {
        dagDraw.startNetwork("dagViewDiv");
        timeAxis.create("timeAxisDiv");
        alarm.create("alarmDiv");
    });
})
