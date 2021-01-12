# Why can't I see any data in the UI?

There are three main reasons no data can be shown by the UI:

1. No traces have been sent to the collector.
2. Traces have been sent, but the timezone of your containers is incorrect.
3. Traces are in the collector, but you're not watching the correct timeframe in the UI.

## No traces

Be sure to check the logs of your agents to see if they are connected to the collector and traces are being sent.


## Incorrect timezone in containers

Be sure to check the time in your containers.


## The UI isn't showing any data

Be sure to configure the timeframe shown by the UI.
