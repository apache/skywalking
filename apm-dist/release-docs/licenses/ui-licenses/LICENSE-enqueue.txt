
# enqueue

  seamlessly queue up asynchronous function calls. supports concurrency and timeouts.

## Installation

Node:

```bash
$ npm install enqueue
```

Browser (with [Duo](https://github.com/duojs/duo)):

```js
var enqueue = require('matthewmueller/enqueue');
```

## Example

```js
var superagent = require('superagent');
var enqueue = require('enqueue');

// execute 2 at a time, with a timeout
var options = {
  concurrency: 2,
  timeout: 1000,
  limit: 10
};

var fn = enqueue(function(url, done) {
  superagent.get(url, done);
}, options);

fn('http://lapwinglabs.com', function(err, res) { /* ... */ })
fn('http://gittask.com', function(err, res) { /* ... */ })

// delayed until one of the other two come back
fn('http://mat.io', function(err, res) { /* ... */ })
```

## API

### `queue = enqueue(fn, [options])`

Create a queue wrapper for `fn`. `options` include:

- `concurrency` (default: `1`): specify how many jobs you'd like to run at once.
- `timeout` (default: `false`): specify how long a job stall run before it times out.
- `limit` (default: `Infinity`): limit how many jobs can be queued up at any given time.
`queue` will return an `Error` if the limit has been reached.

### `queue(args..., [end])`

Pass any number of `args...` into the queue with an optional `end` function.

## Test

```
npm install
make test
```

## License

(The MIT License)

Copyright (c) 2014 Matthew Mueller &lt;mattmuelle@gmail.com&gt;

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
'Software'), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
