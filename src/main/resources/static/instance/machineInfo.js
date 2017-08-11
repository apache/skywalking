define(['jquery', 'vue', 'text!machineInfoHtml'], function($, Vue, segmentHtml) {
  function draw(data) {
    $("#machineInfoDiv").html(segmentHtml);
    new Vue({
      el: "#machineInfoDiv",
      data: data
    });
  }

  function loadMachineInfo() {
    $.getJSON("/testData/instance/machineInfo.json", function(data) {
      draw(data);
    });
  }

  return {
    loadMachineInfo: loadMachineInfo
  }
});
