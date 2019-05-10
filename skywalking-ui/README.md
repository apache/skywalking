Apache SkyWalking UI
===============

<img src="http://skywalking.apache.org/assets/logo.svg" alt="Sky Walking logo" height="90px" align="right" />

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

This mode is suitable for developing locally. Use `username:admin, password:888888` to login system.

```
npm start
```

#### No-Mock mode 

This mode is suitable for e2e test with backend collector. No webapp proxy required.

```
npm run start:no-proxy
```

The default collector query address is `http://localhost:12800`. You can change this address by editing `.webpack.js` file. From 5.0.0-beta2, login auth is supported, but without webapp proxy, there is no one to take charge of authentication, so we need specific processes to login in this mode.

1. Start up in `Mock mode`.
1. Do login by `username:admin, password:888888`. (Now, browser saved authentication in local storage)
1. Stop and restart in `No-Mock mode`.
1. You could access without username/password and webapp proxy.

#### Commands

| Command                 | Description                                                 |
| ----------------------- | ----------------------------------------------------------- |
| `npm start`             | Starts development server with hot reloading and mock.      |
| `npm run start:no-proxy`| Starts development server to access collector               |
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
