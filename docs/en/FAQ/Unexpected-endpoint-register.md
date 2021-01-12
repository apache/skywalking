# Local span and Exit span should not be register

Since 6.6.0, SkyWalking cancelled the local span and exit span register. If old java agent(before 6.6.0) is still running,
and do register to 6.6.0+ backend, you will face the following warning message.
```
class=RegisterServiceHandler, message = Unexpected endpoint register, endpoint isn't detected from server side.
```

This will not harm the backend or cause any issue. This is a reminder that, your agent or other client should follow the new protocol
requirements.

You could simply use `log4j2.xml` to filter this warning message out.