define(['jquery', 'vue', 'text!applicationListHtml'], function($, Vue, segmentHtml) {
  var apps = {
    applist: []
  };
  var vue;

  function draw(data) {
    $("#applications").html(segmentHtml);
    vue = new Vue({
      el: '#applicationUl',
      data: apps
    });
  }

  function loadApplications() {
    $.getJSON("/testData/instances/applicationList.json", function(data) {
      apps.applist = data;
      draw();
    });
  }

  return {
    loadApplications: loadApplications,
    apps: apps
  }
});
