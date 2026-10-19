# CreatorLink — Specification & Roadmap

A clean-room, MIT-licensed Fabric mod for **Minecraft 26.2** that reimplements the
functionality and JSON config format of iChun's Content Creator Integration (CCI),
which is closed-source/ARR and last released for 1.21.5 (v1.13.0).

**Legal basis:** built solely from the MIT-licensed official documentation
(`reference/cci-docs/`, cloned from iChun/ContentCreatorIntegration-IssuesAndDocumentation)
plus public API docs for Twitch/YouTube/Streamlabs/StreamElements/DonationAlerts.
No CCI code was decompiled or copied. Original name/branding (CreatorLink, modid
`creatorlink`) — CCI's name, icon, and assets are not reused.

**Compatibility goal:** existing CCI profile configs (`twitcheventsub.json`,
`chat.json`, `streamelements.json`, `constants.json`, …) load unmodified.

---

## Toolchain

- Minecraft 26.2 (Mojang year-based versioning), **Java 25** (Temurin;
  point `JAVA_HOME` at it)
- Fabric Loader 0.19.3, Fabric API 0.154.2+26.2, Loom 1.17 (default = Mojang mappings), Gradle 9.5.1
- 26.2 gotchas: experimental Vulkan backend (use Blaze3D/GuiGraphics APIs only, no raw GL);
  screen management moved off `Minecraft` to `gui`/`hud` classes; block/item ID registry split.
- Bundled deps (jar-in-jar): night-config core+toml only. All networking uses
  `java.net.http` (HttpClient + WebSocket) — no OkHttp/Socket.IO-client bundling;
  we implement a thin Engine.IO/Socket.IO (v3+v4) client ourselves.

## Architecture (mirrors documented CCI behavior)

**Client does the heavy lifting.** Sockets, event configs, conditions, and most outcomes
run client-side. The server component only: whitelists users, executes long/permission
commands sent by whitelisted clients, blocks `disallowedCommands`, and provides
`/creatorlink whitelist|dewhitelist|setBlacklist` (alias `/cci`-style; permission via
`commandPermissionLevel`).

**Processing pipeline:**
`Stream Event → Event (variables map) → EventConfiguration (per socket; chat shared) → Configuration (filter by "for" account + type; queue/ignoredTypes) → Config Event (conditions → outcomes; top-to-bottom; short-circuit unless allowsOtherEventsToTrigger)`

**Event = variables.** Key→value map referenced as `$name` (case-sensitive).
Global variables persist: `streamer`, `playerName`, `mcVersion`, + user-set globals.
`cci-type-*` reserved variables (e.g. `cci-type-event`). Variable insertion applies
`$var` substitution in nearly every string field. Notes = persistent key→value store
saved to disk (`notes` system). Constants = reusable Config Events referenced by
`constantName` from `constants.json`.

**Config objects** (field names exactly as CCI serializes them — `type` discriminator,
NOT `_type`; Configuration's account filter serializes as `for`):

- `EventConfiguration`: `configs[]`, `init[]` (events run on load), `type`, `from`
- `Configuration`: `types{String → Event[]}`, `queue[]`, `ignoredTypes[]`, `for`
- `Event`: `name`, `constantName`, `disabled`, `conditions[]`, `outcomes[]`,
  `triggersFromAnyConditionMet`, `allowsOtherEventsToTrigger`, `singleOutcomeOnly`,
  `playTime` (ticks; -1 skips queue), `playTimeEvent` (gets `$currentPlayTime`/`$totalPlayTime`),
  `delay`, `cooldown`, `disableShortCircuitWhenOnCooldown`
- `Condition` base: `type`, `displayName`, `inverseMatch`. No conditions = met.
- `Outcome` base: `type`, `displayName`, `weight`, `disabled`

## Component inventory (each has a spec page in reference/cci-docs/docs/components/)

**Conditions** — conditional: Condition, ConditionalCondition, ConstantCondition,
GameSettings, Inventory, JsonSafe, Message, ModExists, MultipleCondition, Note,
NotesList, PlayerState, Random, RangedAmount, ReadFile, Server, SpecificAmount,
SpecificUser, Statistics, Target, TargetExists, TwoHighSpace, UUIDToNBT;
array: Array, ArrayAppend, ArrayCombine, ArrayDelete, ArrayExplode, ArrayLength;
string: StringContains, StringEndsWith, StringEquals, StringStartsWith, StringSubString;
string-unconditional: Base64Decode, Base64Encode, StringCompareTo, StringConcat,
StringIndexOf, StringLength, StringLowerCase, StringReplace, StringSplit, StringTrim,
StringUpperCase; unconditional: Arithmetic (the `math` type), LocalisedString, LogArgs,
Null, RandomNumber, RandomString, Repeat, RequestStatistics;
variable: VariableCondition (`variableCheck`), VariableExists, VariableIsArray,
VariableIsNumber, VariableInsert, VariableRemove.

**Outcomes** — ChatMessage, Command (`executeAsSelf`; server path for >256 chars),
Conditional, Constant, Event, FakeCrash, Food, GameCrash, GameSettings, Health, IMC,
Input, Inventory, KeyPress, Motion, Mount, MultipleOutcome, Narrator, Note,
NoteDeletion, Null, ObjectAccessor, Popup, Prompt, ReadFromUrl, Repeat, Sound, Toast,
WriteToFile; cci-namespace: ChangeProfile, ClearQueue, FromJson, SendEventToSocket,
SendToPlayer.

**Game hooks (expert tier):** GameEventConfig, Listener, ObjectAccessor, ParamInjector —
reflection-driven hooks into game events. Lowest priority; document-only until core is done.

## Sockets

| Socket | Transport | Notes |
|---|---|---|
| Twitch Chat (IRC) | WSS `irc-ws.chat.twitch.tv` | anonymous or token; shared "chat" EventConfiguration with YouTube; channel-point rewards surface as `message` with `custom-reward-id` variable |
| Twitch EventSub | WSS `eventsub.wss.twitch.tv/ws` + Helix REST | replaces dead PubSub (shut down Apr 2025). OAuth access+refresh tokens (`eventsubOauthTokens`/`eventsubRefreshTokens` in TOML; twitchtokengenerator client-id default). Channel points, subs, follows, cheers, raids, channel.chat, hype train, polls, predictions |
| Streamlabs | Socket.IO | event type ids: `bits`, `follow`, `donation`, … |
| StreamElements | Socket.IO (JWT) | different ids: `cheer`, `follower`, …; configs NOT interchangeable with Streamlabs |
| DonationAlerts | Socket.IO | tips |
| YouTube Chat | REST polling | requires Google API key config |

## Mod config (TOML, night-config)

Client `creatorlink.toml` (accept CCI's `contentcreatorintegration.toml` keys):
`[general]` allowOutcomesRequiringServerWait, defaultProfile, enableInformationToasts,
firstRun, logTypes (cci|debug|event|socket_status|socket_event), maxAutomaticReconnects,
maxEventCache, onlineConfigs, overrideToastGui, stats, streamerName.
`[socket]` per-socket key lists: twitchChats, eventsubOauthTokens, eventsubRefreshTokens,
eventsubClientIDs, streamlabsSocketTokens, streamelementsJWTTokens, donationAlertsTokens,
youtubeChatKeys, googleApiKey.

Server `creatorlink-common.toml`: whitelistedUsers, enableBlacklist,
commandPermissionLevel, disallowedCommands, logTypes.

Profiles: `config/creatorlink/<profile>/{chat,twitcheventsub,streamlabs,streamelements,donationalerts,youtubechat,constants}.json` + notes storage.
Migration helper: detect `config/contentcreatorintegration/` and offer copy.

## Known CCI quirks to preserve (from Limitless SkyBlock production use)

- Condition fields are `type`/`source`/`target` — the old docs' `_type` style must ALSO
  parse (accept both; underscore style crashed real CCI, we can just accept both).
- `executeAsSelf: true` on command outcomes runs as the player (needs whitelist/op).
- Empty `"message": []` in a types map disables that event type's handling.
- Toast `titleColor` caused silent failures in CCI — support it properly.
- Repeat outcome: `times` accepts `$variables` (e.g. `$cumulative_months`).
- Math/Arithmetic: `calc` (`+ - * / % floor ceil round pow min max`), `value1`, `value2`,
  `variableName` → stores result as variable.
- EventSub variables: `$user_name`, `$reward_title`, `$cumulative_months`, `$bits`;
  chat: `$user`, `$message`; StreamElements: `$user`, `$amount`.

## Phases (task list mirrors this)

1. ✅ Spec extraction (this file + reference/cci-docs)
2. Scaffold builds on 26.2
3. Core engine: JSON (de)serialization registry, variables, notes, constants, TOML config
4. All outcomes (client + server command path)
5. Twitch IRC chat socket
6. Twitch EventSub socket (+ token refresh)
7. Streamlabs/StreamElements/DonationAlerts via internal Socket.IO client
8. YouTube chat polling
9. Client UI: toasts, status/connections screen, `/creatorlink` client command
   (full in-game editor is a later stretch goal — configs are file-editable meanwhile)
10. Build, smoke test, README + migration guide

## Out of scope for now

- The full Boxes&Stuff-style in-game visual editor (stretch goal)
- Game hooks expert system (stretch goal)
- `miniatures:minime` outcomes depend on iChun's Miniatures mod — no 26.2 version exists;
  configs referencing it need vanilla substitutes.
