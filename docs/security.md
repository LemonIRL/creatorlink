# Security and your tokens

CreatorLink needs credentials to listen to your stream: Twitch OAuth tokens,
a StreamElements JWT, Streamlabs / DonationAlerts tokens, a YouTube API key.
Treat every one of them like a password.

## Where they live

All tokens are stored **in plain text** in `config/creatorlink/creatorlink.toml`
on your own computer. They are only ever sent to the service they belong to
(Twitch, StreamElements, Streamlabs, DonationAlerts, YouTube). They are **not**
sent to a Minecraft server: servers only receive the commands and outcomes your
event configs produce.

CreatorLink does not write token values to the game log. On systems with POSIX file
permissions, `creatorlink.toml` is saved readable by you only.

Anything that can read your game folder can read that file, including other
mods in the same instance. Only install mods you trust.

## Sharing configs without sharing tokens

Your event configs live in profile folders (`config/creatorlink/default/`,
...). Your tokens live one level up, in `creatorlink.toml`.

- **Share a profile folder, not the whole `config/creatorlink` folder.**
- Never zip or upload `creatorlink.toml` (or an old
  `contentcreatorintegration.toml`) to share it.
- Never post screenshots of the settings screen with **Show secrets** on.
  Do not stream with it open.
- If a token ever leaves your machine, revoke it and generate a new one.

## Importing configs from other people

If a dropped file, folder, or `.zip` contains a toml with tokens or channels,
CreatorLink **asks before adding them**, and shows how many it found. Only
say yes to a config that is your own or from someone you trust: adding another
person's stream account means their events can trigger outcomes in your game.
Choosing "Skip" imports the event configs and leaves your tokens alone.

Unzipped imports are deleted from the temp folder as soon as the import
finishes.

## Twitch token refresh

Twitch EventSub tokens are renewed through
[twitchtokengenerator.com](https://twitchtokengenerator.com), the same service
you use to generate them. Your refresh token is sent to that site to get a
new access token, so it is a service you are trusting. If you would rather not,
leave the refresh token field empty and generate a fresh access token when the
current one expires.

## Keep tokens as narrow as possible

- Twitch: only the scopes your events need.
- YouTube: create a key restricted to the **YouTube Data API v3**.
- Use a separate token per service, and rotate them occasionally.

## Viewer-controlled text

Event variables such as `$message` or `$username` are written by your viewers.
Commands run with your player's permissions, so avoid putting viewer-controlled
text into commands that can do damage, and test new rewards with the Event
Viewer replay first.

## Reporting a problem

Open an issue at <https://github.com/LemonIRL/creatorlink/issues>. **Never
include real tokens** in an issue or log: replace them with `REDACTED`.
