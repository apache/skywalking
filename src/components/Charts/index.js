import numeral from 'numeral';
import './g2';
import ChartCard from './ChartCard';
import Area from './Area';
import Bar from './Bar';
import Pie from './Pie';
import Radar from './Radar';
import Gauge from './Gauge';
import Line from './Line';
import MiniArea from './MiniArea';
import MiniBar from './MiniBar';
import MiniProgress from './MiniProgress';
import Field from './Field';
import WaterWave from './WaterWave';
import TagCloud from './TagCloud';
import TimelineChart from './TimelineChart';
import StackBar from './StackBar';
import Sankey from './Sankey';

const yuan = val => `&yen; ${numeral(val).format('0,0')}`;

export {
  yuan,
  Bar,
  Pie,
  Gauge,
  Radar,
  MiniBar,
  MiniArea,
  MiniProgress,
  ChartCard,
  Field,
  WaterWave,
  TagCloud,
  TimelineChart,
  Line,
  Area,
  StackBar,
  Sankey,
};
