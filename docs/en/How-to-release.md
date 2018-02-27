Apache SkyWalking release guide
--------------------

Ref: 
* http://tiles.apache.org/framework/dev/release.html
* http://www.apache.org/dev/publishing-maven-artifacts.html#dev-env

Important things list:
* Maven releases, and the nexus staging repository, and download artifacts upload
* All artifacts are published with .asc, .md5, *sha1 files
* The *.asc files are signed with a gpg key that's part of apache's ring of trust
* Sync'ing this to releases and tags in github
* Making the internal announcements
* Calling a vote
* Moving the staging repository to a public repository, making download artifacts public
* Making the final public announcement