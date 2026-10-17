# Zenkai Ore Cycle

Minecraft 1.20.2, Forge 48.1.0, Java 17. Client-side only. Requires Baritone (separate mod).

## The loop (starts with gold, then coal, forever)
1. Baritone mines (`#mine gold_ore` or `#mine coal_ore`). Deepslate ore is not mined.
2. When no more ore fits in the inventory it sends `#stop`.
3. `/craft` opens a crafting screen. Ore is crafted into raw gold blocks / coal blocks, keeping a reserve of at least 45 ore.
4. `/vault` opens Personal Vaults. Gold blocks + reserve go in Vault #2, coal blocks + reserve in Vault #1. The screen is closed.
5. It switches to the other ore and goes back to step 1.

Hotbar slot 1 (the pickaxe) is never moved or dropped. Slots 2-36 are for ore only.
While mining, the ore is spread over the empty slots (one item each, taken from the biggest stack) so there is no empty slot
left for cobblestone, iron or anything else to be picked up into. Anything that is not raw gold, coal, their blocks or a shulker box is
dropped every second anyway, and once more right after the vault closes.
Auto-eat: while the loop runs, when hunger drops to 3 bars (6 food points) or lower, whatever is in your OFF HAND
(your senzu beans) is eaten. If the off hand is empty you get a chat warning instead.

## Extras
- **Gold-heavy:** after every round it mines gold again (`gold_heavy=true`; `false` goes back to alternating gold and coal).
  When the pickaxe in slot 1 drops below 150 durability (`pickaxe_min_durability`) the round is finished early (craft + vault what you
  hold) and it mines coal until the pickaxe is completely repaired (mending), then the round is finished and it returns to gold.
- **Stuck detection:** no new ore for 60 seconds (`stall_seconds`) sends `#stop`, then `/home mine` (`home_command`), waits 8 seconds
  (`home_wait_seconds`) and starts mining again. Set your home first: `/sethome mine`.
- **Danger:** health at 4 hearts or less, lava or fire also sends you home instead of stopping (`danger_home=false` turns it off). At most once a minute.
- **Auto reconnect:** if the server kicks you or the connection drops, it rejoins the same server (every 15 s, up to 40 tries),
  waits 10 s, then uses `/home mine` and only starts mining once that has really moved you (new world or 50+ blocks away); it retries up to 15 times.
  Quitting to the menu yourself does not trigger it.
- **Money tracker:** the HUD shows ore mined this session, what it is worth, money per hour, and an all-time total
  (`config/zenkaiorecycle-stats.properties`, delete it to reset). Value = ore / 9 x block price, using the prices you last used in
  the Market Seller menu (or `gold_block_price` / `coal_block_price` in the config). It is an estimate of what blocks would sell for.
- **HUD:** a compact panel (state, ore, idle timer, pickaxe bar, hunger, rounds, money). It sits at the left edge, in the middle of the
  screen, so it stays out of the way of other overlays. Change `hud_position` (`left_middle`, `top_left`, `top_right`, `right_middle`) and
  `hud_scale` (default 0.85) in the config. It only shows while the loop is running and disappears when you stop it. Hidden with F1.

## Keys (rebind under Options > Controls > Zenkai Ore Cycle)
- **=** start / stop the loop
- **X** stop everything and send `#stop`
- **C** drop everything in slots 2-36 except raw gold, coal and their blocks (same rule as above, on demand)

Keys do nothing while chat or a menu is open.

## It stops by itself (and tells you in chat) when
you die; `/craft` or `/vault` does not open what it expects; the crafting recipe is not unlocked; a vault is full;
or the inventory is still nearly full after storing everything.

## Settings
`config/zenkaiorecycle.properties` is created on first launch: `vault_command`, `craft_command`, `spare_ore` (45),
`mine_gold_command`, `mine_coal_command`, `stop_command`. Edit and restart the game, no rebuild needed.

## Build
Push this folder to a GitHub repo. `.github/workflows/build.yml` builds it and publishes the jar as the
`zenkaiorecycle-jar` artifact on the Actions tab. Or run `gradle build` with Java 17 and take the jar from `build/libs/`.
