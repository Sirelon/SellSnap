# Staging live checklist (D30)

Runs against the staging account `staging` (`@sellsnap.app`, code prefix `st`) after `server/deploy/deploy.sh` has finished and the owner has put the service URL into the Meta app. Bot commands go to the Telegram bot chat. Every step ends with the result to expect.

```bash
URL=https://sellsnap-agent-186709313778.europe-west1.run.app
```

Firestore edits are made in the console: <https://console.firebase.google.com/project/sellsnap-6e85c/firestore/databases/-default-/data/~2Fsocial_accounts~2Fstaging>.

## 1. Routes refuse strangers

1. `curl -i -X POST $URL/tick` returns `401`.
2. `curl -i -X POST $URL/batch` returns `401`.
3. `curl -i -X POST $URL/telegram -d '{}'` returns `403`.

## 2. Token intake

4. Generate a one-hour token for `@sellsnap.app` in the Meta dashboard's token generator. Send `/token staging <token>`. The bot deletes your message and replies `Token saved for staging. It expires <date>.` The date is about 60 days away (the short token was exchanged) and the reply names the private-profile access end date.
5. Firestore `social_accounts/staging` now has `token`, `tokenExpiresAt` and `threadsUserId`.

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

8. `/post staging now Check one: a plain text post`. The bot replies `Published <code>.` with the post's preview and a Delete button. The post shows on `@sellsnap.app`.
9. Poll. Send one message with three lines:

   ```
   /post staging now Which do you list first?
   ? Phone
   ? Bike
   ```

   Expect `Published <code>.` and a poll with both options on Threads.
10. Image. Send a photo to the bot with the caption `/post staging now Check three: a photo post`. Expect `Published <code>.` and the photo on Threads. `curl -sI "https://firebasestorage.googleapis.com/v0/b/sellsnap-6e85c.firebasestorage.app/o/social%2Fmanual%2F<code>.jpg?alt=media"` returns `200`.
11. Slot publish. Send `/post staging A post for its slot`, tap Approve, and wait for the next 08:30 or 19:30 Kyiv slot. The post appears at the slot, within the hour's tick. Run `gcloud scheduler jobs run sellsnap-tick --location europe-west1 --project sellsnap-6e85c` to see a tick on demand.

## 5. Edit, Skip, Delete

12. Edit. Send `/post staging Edit me`, tap Edit, reply to the prompt with `Edited text`. Expect `Saved and approved <code>.` Firestore `social_posts/<code>` has `editedByOwner: true`; `social_accounts/staging.approvalStreak` is `0`.
13. Skip. Send `/post staging Skip me`, tap Skip. Expect `Skipped.`
14. Delete after publish. Tap Delete on the preview from step 8. Expect `Deleted from Threads.` and `@sellsnap.app is back in approval mode.` The post is gone from Threads and its Firestore `status` is `deleted`.
15. Delete before slot. In the console set `social_accounts/staging.mode` to `auto`, then run the tick (step 11's command). The next queued tip arrives with a `Delete before slot` button and no Approve. Tap it. Expect `Deleted before its slot.` and `@sellsnap.app is back in approval mode.`; `mode` reads `approval` again.

## 6. Refresh

16. Next day (a token refreshes once it is 24 hours old), send `/refresh staging`. Expect `Token for staging refreshed. It expires <date>.` with a date about 60 days out. Before 24 hours the reply is `The refresh failed: ...`, which is correct.

## 7. A 4xx pauses the account

17. In the Firestore console, edit `social_accounts/staging.token`: add `x` at the end of the value. Do not log the value.
18. Send `/post staging now Check four: forced failure`. Expect `<code> failed and the account is paused: ...`, an alert message that ends with `send /resume staging`, and `paused: true` on the account. Threads answers code 190 here.
19. Send `/token staging <valid token>`. Expect `Token saved for staging.`
20. Send `/resume staging`. Expect `@sellsnap.app is resumed.` Then `/post staging now Check four: recovered` publishes. Delete it with its Delete button.

## 8. Redirect `/go`

21. Take a published code from the previous steps. `/go` answers GET only (a HEAD request, such as `curl -I`, gets 404). In a terminal:

    ```bash
    curl -s -o /dev/null -D - -A 'Mozilla/5.0 (Linux; Android 14; Pixel 8)' "$URL/go/<code>"
    ```

    Expect `302`, `cache-control: no-store`, no `set-cookie`, and `location: https://play.google.com/store/apps/details?id=com.sirelon.sellsnap&referrer=utm_source%3Dthreads%26utm_medium%3Dsocial%26utm_campaign%3D...`.
22. Open `$URL/go/<code>` on an iPhone. It lands on the App Store page `apps.apple.com/app/id6776001373?ct=<code>`.
23. Open it in a desktop browser. It lands on `https://sirelon.github.io/SellSnap/`.
24. `curl -s -o /dev/null -D - "$URL/go/stt9999"` (an unknown code) goes to the landing page. Firestore `social_clicks` holds one document per step 21 to 23 and none for the unknown code or for the Threads link-preview bot.
25. Cloud Logging has no request log for `/go`: `gcloud logging read 'resource.type="cloud_run_revision" AND httpRequest.requestUrl:"/go/"' --freshness=1h --limit=3 --project sellsnap-6e85c` prints nothing.

## 9. Clean up

26. Delete every test post still on `@sellsnap.app` with its Delete button, then send `/approval staging`. The seed tips stay queued.
