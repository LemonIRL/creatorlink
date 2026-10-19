# CreatorLink — Complete Guide

Twitch and YouTube events → things happening in Minecraft. A viewer redeems
channel points and a zombie in enchanted armor drops next to you; a raid
spawns one mob per raider; a resub spawns one per month subscribed. If you
used **CCI (Content Creator Integration)** before, CreatorLink is a drop-in
replacement for Minecraft 26.2 — your old configs import as-is.

- **Minecraft:** 26.2 (Fabric)
- **Requires:** Fabric Loader, Fabric API, Java 25
- **License:** MIT

---

## 1. Install (client)

1. Drop `creatorlink-<version>.jar` and Fabric API into your `mods` folder.
2. Launch once — it creates `config/creatorlink/creatorlink.toml`.
3. If you had CCI installed, **everything migrates automatically on first
   launch**: tokens, settings, and all your profiles. Skip to section 3.

## 2. Connect your stream

Open `/creatorlink settings` in-game (tokens are masked until you press
*Show secrets*), or edit `config/creatorlink/creatorlink.toml`:

| Setting | What it is |
|---|---|
| `twitchChats` | Twitch channel name(s) to read chat from — lowercase, no `#` |
| `eventsubOauthTokens` + `eventsubRefreshTokens` | Twitch EventSub — follows, raids, cheers, subs, **channel point redemptions**. Generate both at [twitchtokengenerator.com](https://twitchtokengenerator.com/quick/l7oG2vcNOE) |
| `streamElementsTokens` | StreamElements JWT: SE Dashboard → your profile (top right) → Show secrets |
| `streamlabsTokens` | Streamlabs socket API token |
| `donationAlertsTokens` | DonationAlerts token |
| `youtubeApiKeys` + `youtubeChannelIds` | YouTube live chat + members + likes (see section 7) |
| `streamerName` | Only if your Twitch name differs from your Minecraft name |

Hit **Save + Reconnect**, then check **`/creatorlink connections`** — every
service shows a live status (green CONNECTED / yellow CONNECTING / red
ERROR) with per-service **Connect / Disconnect / Reconnect** buttons.

> **Note:** channel point redemptions only arrive via **Twitch EventSub** —
> StreamElements/Streamlabs don't carry them (SE has its own
> `channelPointsRedemption` events if your config came from CCI).

## 3. In-game commands

| Command | Does |
|---|---|
| `/creatorlink` | Open the visual config editor (also: title-screen button) |
| `/creatorlink simple` | Guided setup — rewards and stream events, no JSON |
| `/creatorlink connections` | Live connection panel with per-service buttons |
| `/creatorlink status` | Same info, printed in chat |
| `/creatorlink settings` | Edit the toml in-game |
| `/creatorlink events` | Event Viewer — recent events, their variables, Replay |
| `/creatorlink editor` | Full editor |
| `/creatorlink reconnect` | Re-read the toml and restart all connections |
| `/creatorlink reload` | Re-read the event configs in your profile |
| `/creatorlink profile <name>` | Switch profile folder |
| `/creatorlink importcci [path]` | Import a CCI install (see section 5) |
| `/creatorlink whitelist <player>` | (Server) allow a streamer's command outcomes |

## 4. Simple mode — `/creatorlink simple`

The no-JSON way to set up everything:

**Channel point rewards.** Type the reward title (must match Twitch
exactly), choose the **Source** (Twitch EventSub or StreamElements — saving
removes the same reward from the other source so it can't double-fire), then
pick an action:

- **Spawn mob** — searchable mob picker; how many; **spawn as baby**;
  weapon + per-slot armor, each with its own **Enchants…** menu (stack
  multiple enchants with levels; contradictory ones like Protection vs
  Blast Protection gray out, same rules as an anvil); name the mob after
  the redeemer; turn off drops; **no despawning** (persistent until killed).
- **Potion effect** — effect, duration, level.
- **Custom command** — anything, with `$variables`.
- **Chat message.**

Plus an optional "announce in chat" line. Existing rewards are listed with
Edit/Delete — including ones imported from CCI (StreamElements rewards are
tagged **[SE]**; anything Simple can't fully parse gets an *Editor* button
that jumps to that exact event in the full editor).

**Stream events.** Follow, Raid, Cheer, Subscription, Resub — each with a
Source choice — plus **YouTube member** and **YouTube likes**. Multipliers
where they make sense:

- Raid → **one per raider** · Resub → **one per month** · Cheer → **per X
  bits** · YouTube likes → **one per like**
- Every multiplier has a **Max repeats** cap — set it (e.g. 50) so a
  2,000-viewer raid doesn't summon 2,000 mobs.

**Chat display.** Twitch and YouTube chat as toasts, chat messages, both,
or off — with **name color** and **message color** pickers per platform.

## 5. Coming from CCI?

The config format is identical — no conversion, ever.

- **Same machine:** first launch migrates everything automatically.
- **Any other time:** **drag & drop** your CCI `.json` files, a profile
  folder, the whole `contentcreatorintegration` folder, **or a .zip of it**
  onto the CreatorLink editor or Simple screen. Configs land in your active
  profile, tokens/API keys are merged in (your existing ones are never
  overwritten), and sockets restart. `.rar` isn't supported — extract it
  first. Renamed files like `streamelements (1).json` snap back to the
  right socket automatically; old underscore-style CCI files load fine.
- Or run `/creatorlink importcci` (reads `config/contentcreatorintegration/`)
  — add a path to import from anywhere else.

**One thing to check after importing:** commands written for 1.21.5 or older
may use NBT Minecraft has since changed (`ArmorItems`/`HandItems`, item
`Enchantments`/`tag:{}`, JSON-string `CustomName`). The full editor flags
these — the outcome shows **⚠ old-version command** in the tree and the
command box turns red, with the fix in its tooltip. Re-saving the reward
from Simple mode regenerates it in current syntax.

## 6. Full editor — `/creatorlink editor`

CCI-style power editing: tabs for every socket config (twitcheventsub,
chat, streamelements, streamlabs, donationalerts, gamehooks — tabs exist
even before the file does; Save creates it), navigator tree on the left,
fields on the right.

- **Double-click** a `conditions` or `outcomes` node (or press *+ Add…*) for
  the full searchable list of every condition/outcome type
  ("chat — ChatOutcome", "note — NoteOutcome", ...).
- Reorder/delete with the ↑ ↓ Delete buttons; **Save applies immediately**.
- Closing with unsaved edits asks Save or Discard, so the file always
  matches what you saw.
- YouTube chat is configured inside **chat.json** under platform `youtube`
  (there is no separate youtubechat file).
- Game hooks (`joinWorld`, `death`, `attackBlock`, ...) live in
  gamehooks.json and only fire when `enableGameHooks` is on in Settings.

## 7. YouTube

Needs a free Google API key: [console.cloud.google.com](https://console.cloud.google.com)
→ create a project → enable **YouTube Data API v3** → Credentials → API key.
Your **channel ID** is the 24-character `UC…` string from YouTube Studio →
Settings → Channel → Advanced settings (not your @handle).

- Connects **only while you're live** (offline = "No active live stream
  found" in the connections panel — normal; hit Reconnect after going live).
- You get: **chat messages**, **membership events** (new members, milestones,
  super chats), and a **like counter** checked once a minute (`$likes`,
  `$newLikes`).
- Limits from YouTube's side: likes are anonymous (no name), and free
  channel subscriptions can't trigger per-person events — memberships can.
- The free API quota (10,000 units/day) is plenty for streaming; just don't
  let it hammer reconnects while offline.

## 8. Playing on a server

Toasts, chat, and sounds work with the mod on your client only. **Command
outcomes** (spawning mobs, effects) run on the server:

1. Put the same jar + Fabric API in the server's `mods` folder.
2. An op runs `/creatorlink whitelist <TheStreamerName>` (works from a
   modded client too).

**Being op is not the same as being whitelisted** — outcomes run with full
command permissions and are triggered by viewers, so the server owner
explicitly lists who's allowed. Without the whitelist you'll see
"[CreatorLink] You are not whitelisted on this server." when a reward fires
(chat messages still show — they're client-side).

With CreatorLink on the server, commands can be ~32,000 characters (big
NBT-heavy summons are fine). Without it, commands fall back to chat, which
caps at 256 characters — big loadouts won't fit, and you'll get a toast
telling you so.

**Server owner tip:** set `disallowedCommands` in `creatorlink-common.toml`
to at least `["stop","op","deop","ban","ban-ip","whitelist"]`.

## 9. When something doesn't work

1. **`/creatorlink connections`** — is the service green?
2. **`/creatorlink events`** — did the event arrive at all? What variables
   does it carry? (You can Replay any event while testing.)
3. Chat works but mobs don't spawn on a server → **whitelist** (section 8).
4. Imported reward fires but the mob is wrong/naked → look for the red
   **⚠ old-version command** flag in the full editor.
5. TwitchEventSub ERROR "token invalid" → regenerate both tokens at the
   link in section 2, paste into Settings, Save + Reconnect.
6. Everything DISCONNECTED after retries → it gave up after
   `maxAutomaticReconnects`; fix the cause, then hit Reconnect in the
   connections panel.
7. Raw event payloads: set `logTypes = ["socket_event"]` in the toml —
   they're written to `config/creatorlink/logs/<date>.log`.

Have fun, and cap your raid multipliers. 🧟
