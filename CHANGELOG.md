# Changelog

## 1.0.1

First public release. Minecraft 1.21.1, NeoForge.

- Seven ping types: normal, danger, resource, waypoint, ally, enemy and vessel, each with
  its own icon and sound
- Aim at a player and the ping picks itself: blue *Ally* for a teammate, red *Enemy* for
  anyone else — the server decides, the client is never asked
- Aim at a Create Aeronautics (Sable) ship and you get a *Vessel* ping on the hull, named
  after the ship, at its real position in the world
- In-world markers with the owner's name and distance, visible through walls, fading in
  and out
- Arrows at the edge of the screen for pings you can't see, up to four at a time
- Waypoints, 8 per player, stored in the world save and shared with the team they were
  placed in, with a list screen on `Ctrl+Shift+Y`
- Markers on Xaero's Minimap and World Map, if installed
- FTB Teams and vanilla `/team` both count at once, not one instead of the other
- With no team at all a marker is private to its author; a switch in the waypoint list
  shares it with everyone nearby instead, off by default
- API for other mods in `dev.teamping.api`
- Client and server configs as plain JSON
- English and Russian
