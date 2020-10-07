# Apache SkyWalking committer
SkyWalking Project Management Committee(PMC) takes the responsibilities to assess the contributions of candidates.

In the SkyWalking, like many Apache projects, we treat contributions including, but not limited to, code contributions.
Such as writing blog, guiding new users, give public speak, prompting project in various ways, are all treated as significant 
contributions.

## Committer
### New committer nomination
In the SkyWalking, **new committer nomination** could only be started by existing PMC members officially. The new contributor
could contact any existing PMC member if he/she feels he/she is qualified. Talk with the PMC member, if some members agree,
they could start the process.

The following steps are recommended, and could only be started by existing PMC member.
1. Send the `[DISCUSS] Promote xxx as new committer` mail to `private@skywalking.a.o`. List the important contributions of the candidates,
in order to help the PMC members supporting your proposal.
1. Keep discussion open in more than 3 days, but not more than 1 week, unless there is any explicit objection or concern.
1. Send the `[VOTE] Promote xxx as new committer` mail to `private@skywalking.a.o`, when the PMC seems to agree the proposal.
1. Keep vote more than 3 days, but not more than 1 week. Consider the result as `Consensus Approval` if there 3 +1 votes and
+1 votes > -1 votes
1. Send the `[RESULT][VOTE] Promote xxx as new committer` mail to `private@skywalking.a.o`, and list the vote detail including
the voters.

### Invite new committer
The PMC member, who start the promotion, takes the responsibilities to send the invitation to new committer and guide him/her to set
up the ASF env.

You should send the mail like the following template to new committer
```
To: JoeBloggs@foo.net
Cc: private@skywalking.apache.org
Subject: Invitation to become SkyWalking committer: Joe Bloggs

Hello [invitee name],

The SkyWalking Project Management Committee] (PMC) 
hereby offers you committer privileges to the project . These privileges are
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
And the new committer should reply the mail to `private@skywalking.apache.org`(Choose `reply all`), and express the will to accept the invitation explicitly.
Then this invitation will be treated as accepted by project PMC. Of course, the new committer could just say NO, and reject the invitation.

If they accepted, then they need to do the following things.
1. Make sure they have subscribed the `dev@skywalking.apache.org`. Usually they already have.
1. Choose a Apache ID that is not in the [apache committers list page](http://people.apache.org/committer-index.html).
1. Download the [ICLA](https://www.apache.org/licenses/icla.pdf) (If they are going to contribute to the project as day job, [CCLA](http://www.apache.org/licenses/cla-corporate.pdf)  is expected).
1. After filling the `icla.pdf` (or `ccla.pdf`) with information correctly, print, sign it manually (by hand),  scan it as an pdf, and send it in mail as an attachment to the [secretary@apache.org](mailto:secretary@apache.org). (If they prefer to sign electronically, please follow the steps of [this page](http://www.apache.org/licenses/contributor-agreements.html#submitting))
1. Then the PMC will wait the Apache secretary confirmed the ICLA (or CCLA) filed. The new committer and PMC will receive the mail like following

```
Dear XXX,

This message acknowledges receipt of your ICLA, which has been filed in the Apache Software Foundation records.

Your account has been requested for you and you should receive email with next steps
within the next few days (can take up to a week).

Please refer to https://www.apache.org/foundation/how-it-works.html#developers
for more information about roles at Apache.
```

If in some case, the account has not be requested(rarely to see), the PMC member should contact the project V.P..
The V.P. could request through the [Apache Account Submission Helper Form](https://whimsy.apache.org/officers/acreq).

After several days, the new committer will receive the account created mail, as this title, `Welcome to the Apache Software Foundation (ASF)!`.
At this point, congratulate! You have the official Apache ID.

The PMC member should add the new committer to official committer list through [roster](https://whimsy.apache.org/roster/committee/skywalking). 

### Set up the Apache ID and dev env
1. Go to [Apache Account Utility Platform](https://id.apache.org/), initial your password, set up your personal mailbox(`Forwarding email address`) and GitHub account(`Your GitHub Username`). An organisational invite will be sent to you via email shortly thereafter (within 2 hours).
1. If you want to use `xxx@apache.org` to send mail, please refer to [here](https://infra.apache.org/committer-email.html). Gmail is recommended, because in other mailbox service settings, this forwarding mode is not easy to find.
1. Following the [authorized GitHub 2FA wiki](https://help.github.com/articles/configuring-two-factor-authentication-via-a-totp-mobile-app/) to enable two-factors authorization (2FA) on [github](http://github.com/). When you set 2FA to "off", it will be delisted by the corresponding Apache committer write permission group until you set it up again. (**NOTE: Treat your recovery codes with the same level of attention as you would your password !**)
1. Use [GitBox Account Linking Utility](https://gitbox.apache.org/setup/) to obtain write permission of the SkyWalking project.
1. Follow this [doc](https://github.com/apache/skywalking-website#how-to-add-a-new-committer) to update the website.

If you want others could see you are in the Apache GitHub org, you need to go to [Apache GitHub org people page](https://github.com/orgs/apache/people), 
search for yourself, and choose `Organization visibility` to `Public`.

### Committer rights, duties and responsibilities
SkyWalking project doesn't require the continue contributions after you become a committer, but we hope and truly want you could.

Being a committer, you could
1. Review and merge the pull request to the master branch in the Apache repo. A pull request often contains multiple commits. Those commits **must be squashed and merged** into a single commit **with explanatory comments**. For new committer, we hope you could request some senior committer to recheck the pull request.
1. Create and push codes to new branch in the Apache repo.
1. Follow the [Release process](../How-to-release.md) to process new release. Of course, you need to ask committer team
to confirm it is the right time to release.

The PMC hope the new committer to take part in the release and release vote, even still be consider `+1 no binding`.
But be familiar with the release is one of the key to be promoted as a PMC member.

## Project Management Committee
Project Management Committee(PMC) member has no special rights in code contributions. 
They just cover and make sure the project following the Apache requirement, 
including 
1. Release binding vote and license check
1. New committer and PMC member recognition
1. Identify branding issue and do branding protection.
1. Response the ASF board question, take necessary actions.

V.P. and chair of the PMC is the secretary, take responsibility of initializing the board report.

In the normal case, the new PMC member should be nominated from committer team. But becoming a PMC member directly is not forbidden, if the PMC could
agree and be confidence that the candidate is ready, such as he/she has been a PMC member of another project, Apache member
or Apache officer.

The process of new PMC vote should also follow the same `[DISCUSS]`, `[VOTE]` and `[RESULT][VOTE]` in private mail list as [new committer vote](#new-committer-nomination).
One more step before sending the invitation, the PMC [need to send NOTICE mail to Apache board](http://www.apache.org/dev/pmc.html#newpmc).
```
To: board@apache.org
Cc: private@skywalking.apache.org
Subject: [NOTICE] Jane Doe for SkyWalking PMC

SkyWalking proposes to invite Jane Doe (janedoe) to join the PMC.

(include if a vote was held) The vote result is available here: https://lists.apache.org/...
```

After 72 hours, if the board doesn't object(usually it wouldn't be), send the invitation.

After the committer accepted the invitation, 
The PMC member should add the new committer to official PMC list through [roster](https://whimsy.apache.org/roster/committee/skywalking).
