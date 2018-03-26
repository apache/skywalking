# G

[![](https://img.shields.io/travis/antvis/g.svg)](https://travis-ci.org/antvis/g)
![](https://img.shields.io/badge/language-javascript-red.svg)
![](https://img.shields.io/badge/license-MIT-000000.svg)

[![npm package](https://img.shields.io/npm/v/@antv/g.svg)](https://www.npmjs.com/package/@antv/g)
[![NPM downloads](http://img.shields.io/npm/dm/@antv/g.svg)](https://npmjs.org/package/@antv/g)
[![Percentage of issues still open](http://isitmaintained.com/badge/open/antvis/g.svg)](http://isitmaintained.com/project/antvis/g "Percentage of issues still open")

A canvas library which providing 2d draw for G2.

## Install

`npm i @antv/g`

## Development

```shell
$ git clone git@github.com:antvis/g.git
$ npm install
$ npm run dev
```

## Use

```js
var canvas = new Canvas({
  containerId: 'c1',
  width: 500,
  height: 600
});

var group = canvas.addGroup();
var shape = group.addShape('circle', {
  attrs: {
    x: 100,
    y: 100,
    r: 50,
    fill: 'red'
  }
});

canvas.draw();

shape.attr({
  x: 200,
  y: 200
});
canvas.draw();

```

## API

G 作为 G2、G6 的绘图引擎，主要包括的类：

![结构图](https://gw.alipayobjects.com/zos/rmsportal/tMWOikrawKGtvEazPucH.png)

* Canvas 绘图引擎的入口
* Group 图形分组，可以包含图形和分组
* Shape 图形属性
* Element Group 和 Shape 的基类

### Canvas

#### 属性

* width 画布宽度
* height 画布高度
* containerId 容器 id
* pixelRatio 画布大小和所占 DOM 宽高的比例，一般可以使用 window.devicePixelRatio
* children 所有的子元素（分组或者图形）只读属性

```js
var canvas = new Canvas({
  containerId: 'c1',
  width: 500,
  height: 600
});
```

#### 方法

* get(name) 获取属性
* set(name, value) 设置属性
* draw 绘制方法
* addShape(type, cfg) 添加图形，支持的图形类型见 [Shape](#Shape)

  ```js
    canvas.addShape('circle', {
      zIndex: 5,
      attrs: {
        x: 10,
        y: 10,
        r: 50
      }
    });
  ```

* addGroup([GroupClass], cfg) 添加分组

  ```js
   var group = canvas.addGroup(); // 添加分组
   var group1 = canvas.addGroup(AGroup, {
    // 属性
   });
  ```

* getPointByClient(clientX, clientY) 根据窗口的位置获取画布上的位置信息
* changeSize(w,h) 改变大小
* sort() 对内部图形元素进行排序，根据图形元素的 zIndex 进行排序
* clear() 清空画布
* destroy() 销毁
* on(eventType, callback) 绑定事件，支持浏览器的常见事件：click, dblclick, mouseenter, mouseleave, mouseover, mouseup, mousedown, touchstart, touchend
* off(eventType, callback) 解除绑定

### Group

图形分组可以嵌套图形和分组

#### 属性

* zIndex 层次索引值，决定分组在父容器中的位置
* visible 是否可见
* children 嵌套的图形元素，只读

#### 方法

* get(name) 获取属性
* set(name, value) 设置属性
* setSilent(name, value) 由于 set 方法进行一些检测，会执行一些方法，所以可以使用这个方法提升函数性能
* addShape(type, cfg) 添加图形， 支持的图形类型见 [Shape](#Shape)

  ```js
    group.addShape('circle', {
      zIndex: 5,
      attrs: {
        x: 10,
        y: 10,
        r: 50
      }
    });
  ```

* addGroup([GroupClass], cfg) 添加分组

  ```js
   var g1 = group.addGroup(); // 添加分组
   var g2 = group.addGroup(AGroup, {
    // 属性
   });
  ```

* getBBox() 获取包含的所有图形的包围盒
* show() 显示
* hide() 隐藏
* remove() 从父容器中移除当前分组
* sort() 对内部图形元素进行排序，根据图形元素的 zIndex 进行排序
* clear() 清空画布
* destroy() 销毁

### Shape

支持的所有图形的基类，支持的所有通用的属性和方法

#### 属性

* zIndex 层次索引值，决定分组在父容器中的位置
* visible 是否可见
* attrs 图形属性，通用的图形属性如下：
  + transform 进行几何变换的矩阵
  + 通用的图形属性，见[绘图属性](https://antv.alipay.com/zh-cn/g2/3.x/api/graphic.html)

#### 方法

* attr('name', [value]) 设置、获取图形属性

  ```js
    circle.attr({ // 同时设置多个属性
      x: 100,
      y: 100,
      fill: '#fff'
    });
    circle.attr('fill', 'red'); // 设置单个属性
    circle.attr('fill'); // 获取属性
  ```

* animate(attrs, duration, easing, callback, delay = 0) 执行动画
  + attrs 所有的图形属性
  + duration 执行的时间 ms
  + easing 参加 [d3-ease](https://github.com/d3/d3-ease)
  + callback 执行完毕后的回调函数
  + delay 延迟执行

* getBBox() 获取图形的包围盒
* show() 显示
* hide() 隐藏
* remove() 从父容器中移除当前图形
* destroy() 销毁

### Shape.Circle

圆，一般在添加图形时使用 'circle' 来标识, type = 'circle'

#### 图形属性

* 通用的图形属性见：[绘图属性](https://antv.alipay.com/zh-cn/g2/3.x/api/graphic.html)
* x 圆心坐标的x坐标
* y 圆心坐标的y坐标
* r 圆的半径

```js
  canvas.addShape('circle', {
    attrs: {
      x: 150,
      y: 150,
      r: 70,
      stroke: 'black'
    }
  });
  canvas.addShape('circle', {
    attrs: {
      x: 100,
      y: 100,
      r: 60,
      lineDash: [20, 20],
      stroke: 'red'
    }
  });
  canvas.addShape('circle', {
    attrs: {
      x: 100,
      y: 100,
      r: 100,
      fill: 'rgba(129,9,39,0.5)',
      stroke: 'blue'
    }
  });
  canvas.draw();
```

### Shape.Rect

绘制矩形，type = 'rect'

#### 图形属性

* 通用的图形属性见：[绘图属性](https://antv.alipay.com/zh-cn/g2/3.x/api/graphic.html)
* x 起始点 x 坐标
* y 起始点 y 坐标
* width 宽度
* height 高度
* radius 圆角

```js
canvas.addShape('rect', {
    attrs: {
      x: 150,
      y: 150,
      width: 150,
      height: 150,
      stroke: 'black',
      radius: 2
    }
  });
  canvas.addShape('rect', {
    attrs: {
      x: 150-50,
      y: 150-50,
      width: 150,
      height: 150,
      stroke: 'red'
    }
  });
  canvas.addShape('rect', {
    attrs: {
      x: 150+50,
      y: 150+50,
      width: 150,
      height: 150,
      fill: 'rgba(129,9,39,0.5)',
      stroke: 'blue'
    }
  });
```

### Shape.Path

绘制的路径 ,使用 'path' 来标识, type = 'path'

#### 图形属性

* 通用的图形属性见：[绘图属性](https://antv.alipay.com/zh-cn/g2/3.x/api/graphic.html)
* path：路径，支持 字符串或者数组两种方式，详情参考 [svg path](https://developer.mozilla.org/zh-CN/docs/Web/SVG/Tutorial/Paths)
* arrow 是否显示箭头 ture / false

  ```js
    const path = group.addShape('path', {
      attrs: {
        path: 'M100,600' +
              'l 50,-25' +
              'a25,25 -30 0,1 50,-25' +
              'l 50,-25' +
              'a25,50 -30 0,1 50,-25' +
              'l 50,-25' +
              'a25,75 -30 0,1 50,-25' +
              'l 50,-25' +
              'a25,100 -30 0,1 50,-25' +
              'l 50,-25' +
              'l 0, 200,' +
              'z',
        lineWidth: 10,
        lineJoin: 'round',
        stroke: '#54BECC'
      }
    });
    const path1 = group.addShape('path', {
      attrs: {
        path: [['M', 100, 100], ['L', 200, 200]],
        stroke: 'red',
        lineWidth: 2
      }
    });
  ```

### Shape.Line

绘制直线, type = 'line'，可以使用 path 来代替，

#### 图形属性

* 通用的图形属性见：[绘图属性](https://antv.alipay.com/zh-cn/g2/3.x/api/graphic.html)
* x1 起始点的 x 坐标
* y1 起始点的 y 坐标
* x2 结束点的 x 坐标
* y2 结束点的 y 坐标
* arrow 是否显示箭头 ture / false

```js
  canvas.addShape('line', {
    attrs: {
      x1: 20,
      y1: 20,
      x2: 280,
      y2: 280,
      stroke: 'red'                       // 颜色名字
    }
  });
  canvas.addShape('line', {
    attrs: {
      x1: 20,
      y1: 300 + 20,
      x2: 280,
      y2: 300 + 280,
      arrow: true,                                             // 显示箭头
      stroke: '#00ff00'                   // 6位十六进制
    }
  });
  canvas.addShape('line', {
    attrs: {
      x2: 300 + 20,
      y2: 300 + 20,
      x1: 300 + 280,
      y1: 300 + 280,
      arrow: true,                                             // 显示箭头
      stroke: '#00f'                      // 3位十六进制
    }
  });
  canvas.addShape('line', {
    attrs: {
      x1: 20,
      y1: 600 + 20,
      x2: 280,
      y2: 600 + 280,
      lineDash: [10,10],
      stroke: 'rgb(100, 100, 200)'         // rgb数字模式
    }
  });
```

### Shape.Polyline

多个点的折线，type = 'polyline'

#### 图形属性

* 通用的图形属性见：[绘图属性](https://antv.alipay.com/zh-cn/g2/3.x/api/graphic.html)
* points 包含的点集合

```js
 canvas.addShape('polyline', {
    attrs: {
      points: [[741.6487813219777,1183.92131359719],[583.1709046233477,33.93352498571885],[1098.3455104904451,246.13363066051957],[211.30437444177633,420.3306748934016],[980.3732054245157,756.2252762234709],[374.28495603818607,108.15975006182006],[422.7940564389682,1119.2144105552136],[833.5078092462321,586.7198136138784]],
      stroke: 'red'
    }
});

```

### Shape.Image

绘制图片，type = 'image'

#### 图形属性

* x 起始点 x 坐标
* y 起始点 y 坐标
* width 宽度
* height 高度
* img 图片的路径、canvas 对象、图片对象

```js
  canvas.addShape('image', {
    attrs: {
      x: 150+200,
      y: 150,
      img: 'https://zos.alipayobjects.com/rmsportal/FDWrsEmamcNvtEf.png'
    }
  });
  canvas.addShape('image', {
    attrs: {
      x: 150-50,
      y: 150-50,
      img: 'https://zos.alipayobjects.com/rmsportal/nAVchPnSaAWncPj.png'
    }
  });
  canvas.addShape('image', {
    attrs: {
      x: 150+50,
      y: 150+150,
      img: 'https://zos.alipayobjects.com/rmsportal/GHGrgIDTVMCaYZT.png'
    }
  });

```

### Shape.Text

文本，type = 'text'

#### 图形属性

* 通用的图形属性见：[绘图属性](https://antv.alipay.com/zh-cn/g2/3.x/api/graphic.html)
* x 文字的位置坐标 x
* y 文字的位置坐标 y
* font 设置文本内容的当前字体属性,这个属性可以分解成多个属性单独配置：
  + fontStyle 对应 font-style；
  + fontVariant 对应 font-variant；
  + fontWeight 对应 font-weight；
  + fontSize 对应 font-size；
  + fontFamily 对应 font-family；
* textAlign 设置文本内容的当前对齐方式, 支持的属性：center|end|left|right|start；
* textBaseline 设置在绘制文本时使用的当前文本基线, 支持的属性:top|middle|bottom。

`注意`：文本的颜色一般使用 fill 属性，而非 'stroke' 属性

```js
  canvas.addShape('text', {
    attrs: {
      x: 150,
      y: 150,
      fontFamily: 'PingFang SC',
      text: '文本文本',
      fontSize: 90,
      stroke: 'black'
    }
  });
  canvas.addShape('text', {
    attrs: {
      x: 150+100,
      y: 250,
      fontFamily: 'PingFang SC',
      fontSize: 90,
      text: '字体',
      lineDash: [10, 10],
      stroke: 'red'
    }
  });
  canvas.addShape('text', {
    attrs: {
      x: 150+50,
      y: 150+150,
      text: '对齐方式',
      fontFamily: 'Hiragino Sans GB',
      fontSize: 100,
      textAlign: 'center',
      textBaseline: 'top',
      fill: 'rgba(129,9,39,0.5)',
      stroke: 'blue'
    }
  });
```

## 更多

G 还提供了几个特殊的 Shape 

* marker 绘制小的几何 icon
* fan 绘制圆饼
* arc 圆弧
* ellipse 椭圆
* cubic 三阶贝塞尔曲线
* quadratic 二阶贝塞尔曲线

这些图形都可以使用 path 代替，所以在这里不详细介绍了





