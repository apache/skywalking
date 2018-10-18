const config = {
  "entry": "src/index.js",
  "extraBabelPlugins": [
    "transform-decorators-legacy",
    ["import", { "libraryName": "antd", "libraryDirectory": "es", "style": true }]
  ],
  "env": {
    "development": {
      "extraBabelPlugins": [
        "dva-hmr"
      ]
    }
  },
  "ignoreMomentLocale": true,
  "theme": "./src/theme.js",
  "html": {
    "template": "./src/index.ejs"
  },
  "publicPath": "/",
  "disableDynamicImport": false,
  "hash": true
}

if (process.env.NO_MOCK) {
  config.proxy = {
    "/api":{
      target: "http://localhost:12800",
      changeOrigin: true,
      pathRewrite:  (path) => {
        console.log(path);
        return "/graphql"
      }
    }
  };
}

export default config;
