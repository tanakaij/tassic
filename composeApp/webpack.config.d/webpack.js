// Tassic webpack overrides.
// 1. Relative publicPath so the bundle works when hosted from a GitHub Pages
//    project sub-path (https://<owner>.github.io/Tassic/).
// 2. Node core fallbacks required by sql.js-style dependencies.
config.output.publicPath = './';

config.resolve = config.resolve || {};
config.resolve.fallback = Object.assign({}, config.resolve.fallback || {}, {
    fs: false,
    path: false,
    crypto: false,
});
