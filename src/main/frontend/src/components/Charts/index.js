import numeral from 'numeral';
import ChartCard from './ChartCard';
import Area from './Area';
import Bar from './Bar';
import Line from './Line';
import Pie from './Pie';
import Radar from './Radar';
import Gauge from './Gauge';
import MiniArea from './MiniArea';
import MiniBar from './MiniBar';
import MiniProgress from './MiniProgress';
import Field from './Field';
import WaterWave from './WaterWave';
import TagCloud from './TagCloud';
import TimelineChart from './TimelineChart';
import StackBar from './StackBar';

const yuan = val => `&yen; ${numeral(val).format('0,0')}`;

export default {
  yuan,
  Area,
  Bar,
  Line,
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
  StackBar,
};
