# Google and LinkedIn login

The "Continue with Google" and "Continue with LinkedIn" buttons only appear once you give the app a
client ID and secret for that provider. You can switch on one, both or neither.

For the real event, create these under an account the organisers own, not a personal one, so they
don't disappear if a volunteer leaves.

## The callback address

Both providers ask for an "authorized redirect URL". For this app it is always:

```
<APP_PUBLIC_BASE_URL>/api/v1/auth/oauth/google/callback
<APP_PUBLIC_BASE_URL>/api/v1/auth/oauth/linkedin/callback
```

For example, if `APP_PUBLIC_BASE_URL=https://abc-def.trycloudflare.com`, the Google one is
`https://abc-def.trycloudflare.com/api/v1/auth/oauth/google/callback`. It must match exactly:
same `https`, no slash at the end.

On your own laptop you can also add `http://localhost:8080/api/v1/auth/oauth/google/callback`.

## Google

1. Go to https://console.cloud.google.com and create a project (e.g. "IT Limerick Summit").
2. Open **Google Auth Platform** (called "OAuth consent screen" in older menus) and fill in the app name and
   support email. Audience: **External**.
3. While the app is in **Testing**, only people you add under **Audience → Test users** can log in.
   Add your friends' Gmail addresses there. Google limits how many test users you can add, so check
   the number in the console.
4. Go to **Clients → Create client**, choose **Web application**, and add the callback address under
   **Authorized redirect URIs**. Leave "Authorized JavaScript origins" empty, because this app doesn't need it.
5. Copy the client ID and secret into `.env`:
   ```
   GOOGLE_CLIENT_ID=....apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=...
   ```
6. Restart: `docker compose up -d --build`.

Before the real event, the organisers publish the app (switch it from Testing to In production) so
anyone can log in. Google may ask them to verify the app's branding first. Allow time for that, and
check Google's current rules when you get there.

## LinkedIn

1. Go to https://www.linkedin.com/developers/apps and **Create app**. LinkedIn needs a LinkedIn
   company page to attach the app to, and you can't change it later, so use the IT Limerick page for the real event.
2. In the **Products** tab, add **Sign In with LinkedIn using OpenID Connect**. Don't pick the old
   "Sign In with LinkedIn" product.
3. In the **Auth** tab, under **Authorized redirect URLs for your app**, add the callback address.
4. Copy the client ID and **Primary Client Secret** into `.env`:
   ```
   LINKEDIN_CLIENT_ID=...
   LINKEDIN_CLIENT_SECRET=...
   ```
5. Restart: `docker compose up -d --build`.

## How it behaves

- **New person:** an account is created from the name and email the provider shares. The login page
  tells them that continuing means accepting the privacy notice, and this is recorded like a normal
  sign-up. Marketing emails stay off.
- **Same email as an existing account:** they're logged into that account, because the provider has
  confirmed they own the email.
- **Account page:** people can connect or disconnect Google/LinkedIn and set a password. The app
  won't let someone disconnect their only way of logging in.
- **Stored data:** we keep only the provider's id for the person. Google/LinkedIn tokens are used
  once and thrown away.

## If it fails

The user lands back on the login page with a plain-English message. The API log has the detail:

| What you see | Usual cause |
|---|---|
| `redirect_uri_mismatch` (Google) or "redirect_uri does not match" (LinkedIn) | The callback address in the console doesn't exactly match `APP_PUBLIC_BASE_URL` + the path above. A tunnel link changes every time you restart it. |
| Google says "access blocked" or the app isn't verified | The person isn't on the test users list. |
| LinkedIn shows a scope error | The OpenID Connect product isn't added to the app. |
| "That sign-in took too long…" | They took over 10 minutes, or started on one device or browser and finished on another. |
