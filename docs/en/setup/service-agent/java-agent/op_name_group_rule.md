# Operation Name Group Rule
Operation Name in auto instrumentation agent is unpredictable, some time, target application carries parameter in it, due to the parameter included in URI mostly.
Those operation name are also as known endpoint name in most cases.
Such as /api/checkTicket/tk/{userToken}.

We solved most of these cases, by leverage the parameter pattern path in framework, such as SpringMVC, Webflux, etc. 
But it is undetected in RPC client side, such as HTTP restful client.
In this case, we have to ask the users to set the group rule manually.

All rules are supported to set through agent.config, system properties and system env, like other agent settings.
- Config format, `plugin.opgroup.`plugin name`.rule[`rule name`]`=pattern expression
Example, `plugin.opgroup.resttemplate.rule[/user/auth/{token}]=/user/auth/*`

We have following plugins supporting operation name group.
