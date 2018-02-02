const config = {
  "entry": "src/index.js",
  "extraBabelPlugins": [
    "transform-runtime",
    "transform-decorators-legacy",
    "transform-class-properties",
    ["import", { "libraryName": "antd", "libraryDirectory": "es", "style": true }]
  ],
  "env": {
    "development": {
      "extraBabelPlugins": [
        "dva-hmr"
      ]
    }
  },
  "externals": {
    "g2": "G2",
    "g-cloud": "Cloud",
    "g2-plugin-slider": "G2.Plugin.slider"
  },
  "ignoreMomentLocale": true,
  "theme": "./src/theme.js"
};

if (process.env.NO_PROXY) {
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
