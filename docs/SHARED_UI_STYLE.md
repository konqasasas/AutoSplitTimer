# Shared UI Style

This is the common visual language for my Minecraft mod tools. It should make separate mods feel as if they were made by the same person. Apply it to the new mod without copying AutoSplit Timer's screen structure or adding unnecessary features.

## Direction

- A restrained professional editing tool, built for frequent use.
- Angular and compact, with straight edges and square controls.
- Quiet hierarchy and clear state changes. Avoid decorative dashboard styling.
- Keep the number of panels, labels, dividers, and persistent controls low.
- Tooltips and hover/focus feedback should explain unfamiliar controls without filling the screen with help text.

## Foundation

```css
:root {
  --canvas: #101214;
  --surface: #171a1d;
  --surface-subtle: #131619;
  --control: #20262b;
  --control-hover: #293139;

  --line: #30353a;
  --line-strong: #3a4045;
  --line-control: #52606b;

  --text: #dfe3e6;
  --text-strong: #f2f4f5;
  --text-muted: #969fa6;
  --text-faint: #747e86;

  --start: #58b96c;
  --lap: #c8a348;
  --goal: #d65c63;
  --focus: #8aa5ba;
}
```

Use start green, lap yellow, and goal red only for their meanings and closely related success/pending/error states. Most of the interface stays neutral.

## Typography

- UI: `IBM Plex Sans`, then `BIZ UDPGothic`, `Yu Gothic UI`, sans-serif.
- Times, coordinates, IDs, and numeric values: `IBM Plex Mono`, then `Consolas`, monospace.
- Main title: 24px / 600.
- Normal labels and body text: 11–12px.
- Small section label: 10px / 650 / uppercase / `0.14em` letter spacing.
- Do not use oversized headings, gradients in text, or marketing copy.

## Shape and spacing

- Default corner radius: `0`.
- Primary surfaces use a 1px border. Prefer dividers and alignment over many nested cards.
- Typical row height: 40–44px. Typical compact button height: 28–32px.
- Common horizontal padding: 20px. Use an 8px-based spacing rhythm where practical.
- Shadows are sparse and functional; one floating window may use `0 18px 48px #0008`.

## Interaction

- Hover: slightly brighten the surface and border; avoid glow, bounce, and large movement.
- Keyboard focus: a visible 1px cool-gray outline with a 2px offset.
- Disabled controls lose contrast and do not animate.
- Status indicators are small square marks. Do not rely on color alone; always pair them with text.
- Prefer one clear primary action per context. Put secondary actions nearby only when they are needed.

## Avoid

- Large rounded cards, pill-shaped controls, glassmorphism, neon glow, and gradients.
- Dense dashboards, excessive metadata, decorative charts, permanent help panels, and large footers.
- Too many accent colors or color applied to whole panels.
- Generic AI dashboard layouts and verbose explanatory copy.

The exact layout should follow the new mod's workflow. Reuse this visual system and interaction behavior, not AutoSplit Timer's information architecture.
