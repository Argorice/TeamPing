# TeamPing — NeoForge

Developer notes. For what the mod actually does, see the [root README](../README.md).

The Fabric version is a separate project in `../fabric`, forked from this one.

## Building

```
gradlew build            # jar in build/libs/
gradlew runClient
gradlew runServer
gradlew runClientAlt     # second client as "Tester2", for testing pings with two players
```

ModDevGradle 2.0.143, Gradle 9.2.1, Java 21, NeoForge 21.1.233. Versions live in
`gradle.properties`. If Gradle complains about the wrapper:
`gradle wrapper --gradle-version 9.2.1 --distribution-type bin`.

On Windows `../local/build-mod.bat` does the same in one click and dumps the whole output
into `local/build.log`, which is easier to read than a console that has scrolled away.

## Keybinds

`PingKeybinds` registers five mappings: `Y` for the main one, and four unbound extras for
danger, waypoint, removing the nearest waypoint, and the waypoint list. Modifiers on the
main key do the same things, so the extras are there purely for people who dislike
modifier combos.

UI hints never hardcode "Ctrl + Y" — `PingKeybinds.waypointHint()` asks the mappings what
is actually bound right now, so rebinding is reflected everywhere.

## Layout

```
src/main/java/dev/teamping/
├── TeamPing.java   @Mod, networking and server events
├── api/            public API for other mods
├── network/        five payloads and their codecs
├── ping/           model, types, server-side manager
├── team/           TeamSource per system, merged by CompositeTeamProvider
├── config/         plain JSON, no config library
└── client/         store, rendering, HUD, keys, waypoints, map/
```

`client/TeamPingClient` is `@Mod(dist = Dist.CLIENT)`, so nothing under `client/` is
loaded on a dedicated server.

## Server-side validation

The client sends coordinates, a type and what it was aiming at — a block position, an
entity id, or neither. On arrival the server:

1. drops the packet if the position isn't finite;
2. drops it if the player pinged less than a second ago;
3. resolves the target (see below), which can move the point and change the type;
4. drops it if the point is further than `maxPingDistance` away;
5. takes the dimension from the player, not the packet;
6. fills in the owner, name and timestamp itself;
7. for a resource ping, reads the block and checks it's really in `c:ores`, otherwise
   downgrades it to a normal ping;
8. sends it to teammates in the same dimension who actually have the channel.

The distance check runs *after* target resolution on purpose: a hit on a Sable ship
arrives in that ship's plot coordinates, thirty million blocks out, and would fail the
check before anyone had a chance to translate it back into the world.

## Aiming at things

`ServerPingManager.resolveTarget` decides what was pinged, in this order:

- **a Sable ship** → `VESSEL`, labelled with the ship name, positioned on the hull;
- **a player** → `ALLY` or `ENEMY`, depending on `isSameTeam`. The client says which
  entity it was looking at and nothing more — who counts as a teammate is the server's
  business, so an edited client can't paint an enemy blue. With no team at all the split
  is meaningless, and it falls back to a plain named marker;
- **any other entity** → a normal ping labelled with the entity name;
- **nothing in particular** → the point as sent.

A waypoint stays a waypoint even when aimed at a player: `Ctrl+Y` means "remember this
spot", not "mark that target".

Entity picking on the client is a plain loop over `level.getEntities` with `AABB.clip`,
compared against the block hit by distance. `ProjectileUtil` would do the same thing, but
its signature has moved between versions often enough that a hand-rolled loop is the
cheaper bet.

## Create Aeronautics (Sable)

Sable ships are chunk plots inside the same level, parked far outside the normal world.
It `@Overwrite`s `BlockGetter#clip`, so a vanilla raycast already hits ship blocks — it
just answers in plot coordinates. `util/SableSupport` translates those back:

```
SableCompanion.INSTANCE → getContaining(level, pos) → projectOutOfSubLevel(access, vec)
```

All of it by reflection, gated on `ModList.isLoaded("sable")` rather than class presence —
the companion classes get jar-in-jar'd into other mods, so finding the class proves
nothing. Anything unexpected means one warning in the log and a normal ping instead of a
vessel one.

Methods are looked up by **exact signature**, not by name and argument count.
`getContaining` has eight two-argument overloads and `projectOutOfSubLevel` has three;
which one `getMethods()` hands back first is undefined, and picking the wrong one gives
`IllegalArgumentException: argument type mismatch` at the first ship you aim at. We ask
for `getContaining(Level, Vec3i)` and `projectOutOfSubLevel(Level, Position)`, falling
back to the deprecated `(Level, Vec3)` for older companion builds. If neither is there,
the warning prints the signatures it actually found, so the next mismatch is a log line
rather than a guessing game.

The API shape came from reading [sable-companion](https://github.com/ryanhcode/sable-companion)
(MIT, RyanHCode) — it is a compatibility library meant to be called, and that is all we
do with it. No code was copied.

## When not everyone has the mod

Payloads are registered `optional()`. A vanilla client joins fine and just sees nothing.
A client with the mod on a server without it also joins, and gets one "not installed on
this server" line the first time it tries to ping.

The `hasChannel` check runs on both ends. NeoForge validates sends symmetrically, so
without the client-side check, pressing the key on a foreign server would throw straight
out of the tick loop.

## Map markers

Pings show up on Xaero's Minimap and World Map. Config key `mapMarkers`: `all`,
`waypoints` or `none`.

This goes through reflection rather than a compile dependency — Xaero's sources are
closed and there's no waypoint API, so compiling against internals would mean pinning an
exact version. If anything doesn't match, you get one warning in the log and the world
markers keep working.

A few things worth knowing if you touch `client/map/XaeroMapIntegration`:

- detection is by class, not mod id, because Better PVP bundles the same minimap
- each marker remembers which waypoint set it went into, since the player can switch sets
- markers are created `temporary` so a crash doesn't leave them behind
- the world map reads waypoints from the minimap, so world map alone does nothing

Class and method names were cross-checked against [XaeroPlus](https://github.com/rfresh2/XaeroPlus),
[MapLink](https://github.com/thebuildcraft/RemotePlayerWaypointsForXaero) and
[ping-to-map-xaeros](https://github.com/KURONAMI333/ping-to-map-xaeros) — thanks to their
authors. No code was copied from them.

## API

Other mods can subscribe to pings and render them however they like. No team filtering
needed, pings from other teams never reach the client.

```java
// client
TeamPingClientApi.registerListener(new PingListener() {
    @Override public void onPingAdded(Ping ping) { myMap.addMarker(ping); }
    @Override public void onPingRemoved(UUID id) { myMap.removeMarker(id); }
    @Override public void onPingsCleared()       { myMap.clearMarkers(); }
});

TeamPingClientApi.placePing(position, PingType.DANGER, null);
```

```java
// server
TeamPingServerApi.placePing(player, position, PingType.DANGER, null);
Collection<ServerPlayer> mates = TeamPingServerApi.teammates(player);
int color = TeamPingServerApi.teamColor(player);
```

A late subscriber immediately gets `onPingAdded` for everything already live. Exceptions
from listeners are caught and logged. The Xaero integration uses this same API, there's no
private path around it.

Public surface is `dev.teamping.api.*` plus `Ping` and `PingType`. Everything else can
change between versions.

## Config

`config/teamping-client.json`:

```
pingScale            0.5 – 2.0
showThroughWalls     true/false
showOffscreenArrows  true/false
maxOffscreenArrows   1 – 8
soundVolume          0.0 – 1.0
hideOwnPings         false
showActionbarNotice  true
showDistance         true
mapMarkers           all | waypoints | none
```

`config/teamping-server.json`:

```
maxPingDistance       256
rateLimitMs           1000
maxWaypointsPerPlayer 8
soloModeRadius        512   (only for players who turned the switch on)
teamProvider          auto | ftb | scoreboard | solo
```

Teams come from `TeamSource`s, and `CompositeTeamProvider` **adds them up** instead of
picking one: teammates are the union, and two players are on one team if they share any
source. Packs hand out teams twice often enough that choosing between FTB and the
scoreboard was just wrong.

If nothing reports a team, the ping goes to its author and nobody else — the same rule
waypoints follow. `SoloProvider` and its radius are still there, but only for players who
asked for them: `NearbySharing` holds that per-player flag, the client sends it on join and
on every toggle, and it lives in the client config so it survives a relog. The switch is in
the waypoint list rather than a config file, because "who sees this" is a decision people
make mid-game.

It is off by default on purpose. "Everyone within 512 blocks" sounds friendly on a server
with four friends on it and means "strangers over the next hill" on any other.

One consequence worth knowing: with no team at all, pointing at a player gives a plain
named marker instead of Ally or Enemy. Painting a mate red because nobody had created a
team would be a lie, so the mod does not guess.

One trap worth knowing: FTB keeps every player in a personal team even outside a party,
so `FtbTeamSource.partyOf` filters by `isPartyTeam()` and returns null for those.
Without that filter vanilla `/team` would never get a turn on any server with FTB
installed — which is exactly the bug that made `/team` look broken.

`teamProvider` in the server config narrows this down: `ftb` or `scoreboard` for one
source only, `solo` to ignore teams entirely — that last one is a deliberate server-wide
choice, so it hands out the radius to everybody and ignores the per-player switch.

Missing or broken files fall back to defaults and get rewritten.

## Waypoints

Waypoints live on the server, in the world save (`WaypointStore extends SavedData`), and
survive a restart. Up to 8 per player; the oldest of that player is dropped on overflow,
so nobody can eat the whole team's budget.

Ownership is dual: a waypoint belongs both to whoever placed it and to every team they
were in at that moment — plural, since FTB and the scoreboard can both apply. Everything
else follows from that:

- someone joining the team sees waypoints placed before they arrived;
- the author leaves: the waypoint stays with them *and* with the team;
- anyone else leaves: it disappears for them, they did not place it;
- no team at all: it is a private note, visible only to the author.

A waypoint is visible when its team ids intersect the viewer's.

Neither FTB nor the vanilla scoreboard reports team changes as an event, so `WaypointSync`
compares each player's team ids every 40 ticks and pushes the difference. Two seconds is
short enough that nobody notices and cheap enough to be one set comparison per player.

The client keeps no waypoint file of its own. It used to, and that was the reason a
waypoint survived leaving a team — the server simply had no idea it existed.

## Before releasing

- [ ] server starts with and without FTB Teams
- [ ] players in different teams don't see each other's pings
- [ ] a client without the mod can join
- [ ] dedicated server, not singleplayer
- [ ] changing dimension clears pings
- [ ] key spam hits the rate limit
- [ ] waypoints survive a relog and a server restart
- [ ] joining a team shows its existing waypoints
- [ ] leaving a team hides other people's waypoints but keeps your own
- [ ] Xaero markers appear and disappear with the ping; nothing breaks without Xaero
- [ ] pinging a teammate gives blue, pinging anyone else gives red
- [ ] pinging a Sable ship lands the marker on the hull, not thirty million blocks away
- [ ] the same ping without Sable installed still works as a normal one

Things only visible in game: the icon through a wall vs in the open, arrows when a ping is
directly behind you, icon scale at 10 and 200 blocks.

## Misc

Textures and the mod icon come from `tools/gen_textures.py` (Pillow) — edit the script,
not the PNGs. It writes the marker icons into `src/main/resources`, the small icon next to
them, and a 512-pixel copy into `../docs` for the mod pages.

`../local/` is my own corner: the build script, its log, the store texts. It ignores
itself, so nothing from there can reach the repository by accident.
