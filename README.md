# CreatorLink

Stream events → in-game outcomes for **Minecraft 26.2 (Fabric)**. Twitch channel
point redemptions spawn mobs, subs trigger commands, chat pops toasts — all
driven by JSON event configurations.

CreatorLink is a **clean-room, MIT-licensed work-alike of Content Creator
Integration (CCI)** by iChun, which is closed-source and was last released for
1.21.5. It was built from CCI's MIT-licensed documentation and the public APIs
of the streaming services — no CCI code was decompiled or copied. **Existing CCI
configs load as-is** (same JSON format, same variable names, same TOML keys).

## Supported services

| Service | Transport | Events |
|---|---|---|
| Twitch EventSub | WebSocket + Helix | channel points, subs/resubs/gifts, follows, cheers, raids, polls, predictions, hype trains, goals, ads, chat, stream on/offline |
| Twitch Chat | IRC (anonymous) | messages, /me actions, usernotices; IRC tags (e.g. `custom-reward-id`) exposed as variables |
| StreamElements | Socket.IO (JWT) | follows, subs, cheers, tips, raids (+ test events) |
| Streamlabs | Socket.IO (token) | donations, follows, bits, subs, ... |
| DonationAlerts | Socket.IO (token) | donations |
| YouTube Live Chat | API polling (Google API key + channel id) | chat messages |

## Documentation

**User guide: [docs/](docs/README.md)** — [Getting Started](docs/getting-started.md) ·
[Event Configs](docs/event-configs.md) · [Recipes](docs/recipes.md) ·
[Server Setup](docs/server-setup.md) · [Troubleshooting](docs/troubleshooting.md)

## Setup

1. Install the mod (Fabric 26.2, Fabric API, Java 25) on the **client**.
   Install on the **server** too if you want server-side command outcomes
   (mob spawns, effects) — then whitelist the streamer:
   `/creatorlink whitelist <playerName>`.
2. Launch once to generate `config/creatorlink/creatorlink.toml`, then fill in
   your tokens (same keys as CCI):
   - `twitchChats` — channel name(s), lowercase
   - `eventsubOauthTokens` / `eventsubRefreshTokens` — from
     [twitchtokengenerator.com](https://twitchtokengenerator.com/quick/l7oG2vcNOE)
   - `streamElementsTokens` (JWT), `streamlabsTokens`, `donationAlertsTokens`
   - `youtubeApiKeys` + `youtubeChannelIds` (or CCI's `googleApiKey`/`youtubeChats`)
3. Put event configs in `config/creatorlink/default/` —
   `twitcheventsub.json`, `chat.json`, `streamelements.json`, `constants.json`,
   etc. Copied CCI configs work unchanged.
4. In game: `/creatorlink connections` opens the live connection panel
   (per-service status with Connect/Disconnect/Reconnect buttons),
   `/creatorlink status` prints the same in chat,
   `/creatorlink reconnect` restarts sockets after editing tokens,
   `/creatorlink reload` re-reads the profile, `/creatorlink profile <name>` switches profiles.

### Migrating from CCI

If `config/contentcreatorintegration/` exists and CreatorLink has no config yet,
the first launch **migrates automatically**: TOML settings (tokens, streamer
name) and every profile's JSON configs and notes are copied over.

Any other time, **drag & drop** CCI `.json` files (or a whole CCI folder, or
a `.zip` of it) onto the CreatorLink editor or Simple screen to import them
into the active profile — no conversion needed, the formats are identical, and tokens/API
keys from the CCI toml are merged in too (never overwriting yours).
`/creatorlink importcci [path]` does the same from a command.

Notes:
- Condition/outcome fields use `type` / `source` / `target` (no leading
  underscores) — same as real CCI 1.13. Old `_type`-style configs are accepted too.
- Outcomes referencing other mods' entities (e.g. `miniatures:minime`) need
  those mods to exist on 26.2 — swap in vanilla mobs otherwise.

## Config format

The pipeline, exactly as documented for CCI:
`Stream Event → Event (variables) → EventConfiguration → Configuration (for/types filter) → Config Event (conditions → outcomes)`

- Events are variable maps; reference them with `$name` (case-sensitive,
  recursive insertion, `variableInsertionMaxRecursion` honored).
- All documented condition types (string ops, arrays, variables, math, notes,
  random, message, user, amount/range, conditional/multiple/constant/repeat...)
  and outcome types (command, chat, toast, sound, repeat, note, conditional,
  multiple, event, writeToFile, readFromUrl, cci* ...) are implemented.
- Queues (`queue` + `playTime`), delays, cooldowns, `allowsOtherEventsToTrigger`
  short-circuiting, `singleOutcomeOnly`, outcome `weight`, the `all` event type,
  `init` events, constants, and notes (with expiry) all work as documented.

### In-game GUI

Everything is configurable without touching JSON (title-screen button or `/creatorlink`):

- **Simple mode** (`/creatorlink simple`) — reward manager: list/edit/delete
  channel point rewards with searchable mob, weapon and per-slot armor
  pickers, per-piece stackable enchants with anvil-style conflict rules,
  spawn-as-baby, no-drops and no-despawn toggles, effect/command/chat
  actions; a *Stream events* section for follows,
  raids, cheers, subs and resubs with a per-event **source** choice (Twitch
  EventSub vs StreamElements) and multipliers ("one per raider", "per X
  bits", "one per month") with an optional max-repeats cap; Twitch and
  YouTube chat display (toasts/chat/both/off) with name and message color
  pickers; Twitch **and** YouTube chat display modes (toasts/chat/both/off).
- **Full editor** (`/creatorlink editor`) — config tabs across the top,
  navigator tree, editable fields, type pickers for every condition/outcome,
  reorder/delete; Save applies to the running game immediately.
- **Settings** (`/creatorlink settings`) — edit creatorlink.toml in-game;
  tokens are masked until "Show secrets", Save + Reconnect restarts sockets.
- **Event Viewer** (`/creatorlink events`) — recent stream events with all
  their variables, plus Replay.
- **Connections** (`/creatorlink connections`) — live status panel: every
  socket (Twitch Chat, EventSub, StreamElements, ...) with a color-coded
  state that updates in real time, per-socket Connect/Disconnect and
  Reconnect buttons, and Reconnect All (re-reads the toml).

### Game hooks (native)

Instead of CCI's reflection-based expert system, CreatorLink exposes game
events natively: create `gamehooks.json` in your profile and react to event
types `joinWorld`, `leaveWorld`, `death`, `respawn`, `attackBlock` (vars:
`$block`, `$x/$y/$z`), `useItem` (`$item`), `chatSent`/`commandSent`
(`$message`/`$command`).

### Not yet implemented (roadmap)

- TikTok chat (feasible via the community webcast API; deferred)
- CCI's reflection-based gamehooks format (`objectAccessor`,
  `ParamInjector`) — replaced by the native gamehooks above
- First-run wizard (edit the toml instead)

## Building

Requires JDK 25. `gradlew build` — the jar lands in `build/libs/`.

## Credits

- iChun — for CCI's design and its MIT-licensed documentation
  (`reference/cci-docs/`), which served as the specification for this
  reimplementation. If you're on Minecraft ≤ 1.21.5, use the original
  [Content Creator Integration](https://modrinth.com/mod/content-creator-integration).
- Licensed MIT. Not affiliated with iChun, Twitch, YouTube, StreamElements,
  Streamlabs, or DonationAlerts.
