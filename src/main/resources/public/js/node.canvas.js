function createNode(image, instNum) {
    var canvas = document.createElement('canvas');
    var context2D = canvas.getContext('2d');
    // var tmpImage = new Image();
    var base64Str = "";
    var nodeWidth = 60;
    var nodeHeight = 60;
    var imageSize = 40;
    var offset = 4;

    if (instNum > 9) {
        offset = offset * 2;
    }

    canvas.width = nodeWidth;
    canvas.height = nodeHeight;

    context2D.strokeRect(0, 0, nodeWidth, nodeHeight);

    var left = (nodeWidth - imageSize) / 2;
    var top = (nodeHeight - imageSize) / 2 + 5;


    var getPixelRatio = function (context) {
        var backingStore = context.backingStorePixelRatio ||
            context.webkitBackingStorePixelRatio ||
            context.mozBackingStorePixelRatio ||
            context.msBackingStorePixelRatio ||
            context.oBackingStorePixelRatio ||
            context.backingStorePixelRatio || 1;

        return (window.devicePixelRatio || 1) / backingStore;
    };
    var ratio = getPixelRatio(context2D);

    // tmpImage.src = imgSrc;
    context2D.lineJoin = "round";
    context2D.lineWidth = 8;

    // tmpImage.onload = function () {
        context2D.drawImage(image, left, top, imageSize * ratio, imageSize * ratio);
    // };
    context2D.font = "12px font-family: Arial, Helvetica, sans-serif;";
    context2D.fillStyle = "#ffffff";
    if (instNum > 0) {
        context2D.strokeRect(nodeWidth - 8 - offset, 6, offset + 4, 6);
        context2D.fillText(instNum, nodeWidth - 8 - offset, 13);
    }

    base64Str = canvas.toDataURL("image/png");
    return base64Str;
}