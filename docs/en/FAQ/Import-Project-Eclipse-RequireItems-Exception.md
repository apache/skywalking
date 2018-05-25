### Problem
- import skywalking project to eclipse,below error occurs:
- Cannot complete the install because one or more required items could not be found.
  Software being installed: Checkstyle configuration plugin for M2Eclipse 1.0.0.201705301746 (com.basistech.m2e.code.quality.checkstyle.feature.feature.group 
  1.0.0.201705301746)
  Missing requirement: Checkstyle configuration plugin for M2Eclipse 1.0.0.201705301746 (com.basistech.m2e.code.quality.checkstyle.feature.feature.group  
  1.0.0.201705301746) requires 'net.sf.eclipsecs.core 5.2.0' but it could not be found

### reason
uninstall Eclipse Checkstyle Plug-in

### Resolve
download the plugin by the link:https://sourceforge.net/projects/eclipse-cs/?source=typ_redirect，Eclipse Checkstyle Plug-in version:8.7.0.201801131309 slove the problem。
plugin notification:
The Eclipse Checkstyle plug-in integrates the Checkstyle Java code auditor into the Eclipse IDE. The plug-in provides real-time feedback to the user about 
violations of rules that check for coding style and possible error prone code constructs.