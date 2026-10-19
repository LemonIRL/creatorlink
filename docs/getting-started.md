# Getting Started

## 1. Install

Put these two files in your `mods` folder (Fabric Loader, Minecraft 26.2):

- `creatorlink-<version>.jar`
- `fabric-api-<version>+26.2.jar` — [download from Modrinth](https://modrinth.com/mod/fabric-api/versions)

CreatorLink runs on **your client** (the streamer's game). You only need it on
the server too if you want commands like mob spawning to work in multiplayer —
see [Server Setup](server-setup.md).

Launch the game once. This creates:

```
config/creatorlink/
  creatorlink.toml     ← your settings and tokens
  default/             ← your event configs (JSON) go here
```

## 2. Connect your Twitch account

Open `config/creatorlink/creatorlink.toml` in any text editor.

### Twitch chat (no account needed)

```toml
[socket]
	twitchChats = ["yourchannelname"]   # lowercase!
```

That's enough for chat messages to show up as toasts in-game.

### Twitch events (channel points, subs, follows, cheers)

You need an OAuth token. Generate one at
**[twitchtokengenerator.com](https://twitchtokengenerator.com/quick/l7oG2vcNOE)**
(that link pre-selects every scope CreatorLink needs, including channel point
redemptions). Log in with the **streamer's** Twitch account, then copy both
tokens into the config:

```toml
[socket]
	eventsubOauthTokens = ["your_access_token"]
	eventsubRefreshTokens = ["your_refresh_token"]
	# leave eventsubClientIDs = [] — the generator's ID is used automatically
```

With the refresh token in place, CreatorLink renews the access token by itself
when it expires.

### StreamElements / Streamlabs / DonationAlerts (optional)

```toml
[socket]
	streamElementsTokens = ["JWT token"]   # SE Dashboard → your profile → Show secrets
	streamlabsTokens = ["socket API token"] # SL Dashboard → Settings → API Settings
	donationAlertsTokens = ["widget token"]
```

### YouTube live chat (optional)

Needs a Google API key ([instructions](https://developers.google.com/youtube/v3/live/getting-started))
and your channel ID (the `UC...` part of your channel URL):

```toml
[socket]
	youtubeApiKeys = ["your-google-api-key"]
	youtubeChannelIds = ["UCxxxxxxxxxxxxxxxx"]
```

YouTube only connects while you are actually live.

While connected you also get, beyond chat messages: **membership events**
(`newSponsorEvent`, `memberMilestoneChatEvent`, `superChatEvent`, ... — set
up "YouTube member" in `/creatorlink simple` to spawn something for new
members) and a **like counter** — checked once a minute, firing a `likes`
event with `$likes` and `$newLikes` when the count rises ("YouTube likes"
in Simple, with a "one per like" multiplier). YouTube's API doesn't say
*who* liked or subscribed, so likes are anonymous and free channel
subscriptions can't trigger per-person events — memberships (paid) can.

### Your streamer name

```toml
[general]
	streamerName = "YourMinecraftName"
```

This sets the `$streamer` variable used in configs. Leave it empty to use your
Minecraft name.

## 3. Test it

1. Start Minecraft and join a world.
2. Run **`/creatorlink connections`** — each configured service shows
   **CONNECTED** (green), CONNECTING (yellow), or ERROR (red), with
   Disconnect/Reconnect buttons next to each one. (`/creatorlink status`
   prints the same list in chat.)
3. Type something in your Twitch chat. With the default-style `chat.json`
   config, it pops up as a toast in the top-right corner.

Edited tokens while the game is running? Run **`/creatorlink reconnect`**.

## In-game commands

| Command | Does |
|---|---|
| `/creatorlink` | Open the visual config editor (also: button on the title screen) |
| `/creatorlink simple` | Guided setup: rewards, follows/raids/cheers/subs (with source routing), chat display — writes the config for you |
| `/creatorlink connections` | Live connection panel: per-service status + Connect/Disconnect/Reconnect buttons |
| `/creatorlink status` | Print every connection and its state in chat |
| `/creatorlink settings` | Edit creatorlink.toml in-game (tokens masked until "Show secrets") |
| `/creatorlink editor` | Open the visual config editor |
| `/creatorlink events` | Open the Event Viewer (recent events + their variables + replay) |
| `/creatorlink reconnect` | Re-read the toml and restart all connections |
| `/creatorlink reload` | Re-read the event configs in your profile |
| `/creatorlink profile <name>` | Switch to a different profile folder |
| `/creatorlink importcci [path]` | Import CCI configs: no path = from `config/contentcreatorintegration/`, or give a file/folder path |

## Migrating from CCI

If `config/contentcreatorintegration/` exists (from a previous CCI install)
and CreatorLink has never been configured, your first launch copies everything
over automatically: tokens and settings from `contentcreatorintegration.toml`,
plus every profile's JSON configs and notes. Check the log for
`Migrated settings from contentcreatorintegration.toml`.

Already ran CreatorLink, or got CCI configs from someone else? Two options,
no conversion needed (the formats are identical):

- **Drag & drop** the `.json` files — or a whole CCI profile folder, the
  entire `contentcreatorintegration` folder, or a **.zip** of it — onto the
  CreatorLink editor or Simple screen. (`.rar` isn't supported — extract it
  first or re-pack as .zip.) Files go into your active profile (a full install imports
  every profile), configs reload immediately, and a toast summarizes what
  was imported. Files named anything (`my-rewards.json`) are stored under
  the socket their `type` field names, and renamed copies like
  `streamelements (1).json` snap back to the right socket automatically.
  Old CCI files that use underscore-prefixed keys (`_configs`, `_outcomes`)
  load fine too.
- **`/creatorlink importcci`** re-runs the profile migration from
  `config/contentcreatorintegration/` at any time; add a path
  (`/creatorlink importcci C:\backups\cci`) to import from anywhere else.

Both routes also merge tokens and API keys from the CCI toml: values you
don't already have are added (your existing tokens are never overwritten),
and sockets restart automatically. You can also drop a
`contentcreatorintegration.toml` on its own to import just the tokens.

Next: [Event Configs](event-configs.md) — making events actually *do* things.
