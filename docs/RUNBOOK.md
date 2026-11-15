# Event-day runbook

Keep this open on event day. Print a copy too.

## Before the day

- [ ] Content freeze: agenda, speakers and partners final at least 3 days before.
- [ ] Every speaker profile approved and set to "Show on the public site".
- [ ] At least two organisers can log in to the admin panel. Test it.
- [ ] The hosting company has confirmed backups are running and the load test passed.
- [ ] QR codes to the app are on screens, badges or posters.
- [ ] A printed agenda is available in case the app or Wi-Fi fails.
- [ ] No code changes are deployed in the 48 hours before the event, except emergency fixes.

## Contacts

| Role | Name | Phone |
| --- | --- | --- |
| App developer | [name] | [phone] |
| Backup developer / admin | [name] | [phone] |
| Hosting company on-call | [company] | [phone] |
| Organiser lead | [name] | [phone] |

## Common situations

**A session moves room or time.**
1. Admin panel → event → Agenda → Edit the session → change time or room → Save.
2. Then Updates → Add update → e.g. "Panel moved to the Main Hall", Priority: Urgent. Urgent updates show as a banner on the agenda for 3 hours. Phones pick it up within about 30 seconds.

**A session is cancelled.**
Edit it and set Status to "Cancelled". It stays on the agenda, clearly marked. Post an update too.

**Someone can't log in.**
They don't need to: the agenda and bookmarks work without an account. If password-reset email is set up, they can use "Forgot your password?" on the login page.

**The app is slow or down.**
1. Check the health page: `https://<your-domain>/actuator/health` should say `UP`.
2. Call the hosting company.
3. Phones that opened the app earlier still show the last saved agenda.
4. Point people to the printed agenda and announce changes from the stage.

**A bad update was posted.**
Admin panel → Updates → Delete. It disappears from phones within about 30 seconds.

## After the event

- [ ] Unpublish or keep the event, as the organisers prefer.
- [ ] Make sure the agreed data retention is set (see HANDOVER.md, GDPR).
- [ ] Note what to improve for next year.
