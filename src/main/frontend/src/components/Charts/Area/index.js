import React, { PureComponent } from 'react';
import G2 from 'g2';
import Debounce from 'lodash-decorators/debounce';
import Bind from 'lodash-decorators/bind';
import equal from '../equal';
import styles from '../index.less';

class Area extends PureComponent {
  static defaultProps = {
    limitColor: 'rgb(255, 144, 24)',
    color: 'rgb(24, 144, 255)',
  };
  componentDidMount() {
    this.renderChart(this.props.data);

    window.addEventListener('resize', this.resize);
  }

  componentWillReceiveProps(nextProps) {
    if (!equal(this.props, nextProps)) {
      const { data = [] } = nextProps;
      this.renderChart(data);
    }
  }

  componentWillUnmount() {
    window.removeEventListener('resize', this.resize);
    if (this.chart) {
      this.chart.destroy();
    }
    this.resize.cancel();
  }

  @Bind()
  @Debounce(200)
  resize() {
    if (!this.node) {
      return;
    }
    const { data = [] } = this.props;
    this.renderChart(data);
  }

  handleRef = (n) => {
    this.node = n;
  }

  renderChart(data) {
    const {
      height = 0,
      fit = true,
      margin = [32, 60, 32, 60],
      limitColor,
      color,
    } = this.props;

    if (!data || (data && data.length < 1)) {
      return;
    }

    // clean
    this.node.innerHTML = '';

    const chart = new G2.Chart({
      container: this.node,
      forceFit: fit,
      height: height - 22,
      plotCfg: {
        margin,
      },
      legend: false,
    });
    chart.legend(false);
    chart.axis('x', {
      title: false,
    });
    chart.axis('y', {
      title: false,
    });
    chart.tooltip({
      crosshairs: {
        type: 'line',
      },
      title: null,
      map: {
        name: 'x',
        value: 'y',
      },
    });
    const dataConfig = {
      x: {
        type: 'cat',
        tickCount: 3,
        range: [0, 1],
      },
      y: {
        min: 0,
      },
    };
    const view = chart.createView();
    const offset = Math.floor(data.length / 2);
    const xData = data.slice(0, offset);
    const yData = data.slice(offset).map((v, i) => ({ ...v, y: v.y - xData[i].y }));
    view.source(yData.concat(xData), dataConfig);
    view.areaStack().position('x*y').color('type', [limitColor, color]).shape('smooth')
      .style({ fillOpacity: 0.2 });
    view.tooltip(false);
    view.axis(false);
    const view2 = chart.createView();
    view2.source(data, dataConfig);
    view2.line().position('x*y').color('type', [color, limitColor]).shape('smooth')
      .size(2);
    chart.render();

    this.chart = chart;
  }

  render() {
    const { height, title } = this.props;

    return (
      <div className={styles.chart} style={{ height }}>
        <div>
          { title && <h4>{title}</h4>}
          <div ref={this.handleRef} />
        </div>
      </div>
    );
  }
}

export default Area;
