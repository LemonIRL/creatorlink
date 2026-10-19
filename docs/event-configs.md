# Event Configs

Event configs are JSON files in your profile folder
(`config/creatorlink/default/`). Each file handles one service:

| File | Service |
|---|---|
| `twitcheventsub.json` | Twitch events (channel points, subs, follows, cheers, raids...) |
| `chat.json` | Chat messages — Twitch IRC **and** YouTube share this file |
| `streamelements.json` | StreamElements events |
| `streamlabs.json` | Streamlabs events |
| `donationalerts.json` | DonationAlerts donations |
| `constants.json` | Reusable conditions/outcomes/events (see below) |
| `gamehooks.json` | In-game events: `joinWorld`, `leaveWorld`, `death`, `respawn`, `attackBlock`, `useItem`, `chatSent`, `commandSent` — **off by default**; enable `enableGameHooks` in Settings |

Edit them three ways:

- **Simple mode**: `/creatorlink simple` (or the *Simple* button in the
  editor) — lists every channel point reward you have set up with
  Edit/Delete, plus a guided form to add more: choose the **Source** (Twitch
  EventSub or StreamElements — saving removes the same reward from the other
  source so it can't double-fire), type the reward name, pick a
  mob from a searchable list, toggle "spawn as baby" (baby zombies!), choose
  weapon and armor per slot — each piece gets its own **Enchants…** menu
  where you can stack multiple enchants with levels, and contradictory ones
  (Protection vs Blast Protection, Sharpness vs Smite) gray out just like at
  an anvil — toggle "turn off drops" and "no despawning" (persistence — the
  mob stays until killed), and it writes the config for you. Rewards
  imported ones are listed too: StreamElements ones (tagged **[SE]**)
  are fully editable in the form, while events matching on something other
  than the reward title (e.g. reward id) are tagged §8(advanced)§r — their
  *Editor* button jumps straight to that exact event in the full editor. Editing loads the reward back into the
  form; saving with an existing title updates it instead of duplicating.
  A *Stream events* section does the same for follows, raids, cheers, subs,
  resubs, **YouTube members** and **YouTube likes** (likes checked once a
  minute while live; "one per like" multiplier) — Twitch events have a
  **Source** choice (Twitch EventSub or StreamElements;
  the wizard removes its event from the other source so nothing double-fires)
  and multipliers like "one per raider", "one per month" (resubs) or "per X
  bits" — each with an optional **max repeats** cap so a 2,000-viewer raid
  can't summon 2,000 mobs. Also switches
  Twitch and YouTube chat between toasts/chat/both/off, with **name color**
  and **message color** pickers per platform — colors apply to both the
  toast and the chat line (the generated format string updates live, and
  stays hand-editable for finer control).
- **Full editor**: `/creatorlink editor` — tabs across the top for every
  socket type (twitcheventsub, chat, streamelements, streamlabs,
  donationalerts, gamehooks), navigator tree on the left, fields on the
  right. Tabs exist even when the file doesn't yet — open one, hit *+ Add
  Configuration*, and Save creates it. **Double-click** a `conditions` or
  `outcomes` node in the tree (or use its *+ Add…* button) to get the full
  searchable list of every registered type
  ("chat — ChatOutcome", "note — NoteOutcome", ...). Closing with unsaved
  edits (including deletes) asks whether to save or discard, so the file —
  and the Simple screen — always match what you saw. *Save* applies immediately. (YouTube
  chat is configured in **chat.json** under platform `youtube`, not a file
  of its own.) Use
  `/creatorlink events` to inspect recent events (and their variable names)
  and replay them while testing.
- **Text editor**: edit the JSON directly, then `/creatorlink reload` in-game.

## How an event flows

```
Stream event  →  variables  →  matching file  →  "for" filter  →  event type  →  your Config Events
```

Every incoming event is broken into **variables** — a name → value list you
reference with `$name` (case-sensitive). Example: a resub carries
`$user_name`, `$cumulative_months`, `$message`, and more.

## File structure

```json
{
  "configs": [
    {
      "for": "channel.channel_points_custom_reward_redemption",
      "types": {
        "add": [  ← list of Config Events, tested top to bottom
          {
            "name": "optional label",
            "conditions": [ ... ],
            "outcomes": [ ... ],
            "allowsOtherEventsToTrigger": true
          }
        ]
      }
    }
  ],
  "init": [],
  "type": "twitcheventsub"
}
```

- **`for`** — which event family this block handles. For Twitch EventSub it's
  the subscription type minus the last piece: `channel.subscription`,
  `channel.raid`, `channel.channel_points_custom_reward_redemption`...
  For `chat.json` it's the platform: `"twitch"` or `"youtube"`. `null` matches everything.
- **`types`** — the specific event: `add` for a redemption, `message` for
  a resub with text, `incoming`/`outgoing` for raids, `message`/`action` for chat.
- A Config Event with **no conditions always triggers**. When one triggers,
  the ones below it are skipped — unless it sets
  `"allowsOtherEventsToTrigger": true`.

### Useful Config Event fields

| Field | Effect |
|---|---|
| `conditions` | All must pass (or any, with `triggersFromAnyConditionMet`) |
| `outcomes` | What happens; all run, or first success with `singleOutcomeOnly` |
| `allowsOtherEventsToTrigger` | Keep testing the events below this one |
| `cooldown` | Ticks before this event may fire again (20 ticks = 1 s) |
| `delay` | Ticks to wait before the outcomes run |
| `playTime` | With a `queue` on the Configuration: stagger events (e.g. gift bombs) |
| `disabled` | Skip this event entirely |

## Conditions

The workhorse — compare a variable to a value:

```json
{ "type": "stringEquals", "source": "$reward_title", "target": "Spawn Zombie" }
```

Common types (fields are `type`/`source`/`target` — **no underscores**):

| type | Checks |
|---|---|
| `stringEquals` / `stringContains` / `stringStarts` / `stringEnds` | String comparisons |
| `variableCheck` | `variableName` + `variableResult` (case-insensitive by default) |
| `variableExists` | A variable is present |
| `message` | The `$message` contains a `phrase` |
| `user` | Event came from a specific `username` |
| `range` / `amount` | `$amount` within `min`/`max`, or exactly `amount` |
| `random` | Passes with probability `chance` (0.0–1.0) |
| `note` | Load a saved note into a variable |
| `multiple` | Group conditions with AND/OR (`any`) |

Some "conditions" always pass and exist to *do* things — set variables
(`variableInsert`), do math (`math`), manipulate strings (`stringSubstr`,
`stringLower`, `stringSplit`, ...), or log every variable of an event
(`logArgs` — great for discovering variable names).

Math example — one spawn per 100 bits:

```json
{ "type": "math", "calc": "/", "value1": "$bits", "value2": "100", "variableName": "hundreds" },
{ "type": "math", "calc": "floor", "value1": "$hundreds", "value2": "0", "variableName": "spawns" }
```

Add `"inverseMatch": true` to any condition to flip its result.

## Outcomes

| type | Does | Key fields |
|---|---|---|
| `command` | Run a Minecraft command | `command`, `executeAsSelf` |
| `chat` | Message in your chat (client-side) | `message`, `inActionBar`, `sendToServer` |
| `toast` | Pop-up top right | `title`, `subtitle` |
| `sound` | Play a sound | `sound` (e.g. `minecraft:entity.pig.ambient`), `volume`, `pitch` |
| `repeat` | Run an outcome N times | `times` (can be `$variable`), `outcome` |
| `conditional` | Outcome with its own condition | `condition`, `outcome`, `conditionNotMetOutcome` |
| `multiple` | Group outcomes | `outcomes` |
| `note` | Save a value to disk | `noteName`, `noteContent`, `expiresIn` |
| `null` | Does nothing (placeholder) | |

`executeAsSelf: true` runs the command as *you* (needs the server-side mod +
whitelist, or singleplayer). Inside JSON, quotes in commands must be escaped:
`\"minecraft:iron_sword\"`.

Give outcomes a `weight` to pick **one at random** instead of running all —
higher weight, higher odds.

## Variables cheat sheet

| Where | Variables |
|---|---|
| Twitch EventSub (all) | `$user_name`, `$broadcaster_user_name` |
| Channel point redemption | `$reward_title`, `$reward_cost`, `$user_input` |
| Resub message | `$cumulative_months`, `$message` |
| Cheer | `$bits` |
| Chat (`chat.json`) | `$user`, `$message`, plus IRC tags like `$custom-reward-id` |
| StreamElements | `$user`, `$amount`, `$message` |
| YouTube chat | `$displayMessage`, `$authorDetails_displayName` |
| Always available | `$streamer`, `$playerName`, `$mcVersion` |

Don't see the variable you need? Add a `logArgs` condition to the event, watch
the log, and read the full list.

## Constants and notes

- **Constants** (`constants.json`) — define a condition/outcome/event once,
  reference it everywhere with `{"type": "constant", "name": "MyThing"}` or an
  event's `constantName`. Change it in one place, changes everywhere.
- **Notes** — variables that survive restarts. Saved with the `note` outcome,
  read back with the `note` condition. Optional expiry: `"expiresIn": "2d12h"`.

See [Recipes](recipes.md) for complete working examples.
