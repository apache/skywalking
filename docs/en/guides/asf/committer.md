# Apache SkyWalking committer
SkyWalking Project Management Committee (PMC) is responsible for assessing the contributions of candidates.

Like many Apache projects, SkyWalking welcome all contributions, including code contributions, blog entries, guides for new users, public speeches, and enhancement of the project in various ways.

## Committer
### Nominate new committer
In SkyWalking, **new committer nomination** could only be officially started by existing PMC members. If a new committer feels that he/she is qualified, he/she should contact any existing PMC member and discuss. If this is agreed among some members of the PMC, the process will kick off.

The following steps are recommended (to be initiated only by an existing PMC member):
1. Send an email titled `[DISCUSS] Promote xxx as new committer` to `private@skywalking.a.o`. List the important contributions of the candidate,
so you could gather support from other PMC members for your proposal.
1. Keep the discussion open for more than 3 days but no more than 1 week, unless there is any express objection or concern.
1. If the PMC generally agrees to the proposal, send an email titled `[VOTE] Promote xxx as new committer` to `private@skywalking.a.o`.
1. Keep the voting process open for more than 3 days, but no more than 1 week. Consider the result as `Consensus Approval` if there are three +1 votes and
+1 votes > -1 votes.
1. Send an email titled `[RESULT][VOTE] Promote xxx as new committer` to `private@skywalking.a.o`, and list the voting details, including who the voters are.

### Invite new committer
The PMC member who starts the promotion is responsible for sending an invitation to the new committer and guiding him/her to set up the ASF env.

The PMC member should send an email using the following template to the new committer:
```
To: JoeBloggs@foo.net
Cc: private@skywalking.apache.org
Subject: Invitation to become SkyWalking committer: Joe Bloggs

Hello [invitee name],

The SkyWalking Project Management Committee] (PMC) 
hereby offers you committer privileges to the project. These privileges are
offered on the understanding that you'll use them
reasonably and with common sense. We like to work on trust
rather than unnecessary constraints.

Being a committer enables you to more easily make 
changes without needing to go through the patch 
submission process. 

Being a committer does not require you to 
participate any more than you already do. It does 
tend to make one even more committed.  You will 
probably find that you spend more time here.

Of course, you can decline and instead remain as a 
contributor, participating as you do now.

A. This personal invitation is a chance for you to 
accept or decline in private.  Either way, please 
let us know in reply to the [private@skywalking.apache.org] 
address only.

B. If you accept, the next step is to register an iCLA:
    1. Details of the iCLA and the forms are found 
    through this link: http://www.apache.org/licenses/#clas

    2. Instructions for its completion and return to 
    the Secretary of the ASF are found at
    http://www.apache.org/licenses/#submitting

    3. When you transmit the completed iCLA, request 
    to notify the Apache SkyWalking and choose a 
    unique Apache id. Look to see if your preferred 
    id is already taken at 
    http://people.apache.org/committer-index.html     
    This will allow the Secretary to notify the PMC 
    when your iCLA has been recorded.

When recording of your iCLA is noticed, you will 
receive a follow-up message with the next steps for 
establishing you as a committer.
```

### Invitation acceptance process
The new committer should reply to `private@skywalking.apache.org` (choose `reply all`), and express his/her intention to accept the invitation.
Then, this invitation will be treated as accepted by the project's PMC. Of course, the new committer may also choose to decline the invitation.

Once the invitation has been accepted, the new committer has to take the following steps:
1. Subscribe to `dev@skywalking.apache.org`. Usually this is already done.
1. Choose a Apache ID that is not on the [apache committers list page](http://people.apache.org/committer-index.html).
1. Download the [ICLA](https://www.apache.org/licenses/icla.pdf)  (If the new committer contributes to the project as a day job, [CCLA](http://www.apache.org/licenses/cla-corporate.pdf)  is expected).
1. After filling in the `icla.pdf` (or `ccla.pdf`) with the correct information, print, sign it by hand,  scan it as an PDF, and send it as an attachment to [secretary@apache.org](mailto:secretary@apache.org). (If electronic signature is preferred, please follow the steps on [this page](http://www.apache.org/licenses/contributor-agreements.html#submitting))
1. The PMC will wait for the Apache secretary to confirm the ICLA (or CCLA) filed. The new committer and PMC will receive the following email:

```
Dear XXX,

This message acknowledges receipt of your ICLA, which has been filed in the Apache Software Foundation records.

Your account has been requested for you and you should receive email with next steps
within the next few days (can take up to a week).

Please refer to https://www.apache.org/foundation/how-it-works.html#developers
for more information about roles at Apache.
```

In the unlikely event that the account has not yet been requested, the PMC member should contact the project V.P..
The V.P. could request through the [Apache Account Submission Helper Form](https://whimsy.apache.org/officers/acreq).

After several days, the new committer will receive an email confirming creation of the account, titled `Welcome to the Apache Software Foundation (ASF)!`.
Congratulations! The new committer now has an official Apache ID.

The PMC member should add the new committer to the official committer list through [roster](https://whimsy.apache.org/roster/committee/skywalking). 

### Set up the Apache ID and dev env
1. Go to [Apache Account Utility Platform](https://id.apache.org/), create your password, set up your personal mailbox (`Forwarding email address`) and GitHub account(`Your GitHub Username`). An organizational invite will be sent to you via email shortly thereafter (within 2 hours).
1. If you would like to use the `xxx@apache.org` email service, please refer to [here](https://infra.apache.org/committer-email.html). Gmail is recommended, because this forwarding mode is not easy to find in most mailbox service settings.
1. Follow the [authorized GitHub 2FA wiki](https://help.github.com/articles/configuring-two-factor-authentication-via-a-totp-mobile-app/) to enable two-factor authorization (2FA) on [Github](http://github.com/). When you set 2FA to "off", it will be delisted by the corresponding Apache committer write permission group until you set it up again. (**NOTE: Treat your recovery codes with the same level of attention as you would your password!**)
1. Use [GitBox Account Linking Utility](https://gitbox.apache.org/setup/) to obtain write permission of the SkyWalking project.
1. Follow this [doc](https://github.com/apache/skywalking-website#how-to-add-a-new-committer) to update the website.

If you would like to show up publicly in the Apache GitHub org, you need to go to the [Apache GitHub org people page](https://github.com/orgs/apache/people), 
search for yourself, and choose `Organization visibility` to `Public`.

### Committer rights, duties, and responsibilities
The SkyWalking project doesn't require continuing contributions from you after you have become a committer, but we truly hope that you will continue to play a part in our community!

As a committer, you could
1. Review and merge the pull request to the master branch in the Apache repo. A pull request often contains multiple commits. Those commits **must be squashed and merged** into a single commit **with explanatory comments**. It is recommended for new committers to request recheck of the pull request from senior committers.
1. Create and push codes to the new branch in the Apache repo.
1. Follow the [release process](../How-to-release.md) to prepare a new release. Remember to confirm with the committer team
that it is the right time to create the release.

The PMC hopes that the new committer will take part in the release process as well as release voting, even though their vote will be regarded as `+1 no binding`.
Being familiar with the release process is key to being promoted to the role of PMC member.

## Project Management Committee
The Project Management Committee (PMC) member does not have any special rights in code contributions. 
They simply oversee the project and make sure that it follows the Apache requirements. Its functions include:
1. Binding voting for releases and license checks;
1. New committer and PMC member recognition;
1. Identification of branding issues and brand protection; and
1. Responding to questions raised by the ASF board, and taking necessary actions.

The V.P. and chair of the PMC is the secretary, who is responsible for initializing the board report.

In most cases, a new PMC member is nominated from the committer team. But it is also possible to become a PMC member directly, so long as the PMC agrees to the nomination and is confident that the candidate is ready. For instance, this can be demonstrated by the fact that he/she has been an Apache member, an Apache officer, or a PMC member of another project.

The new PMC voting process should also follow the `[DISCUSS]`, `[VOTE]` and `[RESULT][VOTE]` procedures using a private mail list, just like the [voting process for new committers](#new-committer-nomination).
Before sending the invitation, the PMC [must also send a NOTICE mail to the Apache board](http://www.apache.org/dev/pmc.html#newpmc).
```
To: board@apache.org
Cc: private@skywalking.apache.org
Subject: [NOTICE] Jane Doe for SkyWalking PMC

SkyWalking proposes to invite Jane Doe (janedoe) to join the PMC.

(include if a vote was held) The vote result is available here: https://lists.apache.org/...
```

After 72 hours, if the board doesn't object to the nomination (which it won't most cases), an invitation may then be sent to the candidate.

Once the invitation is accepted, a PMC member should add the new member to the official PMC list through [roster](https://whimsy.apache.org/roster/committee/skywalking).
