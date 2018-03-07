import numeral from 'numeral';
import './g2';
import ChartCard from './ChartCard';
import Area from './Area';
import Bar from './Bar';
import Pie from './Pie';
import Line from './Line';
import MiniArea from './MiniArea';
import MiniBar from './MiniBar';
import Field from './Field';
import StackBar from './StackBar';
import Sankey from './Sankey';

const yuan = val => `&yen; ${numeral(val).format('0,0')}`;

export {
  yuan,
  Bar,
  Pie,
  Field,
  MiniBar,
  MiniArea,
  ChartCard,
  Line,
  Area,
  StackBar,
  Sankey,
};
