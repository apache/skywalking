- Update log4j2.xml configurations in both apm-webapp and dist-material.

- Add marker logging in ApplicationStartUp.java within apm-webapp. 

- Change the GRPCServer.java logging in the oap-server/server-library/library-server to marker logging.

- Fix console starts oapService.sh and webappService.sh scripts the startup message is undisplayed. 

- Fix startup.sh starts oapService.sh and webappService.sh on the back-end, the startup message is displayed on the console.