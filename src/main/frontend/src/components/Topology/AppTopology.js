import styles from './index.less';
import Base from './Base';

export default class AppTopology extends Base {
  getStyle = () => {
    return [
      {
        selector: 'node[calls]',
        style: {
          width: 120,
          height: 120,
          'text-valign': 'bottom',
          'text-halign': 'center',
          'background-color': '#fff',
          'border-width': 3,
          'border-color': ele => (ele.data('isAlarm') ? 'red' : 'rgb(99, 160, 167)'),
          'font-family': 'Microsoft YaHei',
          label: 'data(name)',
        },
      },
      {
        selector: 'node[!calls]',
        style: {
          width: 60,
          height: 60,
          'text-valign': 'bottom',
          'text-halign': 'center',
          'background-color': '#fff',
          'background-image': ele => `img/node/${ele.data('type') ? ele.data('type') : 'UNDEFINED'}.png`,
          'background-width': '60%',
          'background-height': '60%',
          'border-width': 1,
          'font-family': 'Microsoft YaHei',
          label: 'data(name)',
        },
      },
      {
        selector: 'edge',
        style: {
          'curve-style': 'bezier',
          'control-point-step-size': 100,
          'target-arrow-shape': 'triangle',
          'target-arrow-color': ele => (ele.data('isAlarm') ? 'red' : 'rgb(147, 198, 174)'),
          'line-color': ele => (ele.data('isAlarm') ? 'red' : 'rgb(147, 198, 174)'),
          width: 2,
          label: ele => `${ele.data('callType')} \n ${ele.data('callsPerSec')} tps / ${ele.data('responseTimePerSec')} ms`,
          'text-wrap': 'wrap',
          color: 'rgb(110, 112, 116)',
          'text-rotation': 'autorotate',
        },
      },
    ];
  }
  getNodeLabel = () => {
    return [
      {
        query: 'node[calls]',
        halign: 'center',
        valign: 'center',
        halignBox: 'center',
        valignBox: 'center',
        cssClass: `${styles.node}`,
        tpl(data) {
          return `
          <div class="${styles.circle}">
            <div class="node-percentage">${data.sla}%</div>
            <div>${data.calls} calls/s</div>
            <div>
              <img src="data.png" class="${styles.logo}"/>${data.numOfServer}
              <img src="alert.png" class="${styles.logo}"/>
              <span class="${styles.alert}">${data.numOfServerAlarm}</span>
            </div>
            <div>${data.apdex} Apdex</div>
          </div>`;
        },
      },
    ];
  }
}
