Apache SkyWalking 发布指南
--------------------
本文档指导每个提交者在Apache Way中发布SkyWalking，并且还帮助提交者检查发布的投票。


## 设置您的开发环境
按照 [Apache maven部署环境文档](http://www.apache.org/dev/publishing-maven-artifacts.html#dev-env)设置gpg工具和加密密码

使用以下代码块作为模板并将其放入 ~/.m2/settings.xml

```
<settings>
...
  <servers>
    <!-- To publish a snapshot of some part of Maven -->
    <server>
      <id>apache.snapshots.https</id>
      <username> <!-- YOUR APACHE LDAP USERNAME --> </username>
      <password> <!-- YOUR APACHE LDAP PASSWORD (encrypted) --> </password>
    </server>
    <!-- To stage a release of some part of Maven -->
    <server>
      <id>apache.releases.https</id>
      <username> <!-- YOUR APACHE LDAP USERNAME --> </username>
      <password> <!-- YOUR APACHE LDAP PASSWORD (encrypted) --> </password>
    </server>
   ...
  </servers>
</settings>
```

## 添加您的GPG公钥
1. 将您的GPG公钥添加到 [SkyWalking GPG KEYS](https://dist.apache.org/repos/dist/release/incubator/skywalking/KEYS) 文件中,
只有你是一个提交者，使用你的Apache id和密码登录这个svn，并更新文件。 **不要覆盖现有文件**
1. 将您的GPG公钥上传到公共GPG站点。 如[MIT's 站点](http://pgp.mit.edu:11371/). 这个网站应该在Apache maven staging repository检查列表

## 测试你的配置
此步骤仅用于测试，如果您的env设置正确，则不需要每次都检查。
```
./mvnw clean install -Papache-release (这将构建工件，来源和标志)
```

## 准备发布
```
./mvnw release:clean
./mvnw release:prepare -DautoVersionSubmodules=true -Pauto-submodule
```
_你可以在发布之前做一个GPG标志，如果你需要输入密码进行签名，而且maven没有给出机会，
但只是失败。 运行`gpg --sign xxx`到任何文件都可以记住密码有足够的时间来发布._

## 发布阶段 
```
./mvnw release:perform -DskipTests -Pauto-submodule
```
1.将版本号设置为x.y.z，标记为** v ** x.y.z（版本标记必须以** v **开头，您将在下一步中找到目的。）
1.该版本将自动插入临时存储库中。

## 构建并签署源代码包
```shell
export RELEASE_VERSION=x.y.z (example: RELEASE_VERSION=5.0.0-alpha)
cd tools/releasing
sh create_source_release.sh
```

**注意**, `create_source_release.sh` 只适合MacOS。 欢迎任何人贡献Windows bat和Linux shell。

这个脚本应该做以下事情
1. 使用 `v` + `RELEASE_VERSION` 作为标记来克隆代码。
1. 完成 `git submodule init/update` .
1. 排除目标源tar中的所有不必要的文件，例如.git，.github，.gitmodules。 有关详细信息，请参阅脚本。
1. 执行 `gpg` 和 `shasum 512`. 


应该在`tools/releasing`文件夹中找到`apache-skywalking-apm-incubating-x.y.z-src.tgz`和.asc, .sha512结尾的文件

## 在Apache Nexus Staging存储库中查找和下载分发
1. 使用ApacheId登录 `https://repository.apache.org/`。
1. 跳转到 `https://repository.apache.org/#stagingRepositories`。
1. 搜索 `skywalking` 并找到您的暂存存储库。
1. 关闭存储库并等待所有检查通过。 在此步骤中，将检查您的GPG KEYS。参考[设置PGP文档](#add-your-gpg-public-key)，如果你以前没有这样做过。
1. 跳转到 `{REPO_URL}/org/apache/skywalking/apache-skywalking-apm-incubating/x.y.z`
1. 下载 `.tar.gz` 和 `.zip` 且有 .asc 和 .sha1 结尾的文件


## 上传到Apache svn
1. 使用ApacheId登录 `https://dist.apache.org/repos/dist/dev/incubator/skywalking/`。
1. 创建文件夹，按发行版本和圆形命名，例如：x.y.z。
1. 将源代码包上传到包含.asc，.sha512的文件夹
    * 包名：apache-skywalking-incubating-x.y.z-src.tar.gz
    * 有关详细信息，请参见“构建和签署源代码包”一节 
1. 使用.asc，.sha512将分发包上载到该文件夹
    * 包名：apache-skywalking-incubating-x.y.z.tar.gz, apache-skywalking-incubating-x.y.z.zip
    * 有关详细信息，请参见“在Apache Nexus Staging存储库中查找和下载分发”一节
    * 创建.sha512包: `shasum -a 512 file > file.sha512`

## 发表内部公告
在开发邮件列表中发送公告邮件。

```
邮件标题: [ANNOUNCE] SkyWalking x.y.z test build available

邮件内容:
The test build of x.y.z is available.

This is our Apache Incubator release.
We welcome any comments you may have, and will take all feedback into
account if a quality vote is called for this build.

Release notes:

 * https://github.com/apache/incubator-skywalking/blob/master/CHANGES.md

Release Candidate:

 * https://dist.apache.org/repos/dist/dev/incubator/skywalking/xxxx
 * sha512 checksums
   - sha512xxxxyyyzzz apache-skywalking-apm-incubating-x.x.x-src.tgz
   - sha512xxxxyyyzzz apache-skywalking-apm-incubating-x.x.x.tar.gz
   - sha512xxxxyyyzzz apache-skywalking-apm-incubating-x.x.x.zip

Maven 2 staging repository:

 * https://repository.apache.org/content/repositories/xxxx/org/apache/skywalking/

Release Tag :

 * (Git Tag) x.y.z

Release CommitID :

 * https://github.com/apache/incubator-skywalking/tree/(Git Commit ID)
 * Git submodule
   * skywalking-ui: https://github.com/apache/incubator-skywalking-ui/tree/(Git Commit ID)
   * apm-protocol/apm-network/src/main/proto: https://github.com/apache/incubator-skywalking-data-collect-protocol/tree/(Git Commit ID)
   * oap-server/server-query-plugin/query-graphql-plugin/src/main/resources/query-protocol https://github.com/apache/incubator-skywalking-query-protocol/tree/(Git Commit ID)

Keys to verify the Release Candidate :

 * http://pgp.mit.edu:11371/pks/lookup?op=get&search=0x2EF5026E70A55777 corresponding to pengys@apache.org

Guide to build the release from source :

 * https://github.com/apache/incubator-skywalking/blob/x.y.z/docs/en/guides/How-to-build.md

A vote regarding the quality of this test build will be initiated
within the next couple of days.
```

## 等待至少48小时的测试响应
任何PPMC，提交者或贡献者都可以测试发布和反馈的功能。
基于此，PPMC将决定是否开始投票。

## 在dev群组中投票
在`dev @ skywalking.apache.org`中投票

```
邮件标题: [VOTE] Release Apache SkyWalking (incubating) version x.y.z

邮件内容:
Hi All,
This is a call for vote to release Apache SkyWalking (Incubating) version x.y.z.

Release notes:

 * https://github.com/apache/incubator-skywalking/blob/x.y.z/CHANGES.md

Release Candidate:

 * https://dist.apache.org/repos/dist/dev/incubator/skywalking/xxxx
 * sha512 checksums
   - sha512xxxxyyyzzz apache-skywalking-apm-incubating-x.x.x-src.tgz
   - sha512xxxxyyyzzz apache-skywalking-apm-incubating-x.x.x.tar.gz
   - sha512xxxxyyyzzz apache-skywalking-apm-incubating-x.x.x.zip

Maven 2 staging repository:

 * https://repository.apache.org/content/repositories/xxxx/org/apache/skywalking/

Release Tag :

 * (Git Tag) x.y.z

Release CommitID :

 * https://github.com/apache/incubator-skywalking/tree/(Git Commit ID)
 * Git submodule
   * skywalking-ui: https://github.com/apache/incubator-skywalking-ui/tree/(Git Commit ID)
   * apm-protocol/apm-network/src/main/proto: https://github.com/apache/incubator-skywalking-data-collect-protocol/tree/(Git Commit ID)
   * oap-server/server-query-plugin/query-graphql-plugin/src/main/resources/query-protocol https://github.com/apache/incubator-skywalking-query-protocol/tree/(Git Commit ID)

Keys to verify the Release Candidate :

 * http://pgp.mit.edu:11371/pks/lookup?op=get&search=0x2EF5026E70A55777 corresponding to pengys@apache.org

Guide to build the release from source :

 * https://github.com/apache/incubator-skywalking/blob/x.y.z/docs/en/guides/How-to-build.md

Voting will start now (xxxx date) and will remain open for at least 72 hours, Request all PPMC members to give their vote.
[ ] +1 Release this package.
[ ] +0 No opinion.
[ ] -1 Do not release this package because....
```

## 投票检查
所有PPMC成员和提交者都应在投票+1之前检查这些。

1. 功能测试。
1. staging repository中的所有工件都使用.asc，.md5，* sha1文件发布
1. 源代码和下发包 (apache-skywalking-incubating-x.y.z-src.tar.gz, apache-skywalking-incubating-x.y.z.tar.gz, apache-skywalking-incubating-x.y.z.zip)
都应该在 `https://dist.apache.org/repos/dist/dev/incubator/skywalking/x.y.z` 且包含 .asc, .sha512
1. `LICENSE` 和 `NOTICE` 文件在源代码和分发包中。
1. 检查 `shasum -c apache-skywalking-apm-incubating-x.y.z-src.tgz.sha512`
1. 构建发布源代码包 (apache-skywalking-incubating-x.y.z-src.tar.gz) by following this [文档](https://github.com/apache/incubator-skywalking/blob/master/docs/en/How-to-build.md#build-from-apache-source-codes).
1. Apache RAT检查。运行`./mvnw apache-rat:check`. (源代码中没有二进制文件)
1. 需要有免责声明

投票结果应遵循这些。
1.PPMC投票是+1绑定，所有其他投票是+1没有约束力。
1.在72小时内，你得到至少3（+1绑定），并且+1比-1更多。投票通过。

## 要求在Apache IPMC中投票
在`general @ incubator.apache.org`中投票

```
邮件标题: [VOTE] Release Apache SkyWalking (incubating) version x.y.z

邮件内容:
Hi All,
This is a call for vote to release Apache SkyWalking (Incubating) version x.y.z.

The Apache SkyWalking community has tested, voted and approved the proposed
release of Apache SkyWalking (Incubating) x.y.z

We now kindly request the Incubator PMC members review and vote on this
incubator release.

SkyWalking: APM (application performance monitor) tool for distributed systems, 
especially designed for microservices, cloud native and container-based (Docker, Kubernetes, Mesos) architectures. 
Underlying technology is a distributed tracing system.

Vote Thread:

 * From `list.apache.org`

Result Thread:

 * From the vote thread.

Release notes:

 * https://github.com/apache/incubator-skywalking/blob/x.y.z/CHANGES.md

Release Candidate:

 * https://dist.apache.org/repos/dist/dev/incubator/skywalking/xxxx
 * sha512 checksums
   - sha512xxxxyyyzzz apache-skywalking-apm-incubating-x.x.x-src.tgz
   - sha512xxxxyyyzzz apache-skywalking-apm-incubating-x.x.x.tar.gz
   - sha512xxxxyyyzzz apache-skywalking-apm-incubating-x.x.x.zip

Maven 2 staging repository:

 * https://repository.apache.org/content/repositories/xxxx/org/apache/skywalking/

Release Tag :

 * (Git Tag) x.y.z

Release CommitID :

 * https://github.com/apache/incubator-skywalking/tree/(Git Commit ID)
 * Git submodule
   * skywalking-ui: https://github.com/apache/incubator-skywalking-ui/tree/(Git Commit ID)
   * apm-protocol/apm-network/src/main/proto: https://github.com/apache/incubator-skywalking-data-collect-protocol/tree/(Git Commit ID)
   * oap-server/server-query-plugin/query-graphql-plugin/src/main/resources/query-protocol https://github.com/apache/incubator-skywalking-query-protocol/tree/(Git Commit ID)

Keys to verify the Release Candidate :

 * http://pgp.mit.edu:11371/pks/lookup?op=get&search=0x2EF5026E70A55777 corresponding to pengys@apache.org

Guide to build the release from source :

 * https://github.com/apache/incubator-skywalking/blob/x.y.z/docs/en/guides/How-to-build.md

Voting will start now (xxxx date) and will remain open for at least 72 hours, Request IPMC to give their vote.
[ ] +1 Release this package.
[ ] +0 No opinion.
[ ] -1 Do not release this package because....
```

## 投票结果邮件
关闭投票，如果：
1. I在72小时内，你得到至少3（+1绑定），并且+1比-1更多。 投票通过。 在IPMC投票中，只有孵化器项目PMC
投票将被视为+1绑定。
1. 一些评论者在这个版本中发现了一些严重的错误，团队可以决定取消投票并准备一个新的RC。

发送邮件到 `general@incubator.apache.org` 涉及投票结果和状态.
```
邮件标题: [Result][VOTE] Release Apache SkyWalking (incubating) version x.y.z

邮件内容:
Hi all,

The vote for releasing Apache SkyWalking x.y.z (incubating) is closed, now.

Vote result:
x (+1 binding) (Names of voters)
y -1.

Thank you everyone for taking the time to review the release and help us. 

I will process to publish the release and send ANNOUNCE.
```

## 发版
1. 将源代码tar球和发行版移动到 `https://dist.apache.org/repos/dist/release/incubator/skywalking/`.

```
> export SVN_EDITOR=vim
> svn mv https://dist.apache.org/repos/dist/dev/incubator/skywalking/x.y.z https://dist.apache.org/repos/dist/release/incubator/skywalking
....
enter your apache password
....

```
2. 在nexus staging repo中发布.
3. 公共下载源和下发 tar/zip 位于 `http://www.apache.org/dyn/closer.cgi/incubator/skywalking/x.y.z/xxx`.
我们只发布Apache镜像路径作为发布信息。
4. 公共asc和sha512位于 `https://www.apache.org/dist/incubator/skywalking/x.y.z/xxx`
5. 公共密钥指向 `https://www.apache.org/dist/incubator/skywalking/KEYS`
6. 将ANNOUNCE邮件发送到 `general@incubator.apache.org` 和 `dev@skywalking.apache.org`.

```
邮件标题: [ANNOUNCE] Release Apache SkyWalking (incubating) version x.y.z

邮件内容:
Hi all,

Apache SkyWalking (incubating) Team is glad to announce the first release of Apache SkyWalking Incubating x.y.z.

SkyWalking: APM (application performance monitor) tool for distributed systems, 
especially designed for microservices, cloud native and container-based (Docker, Kubernetes, Mesos) architectures. 
Underlying technology is a distributed tracing system.

Vote Thread: 

Download Links : http://skywalking.apache.org/downloads/

Release Notes : https://github.com/apache/incubator-skywalking/blob/x.y.z/CHANGES.md

Website: http://skywalking.apache.org/

SkyWalking Resources:
- Issue: https://github.com/apache/incubator-skywalking/issues
- Mailing list: dev@skywalkiing.incubator.apache.org
- Documents: https://github.com/apache/incubator-skywalking/blob/x.y.z/docs/README.md


- Apache SkyWalking (incubating) Team

=====
*Disclaimer*

Apache SkyWalking (incubating) is an effort undergoing incubation at The
Apache Software Foundation (ASF), sponsored by the name of Apache
Incubator PMC. Incubation is required of all newly accepted
projects until a further review indicates that the
infrastructure, communications, and decision making process have
stabilized in a manner consistent with other successful ASF
projects. While incubation status is not necessarily a reflection
of the completeness or stability of the code, it does indicate
that the project has yet to be fully endorsed by the ASF.
```

7. 更新网站下载页面。 http://skywalking.apache.org/downloads/ 。 包括新的下载源，分发，sha512，asc和文档，链接。 可通过遵循上述规则（3） - （6）找到链接。
8. 在网站主页甚至页面上添加发布活动。 通过更改日志或主要功能宣布公开发布。
