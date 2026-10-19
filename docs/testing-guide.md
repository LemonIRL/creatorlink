# Testing Guide

How to verify every feature, mostly without needing live Twitch events.

## The test harness

A ready-made `gamehooks.json` maps chat commands to every outcome type, so you
can trigger anything by **typing in Minecraft chat**. It's already installed in
the "26.2 test" profile (and lives in the repo at
`docs/examples/gamehooks.json`).

Setup: game hooks are **off by default** — enable them first via
`/creatorlink settings` → `enableGameHooks` → `true` → *Save + Reconnect*
(or set it in `creatorlink.toml`). Then join any world and type
**`!test help`** in Minecraft chat for the full command list. The message
still appears in chat (and on Twitch if you're connected); that's normal,
the trigger is you *sending* it.

## Checklist

### 0. Boot sanity

| Check | Expect |
|---|---|
| Launch the profile, watch the log | `CreatorLink initialised (client), profile 'default'` |
| `/creatorlink status` | TwitchChat + TwitchEventSub green `CONNECTED` |
| Join a world | Toast: "gamehooks active — type !test help" |

### 1. Display outcomes

| Type in chat | Expect |
|---|---|
| `!test toast` | Cyan toast top-right |
| `!test chat` | Green chat line with your `$streamer` and MC version filled in |
| `!test actionbar` | Yellow text above the hotbar |
| `!test sound` | Level-up chime |
| `!test narrator` | Spoken "Creator Link narrator test successful" (needs OS text-to-speech on) |

### 2. Interactive popups

| Type | Expect |
|---|---|
| `!test popup` | Dialog with OK; pressing OK pops a toast |
| `!test prompt` | Yes/No dialog with custom button labels; each answer prints a different chat line |
| `!test input` | Text box (pre-filled "hello"); Done shows a toast with what you typed |

### 3. Engine features

| Type | Expect |
|---|---|
| `!test math` | Three "repeat iteration" lines (350 / 100 floored = 3) |
| `!test random` | ONE of common/uncommon/rare — spam it; ratios ≈ 6:3:1 |
| `!test note` then `!test readnote` | Second command prints the saved note; also check `notes.json` on disk |
| `!test delay` | Chat line arrives 3 seconds after you send |
| `!test state` | Different message depending on whether you're sneaking (tests conditions + inverseMatch) |
| `!test query` | Your health, biome id, and world day (target-query conditions) |

### 4. Entity manipulation (server ops)

Singleplayer: all work immediately. Multiplayer: server needs the mod + you whitelisted.

| Type | Expect |
|---|---|
| `!test hurt` | Take 2 hearts damage |
| `!test heal` | Heal 3 hearts |
| `!test feed` | Hunger bar refills |
| `!test launch` | You get launched upward |
| `!test mount` | A pig spawns and you're riding it |
| `!test give` | 3 diamonds appear in your inventory |
| `!test freespace` | Sheep summoned at a safe open spot near you (never inside blocks) |

### 5. Client manipulation

| Type | Expect |
|---|---|
| `!test zoom` / `!test unzoom` | FOV snaps to 30 / back to 70 (gameSettings outcome) |
| `!test sneak` | You crouch for 3 seconds, then release (keyPress + delayed event) |
| `!test fakecrash` | Game freezes for exactly 3 seconds, then recovers |

### 6. Command feedback capture

1. `!test feedback` — runs `/time query daytime` silently and arms the capture
2. `!test feedback2` — prints "captured feedback was: The time is …"

### 7. Gamehook events themselves

| Do | Expect |
|---|---|
| Punch a block | Action bar: "punched minecraft:… at x y z" (2s cooldown) |
| Die and respawn | Red "you died!" then green "respawned" |
| Rejoin the world | The join toast again |

### 8. Twitch (needs your channel)

| Do | Expect |
|---|---|
| Type in **Twitch** chat | Toast top-right with name + message |
| `/me waves` in Twitch chat | Toast (type 2 background) |
| Redeem a channel point reward | The configured command fires (effect/mob) + chat line |
| Follow/sub/cheer | Chat lines per your `twitcheventsub.json` |

No live viewers to trigger things? Use **`/creatorlink events`** — every past
event is cached with a **Replay** button. Replay a redemption to re-run its
outcomes without spending points. StreamElements' dashboard test buttons
("send test follow") also work and arrive as `event:test`.

### 9. Editor

1. `/creatorlink editor` → tree opens on `twitcheventsub.json`
2. Navigate: configurations → `channel.channel_points_custom_reward_redemption` → `add` → pick a reward event
3. Change the chat outcome's `message`, hit **Save**
4. Redeem (or Replay) — the new text appears, no restart
5. Add a brand-new event: select the `add` type node → *+ Add Event* → select it → *+ Add…* condition (`stringEquals`) and outcome (`chat`) → fill fields → Save

### 10. Multiplayer / whitelist (with your friend)

1. Server (26.2 Fabric) gets the mod; both players get it client-side
2. Streamer redeems → mob spawn should FAIL with "not whitelisted"
3. `/creatorlink whitelist <streamer>` (op) → redeem again → works
4. Add `"summon"` to `disallowedCommands` in the server toml, restart → spawn blocked with the disallowed message → remove it again

### 11. Raw logs

`logTypes = ["socket_event"]` is already on in your config. After any Twitch
activity, check `config/creatorlink/logs/<today>.log` — every raw IRC line and
EventSub JSON payload is there. This is the ground truth when an event doesn't
do what you expect.

## When something fails

1. `/creatorlink events` — did the event arrive at all? What are its variables?
2. `config/creatorlink/logs/` — did the raw payload arrive?
3. Game log (`logs/latest.log`), lines tagged `(creatorlink)`
4. [Troubleshooting](troubleshooting.md) for the common cases

## Cleanup

The harness is just a config file — delete `gamehooks.json` from the profile
(or set every event `"disabled": true` in the editor) when you're done testing.
`!test zoom` changes your real FOV setting; run `!test unzoom` if you used it.
