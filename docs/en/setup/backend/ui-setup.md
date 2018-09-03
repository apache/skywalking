# UI
SkyWalking UI distribution is already included in our Apache official release. 

## Startup
Startup script is also in `/bin/webappService.sh`(.bat). UI runs as an OS Java process, powered-by Zuul.

## Settings
Setting file of UI is  `webapp/webapp.yml` in distribution package. It is constituted by three parts.

1. Listening port.
1. Backend connect info.
1. Auth setting.

