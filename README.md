Apache SkyWalking UI
===============

<img src="https://skywalkingtest.github.io/page-resources/3.0/skywalking.png" alt="Sky Walking logo" height="90px" align="right" />

The UI for [Apache SkyWalking](https://github.com/apache/incubator-skywalking).

[![Build Status][ci-img]][ci] 
[![Join the chat][gitter-img]][gitter]

## Contributing

See [CONTRIBUTING](./CONTRIBUTING.md).

## Development

The app was built with [dva framework](https://github.com/dvajs/dva).

### Getting codes

Fork, then clone the `incubator-skywalking-ui` repo and change directory into it.

```
git clone https://github.com/apache/incubator-skywalking-ui.git
cd incubator-skywalking-ui
```

Install dependencies via `npm`:

```
npm install
```

### Running the application

#### Mock mode

This mode is suitable for developing locally.

```
npm start
```

#### No-Mock mode 

This mode is suitable for e2e test with backend collector.

```
npm run start:no-mock
```

The default collector query address is `http://localhost:12800`. You can change this address by editing `.webpack.js` file.

#### Commands

| Command                 | Description                                                 |
| ----------------------- | ----------------------------------------------------------- |
| `npm start`             | Starts development server with hot reloading and mock.      |
| `npm run start:no-mock` | Starts development server to access collector               |
| `npm test`              | Runs all the tests                                          |
| `npm run lint`          | Lint the project (eslint, stylelint)                        |
| `npm run build`         | Runs production build. Outputs files to `/dist`.            |

## Build

Running build will output all the static files to the `./dist` folder:

```
npm install
npm run build
```

[ci-img]: https://travis-ci.org/apache/incubator-skywalking-ui.svg?branch=master
[ci]: https://travis-ci.org/apache/incubator-skywalking-ui
[gitter-img]: https://badges.gitter.im/openskywalking/Lobby.svg
[gitter]: https://gitter.im/openskywalking/Lobby
