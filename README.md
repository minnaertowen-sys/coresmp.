# CoreSmp (Fabric 1.21.11, server-side only)

Players do NOT need to install anything. Drop the jar in the server's `mods` folder.
Everything new is under **/core** (type `/core help` in game).

## Features
- **Teleports:** /spawn /setspawn /home /sethome /delhome /homes /warp /warps /setwarp /delwarp /tpa /tpaccept /tpdeny /rtp
- **Kits:** `/core kit <name>`, `/core kits` (kits are lists of commands in config.json, with cooldowns)
- **Quests -> Army:** `/core quest`. Finish quests (kill mobs / mine blocks / play time) to earn soldier slots.
  `/core army summon` spawns a soldier (iron golem, never attacks players) that follows you, teleports to you,
  and attacks hostile mobs you fight. `/core army dismiss` removes them. Admin: `/core army give <player> <n>`
- **Deathban:** dying bans you for 30 minutes (config `deathbanMinutes`). Admin: `/core deathban pardon <name>`.
  Add admin names to `deathbanExempt` in config.json.
- **Chat disabler:** `/core chat off|on` (admin). Names in `chatExempt` can still talk.
- **Anti-cheat:** speed, hover-fly, reach and auto-click checks with setbacks + kick on repeated flags.
  Admin: `/core ac alerts` (toggle live alerts), `/core ac status <player>`.
- **Unstable extras:** random world events every 20 min (lightning, floating, supply drops, hordes, speed storm).
  Admin: `/core extras toggle|trigger|strike <player>` (strike = orbital strike: lightning + TNT).
- **/core info:** TPS, MSPT, players, RAM. **/core reload** reloads config.json.

Config: `config/coresmp/config.json` (quests, kits, events, limits). Data: `config/coresmp/data.json`.
Use `%player%` (UUID selector) and `%name%` in command lists.

## Build
Requires JDK 21.
    gradle wrapper      (once)
    ./gradlew build     (Windows: gradlew.bat build)
Jar: build/libs/CoreSmp-1.0.0.jar  (NOT the -sources jar)
