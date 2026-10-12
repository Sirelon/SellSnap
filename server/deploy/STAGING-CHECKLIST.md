# Staging live checklist (D30)

Runs against the Ukrainian account `ua` (`@sellsnap.app`, code prefix `uk`) after `server/deploy/deploy.sh` has finished. While the brand accounts have no audience this is the live test account (D30); once they have real followers a private test account takes its place. Bot commands go to the Telegram bot chat. Every step ends with the result to expect.

```bash
URL=https://sellsnap-agent-186709313778.europe-west1.run.app
```

Firestore edits are made in the console: <https://console.firebase.google.com/project/sellsnap-6e85c/firestore/databases/-default-/data/~2Fsocial_accounts~2Fua>.

## 1. Routes refuse strangers

1. `curl -i -X POST $URL/tick` returns `401`.
2. `curl -i -X POST $URL/batch` returns `401`.
3. `curl -i -X POST $URL/telegram -d '{}'` returns `403`.

## 2. Token intake

4. Generate a one-hour token for `@sellsnap.app` in the Meta dashboard's token generator. Send `/token ua <token>`. The bot deletes your message and replies `Token saved for ua. It expires <date>.` The date is about 60 days away (the short token was exchanged) and the reply names the private-profile access end date.
5. Firestore `social_accounts/ua` now has `token`, `tokenExpiresAt` and `threadsUserId`.

## 3. Private-profile probe (owner's OK first)

Meta's docs do not say whether a private profile can publish through the API. This probe is run by hand, once, and only with the owner's OK. It publishes one text post to `@sellsnap.app` outside the agent, then deletes it.

6. In zsh:

   ```bash
   read -s "TOK?Threads token: "; echo
   THREADS_USER=$(curl -s -G https://graph.threads.net/v1.0/me -d fields=id -d access_token="$TOK" | jq -r .id)
   CONTAINER=$(curl -s -X POST "https://graph.threads.net/v1.0/$THREADS_USER/threads" -d media_type=TEXT -d "text=API check, deleting now" -d access_token="$TOK" | jq -r .id)
   sleep 30
   MEDIA=$(curl -s -X POST "https://graph.threads.net/v1.0/$THREADS_USER/threads_publish" -d creation_id="$CONTAINER" -d access_token="$TOK" | jq -r .id)
   echo "$MEDIA"
   ```

   A numeric `MEDIA` means a private profile can publish. An `error` object instead means it cannot: stop here and tell the owner, because `sellsnap.app` would then have to go public (D3).
7. Delete the probe post, then clear the token from the shell:

   ```bash
   curl -s -X DELETE -G "https://graph.threads.net/v1.0/$MEDIA" -d access_token="$TOK"
   unset TOK
   ```

   Expect `{"success":true}`. Check that the post is gone in the Threads app. The agent does not track this post.

## 4. Publish: text, poll, image

8. `/post ua now Check one: a plain text post`. The bot replies `Published <code>.` with the post's preview and a Delete button. The post shows on `@sellsnap.app`.
9. Poll. Send one message with three lines:

   ```
   /post ua now Which do you list first?
   ? Phone
   ? Bike
   ```

   Expect `Published <code>.` and a poll with both options on Threads.
10. Image. Send a photo to the bot with the caption `/post ua now Check three: a photo post`. Expect `Published <code>.` and the photo on Threads. `curl -sI "https://firebasestorage.googleapis.com/v0/b/sellsnap-6e85c.firebasestorage.app/o/social%2Fmanual%2F<code>.jpg?alt=media"` returns `200`.
11. Slot publish. Send `/post ua A post for its slot`, tap Approve, and wait for the next 08:30 or 19:30 Kyiv slot. The post appears at the slot, within the hour's tick. Run `gcloud scheduler jobs run sellsnap-tick --location europe-west1 --project sellsnap-6e85c` to see a tick on demand.

## 5. Edit, Skip, Delete

12. Edit. Send `/post ua Edit me`, tap Edit, reply to the prompt with `Edited text`. Expect `Saved and approved <code>.` Firestore `social_posts/<code>` has `editedByOwner: true`; `social_accounts/ua.approvalStreak` is `0`.
13. Skip. Send `/post ua Skip me`, tap Skip. Expect `Skipped.`
14. Delete after publish. Tap Delete on the preview from step 8. Expect `Deleted from Threads.` and `@sellsnap.app is back in approval mode.` The post is gone from Threads and its Firestore `status` is `deleted`.
15. Delete before slot. In the console set `social_accounts/ua.mode` to `auto`, then run the tick (step 11's command). The next queued tip arrives with a `Delete before slot` button and no Approve. Tap it. Expect `Deleted before its slot.` and `@sellsnap.app is back in approval mode.`; `mode` reads `approval` again.

## 6. Refresh

16. Next day (a token refreshes once it is 24 hours old), send `/refresh ua`. Expect `Token for ua refreshed. It expires <date>.` with a date about 60 days out. Before 24 hours the reply is `The refresh failed: ...`, which is correct.

## 7. A 4xx pauses the account

17. In the Firestore console, edit `social_accounts/ua.token`: change one character in the middle of the value (Threads still accepts a token with extra characters at the end). Keep the original to restore it. Do not log either value.
18. Send `/post ua now Check four: forced failure`. Expect `<code> failed and the account is paused: ...`, an alert message that ends with `send /resume ua`, and `paused: true` on the account. Threads answers code 190 here.
19. Send `/token ua <valid token>`. Expect `Token saved for ua.`
20. Send `/resume ua`. Expect `@sellsnap.app is resumed.` Then `/post ua now Check four: recovered` publishes. Delete it with its Delete button.

## 8. Website links

Posts link to the website, which reports visits and store taps to `POST /click`. Take a published code from the steps above, for example `ukt0001`; it stays `<code>` below.

21. On an Android phone open `https://sirelon.github.io/SellSnap/?lang=uk&p=<code>`. The page loads in Ukrainian. Tap the Google Play button. Expect the Play Store page and, before leaving the page, the button's `href` reading `https://play.google.com/store/apps/details?id=com.sirelon.sellsnap&referrer=utm_source%3Dthreads%26utm_medium%3Dsocial%26utm_campaign%3Duk%26utm_content%3D<code>`. Check the `href` in a desktop browser's inspector on the same URL if the phone hides it.
22. On an iPhone open the same URL and tap the App Store button. Expect the App Store page `apps.apple.com/app/id6776001373`, and the button's `href` carrying `ct=<code>`.
23. In a desktop browser open the same URL and tap both store buttons. Expect each button to open its store page and to carry the rewritten `href` of steps 21 and 22.
24. Firestore `social_clicks` holds one `landing` document per page load and one `play` or `appstore` document per tap, with the `uaFamily` of the device (`android`, `ios`, `other`), all with `code` `<code>`: after steps 21 to 23, three `landing`, two `play` and two `appstore` documents. `https://sirelon.github.io/SellSnap/?lang=uk` without `p`, and a page with an unknown code such as `p=ukt9999`, add no document and leave the store `href`s as they are.
25. Cloud Logging has no request log for `/click` or `/go`: `gcloud logging read 'resource.type="cloud_run_revision" AND (httpRequest.requestUrl:"/click" OR httpRequest.requestUrl:"/go/")' --freshness=1h --limit=3 --project sellsnap-6e85c` prints nothing.

## 9. Weekly batch, previews, regenerate and digest

Needs the `GEMINI_API_KEY` secret on the service, and `server/deploy/deploy.sh 13` done for the install line of the digest. The manual job `sellsnap-batch-manual` runs the batch for `ua` only.

26. `gcloud scheduler jobs run sellsnap-batch-manual --location europe-west1 --project sellsnap-6e85c`. Within a few minutes the bot chat receives, in this order: one header `@sellsnap.app, week <yyyy>-W<nn>: <n> drafts`, one preview per draft (each header line holds the account, the code, the slot and, when the checks found something, `needs attention`; the buttons are Approve, Edit, Skip and Regenerate), the closing line `Failed slots: none; AI calls today: <n>.`, then the digest. Firestore `social_batches/<week>` shows `accounts.ua.done: true` and `digestSent` holding `ua`.
27. Polish back-translation. For a Polish account that has a token, point the manual job at it for one run: `gcloud scheduler jobs update http sellsnap-batch-manual --location europe-west1 --project sellsnap-6e85c --message-body '{"accounts":["pl"]}'`, run it as in step 26, then set the body back to `{"accounts":["ua"]}`. Every Polish preview ends with a line `EN: <English back-translation>`; a draft whose checks found a problem also ends with `Check: <notes>` and carries `needs attention` in its header.
28. Regenerate. Tap Regenerate under one preview. The bot answers `Writing a new version…` and the preview's text is replaced within a minute; Firestore `social_posts/<code>.regenerations` is `1`. The fourth tap on the same post answers `No more new versions for this post.`
29. Approve. Tap Approve under a preview. Expect `Approved.` and the buttons change to Cancel; Firestore `social_posts/<code>.status` is `approved`. The post publishes at its slot.
30. The digest message starts `@sellsnap.app, digest for <d MMM yyyy> to <d MMM yyyy>` and holds every line: `Follower change` (in the first week `n/a (first snapshot on <date>)` and `Followers now: <n>.`), `Posts published`, `Views` and `Replies`, `Clicks on all posts` split into landing, play and appstore, `Top 3 by views` and `Bottom <n> by views` with each post's code, the start of its text, its views, replies and clicks, `Android installs from Threads`, `Events` (errors, pauses, resumes, mode changes, deletes) and `Model use, last 7 UTC days` with calls and tokens per model against the daily call limit. Without any views figure the lists read `by likes + replies (views unavailable)`.
31. Run step 26 again. No second digest arrives, and no new draft: the batch answers `already done`. To see the digest again, remove `ua` from `digestSent` of `social_batches/<week>` in the console and run the job.
32. Install attribution. On an Android phone with SellSnap uninstalled, open `https://sirelon.github.io/SellSnap/?lang=uk&p=<code>`, tap the Google Play button, install, and open the app once. The Firebase export lists the install after about a day: remove `ua` from `digestSent` as in step 31 and run the manual job again, then check that Firestore `social_installs/<yyyymmdd>_<code>` exists with `installs: 1` and that the next digest's `Android installs from Threads` line names `<code>`. Android installs only; iOS installs are not attributed.

## 10. Clean up

33. Delete every test post still on `@sellsnap.app` with its Delete button, then send `/approval ua`. The seed tips stay queued.
