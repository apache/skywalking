### Problem： Maven compilation failure with error like `Error: not found: python2`
When you compile the project via maven, it failed at module `apm-webapp` and the following error occured.

Pay attention to key words such as `node-sass` and `Error: not found: python2`.

```
[INFO] > node-sass@4.11.0 postinstall C:\XXX\skywalking\skywalking-ui\node_modules\node-sass
[INFO] > node scripts/build.js

[ERROR] gyp verb check python checking for Python executable "python2" in the PATH
[ERROR] gyp verb `which` failed Error: not found: python2
[ERROR] gyp verb `which` failed     at getNotFoundError (C:\XXX\skywalking\skywalking-ui\node_modules\which\which.js:13:12)
[ERROR] gyp verb `which` failed     at F (C:\XXX\skywalking\skywalking-ui\node_modules\which\which.js:68:19)
[ERROR] gyp verb `which` failed     at E (C:\XXX\skywalking\skywalking-ui\node_modules\which\which.js:80:29)
[ERROR] gyp verb `which` failed     at C:\XXX\skywalking\skywalking-ui\node_modules\which\which.js:89:16
[ERROR] gyp verb `which` failed     at C:\XXX\skywalking\skywalking-ui\node_modules\isexe\index.js:42:5
[ERROR] gyp verb `which` failed     at C:\XXX\skywalking\skywalking-ui\node_modules\isexe\windows.js:36:5
[ERROR] gyp verb `which` failed     at FSReqWrap.oncomplete (fs.js:152:21)

[ERROR] gyp verb `which` failed   code: 'ENOENT' }
[ERROR] gyp verb check python checking for Python executable "python" in the PATH
[ERROR] gyp verb `which` succeeded python C:\Users\XXX\AppData\Local\Programs\Python\Python37\python.EXE
[ERROR] gyp ERR! configure error 
[ERROR] gyp ERR! stack Error: Command failed: C:\Users\XXX\AppData\Local\Programs\Python\Python37\python.EXE -c import sys; print "%s.%s.%s" % sys.version_info[:3];
[ERROR] gyp ERR! stack   File "<string>", line 1
[ERROR] gyp ERR! stack     import sys; print "%s.%s.%s" % sys.version_info[:3];
[ERROR] gyp ERR! stack                                ^
[ERROR] gyp ERR! stack SyntaxError: invalid syntax
[ERROR] gyp ERR! stack 
[ERROR] gyp ERR! stack     at ChildProcess.exithandler (child_process.js:275:12)
[ERROR] gyp ERR! stack     at emitTwo (events.js:126:13)
[ERROR] gyp ERR! stack     at ChildProcess.emit (events.js:214:7)
[ERROR] gyp ERR! stack     at maybeClose (internal/child_process.js:925:16)
[ERROR] gyp ERR! stack     at Process.ChildProcess._handle.onexit (internal/child_process.js:209:5)
[ERROR] gyp ERR! System Windows_NT 10.0.17134
......
[INFO] server-starter-es7 ................................. SUCCESS [ 11.657 s]
[INFO] apm-webapp ......................................... FAILURE [ 25.857 s]
[INFO] apache-skywalking-apm .............................. SKIPPED
[INFO] apache-skywalking-apm-es7 .......................... SKIPPED
```

### Reason

It has nothing to do with SkyWalking.   
According to https://github.com/sass/node-sass/issues/1176, if you live in countries where requesting resources from `GitHub` and `npmjs.org` is very slowly, some precompiled binaries for dependency `node-sass` will fail to be downloaded during `npm install`, then npm will try to compile them itself. That's why `python2` is needed.

### Resolve
#### 1. Use mirror. Such as in China, please edit `skywalking\apm-webapp\pom.xml`     
Find
```
<configuration>  
 <arguments>install --registry=https://registry.npmjs.org/</arguments>  
</configuration>
```
Replace it with
```
<configuration>  
 <arguments>install --registry=https://registry.npm.taobao.org/ --sass_binary_site=https://npm.taobao.org/mirrors/node-sass/</arguments>  
</configuration>
```
#### 2. Get an enough powerful VPN
