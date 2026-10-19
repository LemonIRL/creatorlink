# Recipes

Complete, working examples. All of these go inside the matching `types` block
of the file named in each recipe. Reload with `/creatorlink reload`.

Replace `YourName` with your Minecraft username throughout.

## Channel point reward → potion effect

Create the reward on your Twitch dashboard (name must match **exactly**, it's
case-sensitive), then in `twitcheventsub.json` under
`"for": "channel.channel_points_custom_reward_redemption"` → `"add"`:

```json
{
  "conditions": [
    { "type": "stringEquals", "source": "$reward_title", "target": "Regen" }
  ],
  "outcomes": [
    { "type": "command", "command": "/effect give YourName minecraft:regeneration 22 1", "executeAsSelf": true },
    { "message": "[Twitch] $user_name redeemed $reward_title!", "type": "chat" }
  ],
  "allowsOtherEventsToTrigger": false
}
```

## Channel point reward → named mob spawn

Same place as above. The mob is named after the redeeming viewer and drops
nothing when killed:

```json
{
  "conditions": [
    { "type": "stringEquals", "source": "$reward_title", "target": "Spawn Zombie" }
  ],
  "outcomes": [
    {
      "type": "command",
      "command": "/execute at YourName run summon minecraft:zombie ~2 ~ ~ {CustomName:{text:\"$user_name\"},equipment:{mainhand:{id:\"minecraft:iron_sword\",count:1},head:{id:\"minecraft:diamond_helmet\",count:1}},drop_chances:{mainhand:0.0f,head:0.0f},DeathLootTable:\"\"}",
      "executeAsSelf": true
    },
    { "message": "[Twitch] $user_name redeemed $reward_title!", "type": "chat" }
  ],
  "allowsOtherEventsToTrigger": false
}
```

Tips: `~2 ~ ~` offsets the spawn so the mob isn't inside you. Add `IsBaby:1b,`
before `CustomName` for a baby zombie. Swap `zombie` for `skeleton`, `bogged`
(give it a bow), `wither_skeleton`, etc. If a summon fails, test the command
in-game first — NBT formats occasionally change between Minecraft versions.

## Follow → chat shoutout

`twitcheventsub.json`, `"for": "channel"` → `"follow"`:

```json
{
  "conditions": [],
  "outcomes": [
    { "message": "[Twitch] $user_name just followed!", "type": "chat" }
  ],
  "allowsOtherEventsToTrigger": true
}
```

## Resub → one mob per month subscribed

`twitcheventsub.json`, `"for": "channel.subscription"` → `"message"`
(fires when a resub is shared in chat):

```json
{
  "conditions": [],
  "outcomes": [
    {
      "type": "repeat",
      "times": "$cumulative_months",
      "outcome": {
        "type": "command",
        "command": "/execute at YourName run summon minecraft:wither_skeleton ~2 ~ ~ {CustomName:{text:\"$user_name\"}}",
        "executeAsSelf": true
      }
    },
    { "message": "[Twitch] $user_name resubscribed for $cumulative_months months!", "type": "chat" }
  ],
  "allowsOtherEventsToTrigger": true
}
```

## Cheer → one spawn per 100 bits

`twitcheventsub.json`, `"for": "channel"` → `"cheer"`. The math conditions
always pass — they just compute `$spawns` (99 bits = 0 spawns, 250 = 2):

```json
{
  "conditions": [
    { "type": "math", "calc": "/", "value1": "$bits", "value2": "100", "variableName": "hundreds" },
    { "type": "math", "calc": "floor", "value1": "$hundreds", "value2": "0", "variableName": "spawns" }
  ],
  "outcomes": [
    {
      "type": "repeat",
      "times": "$spawns",
      "outcome": { "type": "command", "command": "/execute at YourName run summon minecraft:creeper ~2 ~ ~", "executeAsSelf": true }
    }
  ],
  "allowsOtherEventsToTrigger": true
}
```

## Chat messages as toasts

`chat.json`, `"for": "twitch"` → `"message"`:

```json
{
  "conditions": [],
  "outcomes": [
    { "title": "$user", "subtitle": "$message", "type": "toast" }
  ],
  "allowsOtherEventsToTrigger": true
}
```

**Seeing chat twice?** Twitch chat can arrive via IRC (`chat.json`) *and* via
EventSub's `channel.chat` → `message`. Silence the EventSub copy by setting
its list empty in `twitcheventsub.json`: `"message": []`.

## Let viewers register their Minecraft name (`!setmcname`)

Viewers type `!setmcname TheirMcName` in chat; it's remembered on disk (a
note) and other events can spawn things named after their Minecraft name
instead of their Twitch name. In `chat.json`, `"for": "twitch"` → `"message"`:

```json
{
  "conditions": [
    { "type": "stringStarts", "source": "$message", "target": "!setmcname " },
    { "type": "stringSubstr", "source": "$message", "beginIndex": 11, "result": "nameArg" },
    { "type": "stringTrim", "source": "$nameArg", "result": "nameArg" },
    { "type": "stringLower", "source": "$user", "result": "userLC" }
  ],
  "outcomes": [
    { "type": "note", "noteName": "mcName_$userLC", "noteContent": "$nameArg" },
    { "title": "MC Name Set", "subtitle": "$user → $nameArg", "type": "toast" }
  ],
  "allowsOtherEventsToTrigger": true
}
```

Read it back in any other event (falls back to the Twitch name if unset):

```json
{ "type": "stringLower", "source": "$user", "result": "userLC" },
{ "type": "note", "noteName": "mcName_$userLC", "variableName": "mcname",
  "defaultVariableValue": "$user", "meetConditionIfNoteDoesNotExist": true }
```

…then use `$mcname` in your summon command.

## Random outcome (pick one)

Give each outcome a `weight` — one is chosen randomly, higher weight = more
likely:

```json
"outcomes": [
  { "type": "command", "command": "/effect give YourName minecraft:speed 30 1", "executeAsSelf": true, "weight": 5 },
  { "type": "command", "command": "/effect give YourName minecraft:slowness 30 1", "executeAsSelf": true, "weight": 3 },
  { "type": "command", "command": "/summon minecraft:lightning_bolt", "executeAsSelf": true, "weight": 1 }
]
```

## Cooldown a reward

Stop an event firing more than once per minute (20 ticks = 1 second):

```json
{
  "conditions": [ { "type": "stringEquals", "source": "$reward_title", "target": "Chaos" } ],
  "outcomes": [ ... ],
  "cooldown": "1200",
  "disableShortCircuitWhenOnCooldown": true,
  "allowsOtherEventsToTrigger": false
}
```
