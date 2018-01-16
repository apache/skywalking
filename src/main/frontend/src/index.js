import dva from 'dva';
import 'ant-design-pro/dist/ant-design-pro.css';
import createLoading from 'dva-loading';
// import browserHistory from 'history/createBrowserHistory';
import './polyfill';
import './index.less';

// 1. Initialize
const app = dva({
  // history: browserHistory(),
});

// 2. Plugins
app.use(createLoading());

// 3. Register global model
app.model(require('./models/global'));

// 4. Router
app.router(require('./router'));

// 5. Start
app.start('#root');
