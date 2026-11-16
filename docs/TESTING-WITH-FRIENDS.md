# Testing with friends on their own phones

The app runs on your laptop, and a free Cloudflare "quick tunnel" gives it a temporary public
**https** link. Your friends open that link on their phones. The https part matters: phone browsers
only allow the camera (QR scanning) and Google/LinkedIn login on https.

Your laptop has to stay on and awake for the whole test. When you close the tunnel the link stops
working, and the next time you start it you get a **different** link.

## 1. One-off setup (Windows)

```powershell
winget install --id Cloudflare.cloudflared
```

Close and reopen PowerShell afterwards so it finds `cloudflared`.

## 2. Start the tunnel and copy the link

Make sure Docker Desktop is running, then in a PowerShell window you leave open:

```powershell
cloudflared tunnel --url http://localhost:8080
```

After a few seconds it prints a box with a link like `https://some-random-words.trycloudflare.com`.
Copy it.

## 3. Point the app at that link

Edit `.env` in the project folder (`notepad .env`) and set:

```
APP_PUBLIC_BASE_URL=https://some-random-words.trycloudflare.com
COOKIE_SECURE=true
APP_ADMIN_EMAILS=your-own-email@example.com
APP_SEED_DEMO_DATA=true
APP_SEED_DEMO_DATE=today
EMAIL_LOG_CONTENT=true
```

- `APP_SEED_DEMO_DATE=today` puts the sample agenda on today's date. That way "On now", session
  ratings and voting all have something to show while you test. It only fills an **empty** database.
  To start over, run `docker compose down -v` first. This wipes all test data.
- `EMAIL_LOG_CONTENT=true` means "forgot password" emails go to the logs instead of being sent.
  If a friend needs a reset, run `docker compose logs api` and send them the link. **Set it back to
  false** before the real event.
- For Google/LinkedIn buttons, also follow `docs/SOCIAL-LOGIN.md` and register
  `https://some-random-words.trycloudflare.com/api/v1/auth/oauth/google/callback` (and the LinkedIn
  one). You have to update this every time the tunnel link changes. Skip it, and the buttons
  simply don't show up. Email login always works.

## 4. Start the app

In a second PowerShell window, in the project folder:

```powershell
docker compose up -d --build
```

Open the https link yourself first, and log in or register with the email you put in
`APP_ADMIN_EMAILS`. That makes you an organiser. Use the https link and not localhost, because
`COOKIE_SECURE=true` only works over https.

## 5. Give friends organiser access (optional)

If you want a friend to try scanning tickets or the team sorter: Admin panel → the sample event →
**Team** tab → add their email as **Organiser**. They must create their account first.

## 6. What to ask your friends to try

Send them the link and this list:

1. Create an account (or Continue with Google/LinkedIn if you switched it on).
2. Browse the agenda and save two sessions with the bookmark. Check they show under My schedule.
3. Open a session that has already started, give it stars and a comment.
4. Teams: either create a team and share the 6-character join code, join someone else's with their
   code, or tap "Find me a team" and wait for the organiser to sort people.
5. Vote for a team (not your own). It's open during the day on the sample event.
6. Updates → Chat: send a message, and react to an announcement.
7. More → My ticket: show the QR code to the organiser to scan.
8. Account: download your data, then (at the end) delete your account.

Ask them to send you a screenshot and the phone model whenever something looks wrong.

## 7. What you do as the organiser during the test

- **Before you start:** the sample event's voting runs 09:00–16:30 on the sample day. If you're testing
  outside those hours, change "Voting opens" and "Voting closes" in **Admin → Event details**.

- **Admin → Updates:** post an announcement and watch it appear on their phones. Mark one urgent
  to see the banner.
- **Admin → Hackathon:** click "Sort people into teams" once a few people are waiting.
- **Admin → Check-in:** tap "Start camera", allow the camera, and scan their tickets. Then try a
  typed code and "check in by email". Rescanning the same person should say "already checked in".
- **Admin → Feedback:** see the ratings come in, with no names.

## 8. When you're done

- Press `Ctrl+C` in the tunnel window. The link stops working straight away.
- `docker compose down` stops the app and keeps the data. `docker compose down -v` deletes it all.
- Set `EMAIL_LOG_CONTENT=false` and `APP_SEED_DEMO_DATA=false` again.

## Limits to know about

- Quick tunnels are Cloudflare's testing tool. Cloudflare says not to use them for production,
  and they limit how many requests can run at once. Fine for a group of friends; the real event
  runs on the hosting company's servers.
- The laptop going to sleep ends the tunnel. Plug it in and turn off sleep for the session.
- Google only lets people on your **test users** list log in until the organisers publish the
  Google app (see `docs/SOCIAL-LOGIN.md`).
