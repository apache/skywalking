
# minstache

  Mini mustache template engine.

## Installation

    $ npm install minstache
    $ component install visionmedia/minstache

## minstache(1)

  The `minstache(1)` executable can compile a file to a valid
  stand-alone commonjs module for you, there's no need to have minstache
  as a dependency:

  hello.mustache:

```
Hello {{name}}! {{^authenticated}}<a href="/login">login</a>{{/authenticated}}
```

  convert it:

```
$ minstache < hello.mustache > hello.js
```

  hello.js:

```js
module.exports = function anonymous(obj) {

  function escape(html) {
    return String(html)
      .replace(/&/g, '&amp;')
      .replace(/"/g, '&quot;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  };

  function section(obj, prop, negate, str) {
    var val = obj[prop];
    if ('function' == typeof val) return val.call(obj, str);
    if (negate) val = !val;
    if (val) return str;
    return '';
  };

  return "Hello " + escape(obj.name) + "! " + section(obj, "authenticated", true, "<a href=\"/login\">login</a>") + "\n"
}
```

## API

### minstache(string, [obj])

  Compile and render the given mustache `string` with optional context `obj`.

### minstache.compile(string)

  Compile the mustache `string` to a stand-alone `Function` accepting a context `obj`.

## Divergence

  Partials are not supported, this lib is meant to be a small template engine solution for stand-alone [component](http://github.com/component) templates. If your template takes "partials" then pass other rendered strings to it. If you need a full-blown mustache solution Hogan.js is still great.

  Minstache uses `{{!name}}` for unescaped properties.

## License

  MIT
