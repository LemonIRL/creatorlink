# Troubleshooting

First stop, always: **`/creatorlink connections`** in-game — a live panel
showing every service's state (with per-service Disconnect/Reconnect
buttons), or `/creatorlink status` for the same list in chat — and the game
log (`logs/latest.log` — CreatorLink lines are tagged `(creatorlink)`).

## Connections

**TwitchEventSub shows ERROR: "token invalid and refresh failed"**
Your OAuth token expired and couldn't be renewed. Generate a fresh pair at
[twitchtokengenerator.com](https://twitchtokengenerator.com/quick/l7oG2vcNOE),
paste both into `creatorlink.toml` (`eventsubOauthTokens` +
`eventsubRefreshTokens`), then run `/creatorlink reconnect`.

**TwitchEventSub connects but some events never arrive**
Your token is missing scopes (each event type needs its own read scope).
Tokens from the link above include everything; tokens generated elsewhere
often don't. Regenerate. Also check the log for `subscription revoked` lines.

**TwitchChat stays DISCONNECTED**
Check the channel name in `twitchChats`: lowercase, no `#`, no URL — just
`"yourchannel"`.

**StreamElements: "JWT rejected"**
Copy the JWT again from StreamElements → your profile (top right) →
*Show secrets*. It's very long; make sure you got all of it inside the quotes.

**YouTubeChat: "No active live stream found"**
Normal — YouTube only connects while you're actually live. Start the stream,
then `/creatorlink reconnect`. Frequent reconnects can also exhaust the free
Google API quota for the day.

**Everything says DISCONNECTED after several retries**
CreatorLink gives up after `maxAutomaticReconnects` attempts (default 3).
Fix the cause, then hit that service's **Reconnect** button in
`/creatorlink connections` (or run `/creatorlink reconnect` to restart
everything) — either resets the counter.

**Chat messages work but commands don't (mobs never spawn) — on a server**
Command outcomes run through the server and require the streamer to be
**whitelisted** there: an op runs `/creatorlink whitelist <name>` (this works
from a modded client too). Chat/toast outcomes are client-side, which is why
they still show. If you're not whitelisted you'll see
"[CreatorLink] You are not whitelisted on this server." when an outcome fires.

**Commands imported from 1.21.5 or older silently do nothing**
Mojang changed entity/item NBT (`ArmorItems`/`HandItems` → `equipment:{}`,
item `tag:{}`/`Enchantments` → `components:{}`, JSON-string `CustomName`).
The full editor flags these: the outcome shows **§c⚠ old-version command§r**
in the tree, and the command box turns red with the fix in its tooltip.
Re-saving the reward from Simple mode writes current syntax.

## Events

**Reward redemptions do nothing**
- The reward name in Twitch must match your config's `target` **exactly** —
  same capitalization, same spaces.
- Confirm the event block sits under
  `"for": "channel.channel_points_custom_reward_redemption"` → `"add"`.
- Watch the log while redeeming. Nothing at all? The EventSub connection is
  the problem (see above). Event arrives but nothing happens? A condition is
  failing — temporarily add `{"type": "logArgs"}` as the first condition to
  print every variable and check what `$reward_title` actually contains.

**A condition file "worked in CCI docs" but crashes or never matches**
Field names have **no underscores**: `type`, `source`, `target`. (Old
underscore-style configs are tolerated, but mixed styles in one object are not
worth the confusion — pick the plain style.)

**Chat messages appear twice (toast + chat)**
Twitch chat arrives via two routes: IRC (`chat.json`) and EventSub
(`channel.chat` → `message`). Empty out the EventSub one in
`twitcheventsub.json`: `"message": []`.

**health/food/motion/mount/inventory outcomes do nothing on a server**
These manipulate entities server-side, so the server needs CreatorLink
installed and you whitelisted — same requirement as long commands. In
singleplayer they always work.

**Commands don't run on a server**
- The server needs the mod too, and you must be whitelisted:
  `/creatorlink whitelist YourName` (see [Server Setup](server-setup.md)).
- Without the server mod, only commands under 256 characters work, and only
  if you have op.
- Check the base command isn't in the server's `disallowedCommands`.

**Summon commands fail or mobs look wrong**
NBT formats change between Minecraft versions. Paste the exact command into
chat yourself — vanilla will tell you which part it dislikes. Fix it there
first, then put it back in the config (remember to escape quotes: `\"`).

**Mob spawns inside you and suffocates you both**
Offset the spawn: `~2 ~ ~` instead of `~ ~ ~` after the coordinates.

**`$variable` prints literally (e.g. the toast says `$user_name`)**
That variable doesn't exist on this event. Variable names are case-sensitive
and differ per service (`$user_name` on EventSub, `$user` on chat and
StreamElements). Use `logArgs` to list what's actually available.

**An event fires once then never again**
It probably has a `cooldown`, or an event above it triggered without
`allowsOtherEventsToTrigger`. Remember: first matching event wins and stops
processing unless that flag is `true`.

## Config file problems

**"Failed to load ...json" in the log**
Broken JSON — usually a missing comma, a stray trailing comma, or an unescaped
quote inside a command. Paste the file into any JSON validator to find the line.

**Unknown condition/outcome type '...' — skipping**
Typo in a `type` id, or a feature that isn't implemented yet (the README lists
those). The rest of the file still loads.

**My changes don't apply**
`/creatorlink reload` after editing JSON configs, `/creatorlink reconnect`
after editing the toml.

## Still stuck?

Enable event logging in `creatorlink.toml`:

```toml
[general]
	logTypes = ["socket_event"]
```

then reproduce the problem and read `logs/latest.log` — you'll see the raw
event exactly as the service sent it.
