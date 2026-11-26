# Kumo — Design system "Dawn Summit"

World: a night sky just before sunrise over a soft mountain. Dark-first ink-navy surfaces; the user's Morning Breeze pastels are the light that rises over them. Light mode is the same world at sunrise: warm cream paper, ink text.

## Color roles (Material 3, `ui/theme/Color.kt`)
| Role | Dark | Light |
|---|---|---|
| background / surface | `#0D1120` ink | `#FFF7D6` cream |
| surfaceContainerLow…Highest | `#131829` → `#2A3150` | `#FFF2C4` → `#EADFB4` |
| primary (butter, energy, CTAs) | `#FFE08A` on `#2B2200` | `#8A6A00` on `#FFFFFF` |
| secondary (sky, calm, progress) | `#A8C6E7` on `#0F2236` | `#3F6A9A` on `#FFFFFF` |
| tertiary (deep sky, career) | `#7FA8D6` | `#2F5E92` |
| accent blush (fitness) | `#F4B6A6` | `#A4513E` |
| accent sage (health) | `#B5D8B0` | `#3F6B3A` |
| error | `#FFB4AB` | `#B3261E` |
Category hues are fixed per domain (career = deep sky, fitness = blush, academics = butter, health = sage, communication = lilac `#C9B8F0`, grooming = peach). Secondary text uses `onSurfaceVariant` tinted toward the hue of the surface, never plain gray.

## Type
Display/headline: **Bricolage Grotesque** (Google Fonts provider, falls back to sans). Body/label: **Figtree**. Numerals tabular (`tnum`) on every metric. Material type roles only; sp everywhere.

## Shape & depth
Cards 24dp radius, chips/buttons full pill, sheets 28dp top. Depth from tonal surface levels; one soft offset shadow only on the floating companion card. No glass, no gradient text, no side-stripe borders, no nested cards. Gradients appear only as sky backdrops behind the mascot and on earned badges.

## Motion (`ui/theme/Motion.kt`)
- `snappy` (damping 1.0, stiffness 500, ~0.28 s response) — taps, toggles, chips, checkmarks.
- `gentle` (damping 1.0, stiffness 200) — cards, rings, sheet content.
- `Spring.bouncy` (damping 0.55, stiffness 300) — celebration only.
- Enter: fade + 12dp rise, exponential ease-out 320ms; stagger 40ms max 6 items. Tab switches fade-through 180ms; pushes 260ms in, 200ms out (exit faster than enter).
- Press: scale 0.97 spring, haptic `TextHandleMove` on toggle, `LongPress` on completion.
- One authored moment per screen. The big one is the Focus Session: bear walks a parallax ridge while the timer runs, then the summit celebration (bear jump with squash/stretch, confetti, points roll-up).
- Reduce motion (system animator scale 0): springs become 0ms snaps, confetti and walking off, crossfades only.

## Mascot — Kumo the bear cub (original)
Drawn in Compose Canvas (`ui/companion/BearCub.kt`), not raster: round head, small round ears, big glossy anime eyes with two highlights, blush cheeks, short muzzle, chubby body. Fur variants: Honey, Cocoa, Snow, Dusk. Gear slots: head (beanie, cap, leaf), neck (scarf colors), back (backpack, cape), face (round glasses), hands (headphones). States: idle (breathing + blink), walk, cheer, sleep.

## Touch
≥48dp targets, ≥8dp gaps. Bottom NavigationBar on compact, NavigationRail ≥600dp.
