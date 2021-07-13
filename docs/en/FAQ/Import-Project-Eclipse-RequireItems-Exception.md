### Problem
- When importing the SkyWalking project to Eclipse, the following errors may occur:
> Software being installed: Checkstyle configuration plugin for
> M2Eclipse 1.0.0.201705301746
> (com.basistech.m2e.code.quality.checkstyle.feature.feature.group 
> 1.0.0.201705301746) Missing requirement: Checkstyle configuration plugin for M2Eclipse 1.0.0.201705301746
> (com.basistech.m2e.code.quality.checkstyle.feature.feature.group 
> 1.0.0.201705301746) requires 'net.sf.eclipsecs.core 5.2.0' but it could not be found

### Reason
The Eclipse Checkstyle Plug-in has not been installed.

### Resolution
Download the plug-in at the link here: https://sourceforge.net/projects/eclipse-cs/?source=typ_redirect 
Eclipse Checkstyle Plug-in version 8.7.0.201801131309 is required.
Plug-in notification:
The Eclipse Checkstyle plug-in integrates the Checkstyle Java code auditor into the Eclipse IDE. The plug-in provides real-time feedback to the user on rule violations, including checking against coding style and error-prone code constructs.
