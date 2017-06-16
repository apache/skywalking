define(function () {
    function createNode(imageObj, real, instNum) {
        console.log(imageObj);
        console.log(real);
        console.log(instNum);
        if (real) {
            return createRealNode(imageObj, instNum);
        } else {
            return createNotRealNode(imageObj);
        }
    }

    function createRealNode(imageObj, instNum) {
        var canvas = document.createElement('canvas');
        var context2D = canvas.getContext('2d');
        var base64Str = "";
        var nodeWidth = 60;
        var nodeHeight = 60;
        var imageSize = 30;
        var offset = 4;

        if (instNum > 9) {
            offset = offset * 2;
        }

        canvas.width = nodeWidth;
        canvas.height = nodeHeight;

        context2D.beginPath();
        context2D.arc(nodeWidth / 2, nodeWidth / 2, nodeWidth / 2.4, 0, 360, false);
        context2D.lineWidth = 3;
        context2D.strokeStyle = "#1a7bb9";
        context2D.stroke();//画空心圆
        context2D.closePath();

        var left = (nodeWidth - imageSize) / 2;
        var top = (nodeHeight - imageSize) / 2;

        context2D.lineJoin = "round";
        context2D.lineWidth = 8;

        context2D.drawImage(imageObj, left, top, imageSize, imageSize);
        context2D.font = "12px font-family: Arial, Helvetica, sans-serif;";
        context2D.fillStyle = "#ffffff";
        if (instNum > 0) {
            context2D.strokeRect(nodeWidth - 8 - offset, 6, offset + 4, 6);
            context2D.fillText(instNum, nodeWidth - 8 - offset, 13);
        }

        return canvas.toDataURL("image/png");
    }

    function createNotRealNode(imageObj) {
        var canvas = document.createElement('canvas');
        var context2D = canvas.getContext('2d');
        var nodeWidth = 60;
        var nodeHeight = 60;
        var imageSize = 40;

        canvas.width = nodeWidth;
        canvas.height = nodeHeight;

        var left = (nodeWidth - imageSize) / 2;
        var top = (nodeHeight - imageSize) / 2 + 5;

        context2D.lineJoin = "round";
        context2D.lineWidth = 8;

        context2D.drawImage(imageObj, left, top, imageSize, imageSize);
        context2D.font = "12px font-family: Arial, Helvetica, sans-serif;";
        context2D.fillStyle = "#ffffff";

        return canvas.toDataURL("image/png");;
    }

    return {
        createNode: createNode
    }
});

