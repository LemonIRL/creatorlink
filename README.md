# CreatorLink

Stream events → in-game outcomes for **Minecraft 26.2 (Fabric)**. Twitch channel
point redemptions spawn mobs, subs trigger commands, chat pops toasts — all
driven by JSON event configurations.

CreatorLink listens to your stream services and turns their events into
in-game outcomes: spawn mobs, run commands, show toasts and popups, play
sounds. Configure it in the built-in editor or the simple setup screen — no
hand-written JSON required. It is open source under the MIT license.

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
   your tokens:
   - `twitchChats` — channel name(s), lowercase
   - `eventsubOauthTokens` / `eventsubRefreshTokens` — from
     [twitchtokengenerator.com](https://twitchtokengenerator.com/quick/l7oG2vcNOE)
   - `streamElementsTokens` (JWT), `streamlabsTokens`, `donationAlertsTokens`
   - `youtubeApiKeys` + `youtubeChannelIds` (`googleApiKey`/`youtubeChats` are also accepted)
3. Put event configs in `config/creatorlink/default/` —
   `twitcheventsub.json`, `chat.json`, `streamelements.json`, `constants.json`,
   etc.
4. In game: `/creatorlink connections` opens the live connection panel
   (per-service status with Connect/Disconnect/Reconnect buttons),
   `/creatorlink status` prints the same in chat,
   `/creatorlink reconnect` restarts sockets after editing tokens,
   `/creatorlink reload` re-reads the profile, `/creatorlink profile <name>` switches profiles.

### Importing existing configs

If a `config/contentcreatorintegration/` folder exists and CreatorLink has no
config yet, the first launch **imports it automatically**: TOML settings
(tokens, streamer name) and every profile's JSON configs and notes are copied
over.

Any other time, **drag & drop** `.json` config files (or a whole profile
folder, or a `.zip` of it) onto the CreatorLink editor or Simple screen to
import them into the active profile. Tokens/API keys from a toml are merged in
too (never overwriting yours). `/creatorlink importcci [path]` does the same
from a command.

Notes:
- Condition/outcome fields use `type` / `source` / `target` (no leading
  underscores). Old `_type`-style configs are accepted too.
- Outcomes referencing other mods' entities (e.g. `miniatures:minime`) need
  those mods to exist on 26.2 — swap in vanilla mobs otherwise.

## Config format

The pipeline:
`Stream Event → Event (variables) → EventConfiguration → Configuration (for/types filter) → Config Event (conditions → outcomes)`

- Events are variable maps; reference them with `$name` (case-sensitive,
  recursive insertion, `variableInsertionMaxRecursion` honored).
- All documented condition types (string ops, arrays, variables, math, notes,
  random, message, user, amount/range, conditional/multiple/constant/repeat...)
  and outcome types (command, chat, toast, sound, repeat, note, conditional,
  multiple, event, writeToFile, readFromUrl, ...) are implemented.
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

CreatorLink exposes game events natively: create `gamehooks.json` in your profile and react to event
types `joinWorld`, `leaveWorld`, `death`, `respawn`, `attackBlock` (vars:
`$block`, `$x/$y/$z`), `useItem` (`$item`), `chatSent`/`commandSent`
(`$message`/`$command`).

### Not yet implemented (roadmap)

- TikTok chat (feasible via the community webcast API; deferred)
- First-run wizard (edit the toml instead)

## Building

Requires JDK 25. `gradlew build` — the jar lands in `build/libs/`.

## Acknowledgements

CreatorLink was inspired by Content Creator Integration by iChun, and can
import its configs. It is an independent project and is not affiliated with
or endorsed by iChun.

## License

MIT, see [LICENSE](LICENSE). Not affiliated with Twitch, YouTube,
StreamElements, Streamlabs, or DonationAlerts.
