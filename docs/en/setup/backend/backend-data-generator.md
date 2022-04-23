# Mock data generator for testing

In 9.1.0, SkyWalking adds a module to generate mock data for testing. You can use this module to generate
mock data that will be sent to the storage.

To start the data generator, execute the script `tools/data-generator/bin/start.sh`.

Note that SkyWalking doesn't release a Docker image for this module, but you can still build it yourselves
by running the commands:

```shell
# build a Docker image for local use
make docker.data-generator

# or push to your registry
export HUB=<your-registry>
make push.docker.data-generator
```

Currently the module can generate two kinds of SkyWalking data, segments and logs. For each type,
there are some generators that can be used to fill the fields.

## Generate mock data

To generate mock data, `POST` a request to URL path `/mock-data/segments/tasks` (segments) or
`/mock-data/logs/tasks` (logs) with a generator template:

```shell
curl -XPOST 'http://localhost:12800/mock-data/segments/tasks?size=20' -H'Content-Type: application/json' -d "@segment-template.json"

curl -XPOST 'http://localhost:12800/mock-data/logs/tasks?size=20' -H'Content-Type: application/json' -d "@logs-template.json"
```

There are two possible types of task to generate mock data, `size` and `qps`:

- `size` (`/mock-data/segments/tasks?size=20`): the task will generate total number of `size` segments/logs and then finish.
- `qps` (`/mock-data/segments/tasks?qps=20`): the task will generate `qps` segments/logs per second continuously, until the task is [cancelled](#cancel-a-task).

Refer to [the segment template](segment-template.json), [the log template](logs-template.json) and the [Generators](#generators) for more details
about how to compose a template.

## Cancel a task

When the task is acknowledged by the server it will return a task id that can be used to cancelled
the task by sending a `DELETE` request to URL path `/mock-data/logs/tasks` with a parameter `requestId` (i.e.
`/mock-data/logs/tasks?requestId={request id returned in previous request}`):

```shell
curl -XDELETE 'http://localhost:12800/mock-data/segments/task?requestId=70d8a39e-b51e-49de-a6fc-43abf80482c1'
curl -XDELETE 'http://localhost:12800/mock-data/logs/task?requestId=70d8a39e-b51e-49de-a6fc-43abf80482c1'
```

## Cancel all tasks

When needed, you can also send a `DELETE` request to path `/mock-data/segments/tasks` to cancel all segment tasks.

```shell
curl -XDELETE 'http://localhost:12800/mock-data/segments/tasks
curl -XDELETE 'http://localhost:12800/mock-data/logs/tasks
```

## Generators

### `uuid`

`uuid` generator leverages `java.util.UUID` to generate a string. You can use `uuid` generator to fill the
`traceId` field of segments.

`changingFrequency` property can be used when you want to reuse a `uuid` for multiple times, for example,
if you want a `traceId` to be reused by 5 segments, then setting `changingFrequency` to `5` would do the trick.
By setting `changingFrequency` to `5`, `uuid` generates 1 string, and uses it for 5 times, then re-generates
a new uuid string and uses it for another 5 times.

```json
"traceId": {
    "type": "uuid",
    "changingFrequency": "5"
}
```

### `randomString` (`String`)

#### `length` (`int`)

`length` specifies the length of the random string to be generated,
i.e. `generatedString.length() == length` is always `true`.

#### `prefix` (`String`)

`prefix` is always added to the random strings **after** they are generated, that means:

- `generatedString.startsWith(prefix)` is always `true`, and,
- `generatedString.length() == length + prefix.length()` is always true.

#### `letters` (`boolean`)

Specifies whether the random string contains letters (i.e. `a-zA-Z`).

#### `numbers` (`boolean`)

Specifies whether the random string contains numbers (i.e. `0-9`).

#### `domainSize` (`int`)

When generating random strings, you might just want some random strings and use them over and over again randomly,
by setting `domainSize`, the generator generates `domainSize` random strings, and pick them randomly every time
you need a string.

### `randomBool` (`boolean`)

This generator generates a `Boolean` value, `true` or `false` with a default possibility of 50%, while you can change the `possibility` below.

#### `possibility` (`double`, `[0, 1]`)

`possibility` is a `double` value `>= 0` and `<= 1`, it's `0.5` by default, meaning **about** half of the generated values are `true`.

To always return a fixed boolean value `true`, you can just set the `possibility` to `1`, to always return a fixed boolean value `false`, you can set the `possibility` to `0`

```json
"error": {
    "type": "randomBool",
    "possibility": "0.9"
}
```

> 90 percent of the generated values are `true`.

### `randomInt` (`long`)

#### `min` (`long`)

The minimum value of the random integers, meaning all generated values satisfy `generatedInt >= min`.

#### `max` (`long`)

The maximum value of the random integers, meaning all generated values satisfy `generatedInt < min`.

#### `domainSize` (`int`)

This is similar to [`randomString`'s `domainSize`](#domainsize-int).

### `randomList` (`list` / `array`)

#### `size` (`int`)

The list size of the generated list, i.e. `generatedList.size() == size`.

#### `item` (`object`)

`item` is a template that will be use as a prototype to generate the list items, for example when generating a
list of `Tag`, the `item` should be the prototype of `Tag`, which can be composed by the generators again.

```json
"tags": {
    "type": "randomList",
    "size": 5,
    "item": {
        "key": {
            "type": "randomString",
            "length": "10",
            "prefix": "test_tag_",
            "letters": true,
            "numbers": true,
            "domainSize": 10
        },
        "value": {
            "type": "randomString",
            "length": "10",
            "prefix": "test_value_",
            "letters": true,
            "numbers": true
        }
    }
}
```

### `fixedString` (`string`)

This generator always returns a fixed `value` of string.

### `sequence` (`long`)

`sequence` generator generates a sequence of monotonically increasing integers, with a configurable `fluctuation`.

#### `min` (`long`)

The minimum value of the sequence.

#### `max` (`long`)

The maximum value of the sequence.

#### `step` (`long`)

The increasing step of this sequence, i.e. `the next generated value == the previous value + step`.

#### `domainSize` (`int`)

This is similar to [`randomString`'s `domainSize`](#domainsize-int).

#### `fluctuation` (`int`)

By default, sequence is strictly increasing numbers, but in some cases you might want the numbers to fluctuate
slightly while they are increasing. Adding property `fluctuation` to the generator will add a random number
`>= -fluctuation, <= fluctuation` to the sequence elements.

For example, `min = 10, max = 15, step = 1` generates a sequence `[10, 11, 12, 13, 14, 15]`, but adding `fluctuation = 2` **might** generate a sequence `[10, 12, 11, 14, 13, 15]`.
