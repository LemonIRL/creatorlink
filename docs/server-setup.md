# Server Setup

Everything visual (toasts, chat, sounds) works with the mod on your client
only. But **command outcomes** — spawning mobs, giving effects — run on the
server, so multiplayer needs a little setup.

## Why install on the server?

- Without it, `command` outcomes fall back to sending the command through
  chat, which caps at 256 characters and requires you to already have op.
- With it, whitelisted players can run commands up to ~32,000 characters
  (big summon commands with NBT) with proper permissions, and the owner can
  block specific commands.

## Install

1. Drop `creatorlink-<version>.jar` and Fabric API into the server's `mods`
   folder (Fabric, Minecraft 26.2, Java 25).
2. Start the server once — it generates
   `config/creatorlink/creatorlink-common.toml`.
3. Whitelist the streamer(s):

   ```
   /creatorlink whitelist TheStreamerName
   ```

   Only whitelisted players can trigger server-side outcomes — **being op is
   not enough** (and not required): op level only controls who may *manage*
   the whitelist. The command works from a modded client too (it forwards to
   the server). Remove with `/creatorlink dewhitelist <name>`.

## Server config (`creatorlink-common.toml`)

| Setting | Meaning |
|---|---|
| `whitelistedUsers` | Players allowed to trigger server-side outcomes |
| `enableBlacklist` | Flip the list's meaning: everyone *except* these players (toggle in-game with `/creatorlink setBlacklist`) |
| `commandPermissionLevel` | Op level needed to *manage* the whitelist (default 3) |
| `disallowedCommands` | Commands viewers can never trigger, e.g. `["stop", "op", "whitelist", "ban"]` |
| `logTypes` | Extra logging categories |

**Recommended:** always fill `disallowedCommands` with at least
`stop`, `op`, `deop`, `ban`, `ban-ip`, `whitelist` — channel point redemptions
run with elevated permissions, and reward titles are matched by string, so
defense in depth is cheap. Note it matches the base command only
(`/tp` and `/teleport` are separate entries).

## The streamer's side

Nothing extra. When a whitelisted player with CreatorLink joins, `command`
outcomes with `"executeAsSelf": true` are sent to the server automatically.
If the server doesn't have the mod (or you're not whitelisted), short commands
still work through the chat fallback as long as you have op.
