---
name: android-design-explore
description: >
  Explore Android app ideas as live Compose proposals in the previewer app before implementation, without
  changing app code. Use to brainstorm phone, tablet, desktop, or Wear OS app screens, mock up redesign
  alternatives, or compare and iterate on visual directions in chat. Not for Wear tiles, watch faces, or widgets.
  Builds on the android-design skill, which owns every design decision.
---

# Visual design exploration

Follow `android-design` for every design decision: its design questions, components, theming, layout, and visual criteria. This skill owns the exploration loop and how you talk to the person. Phone, foldable, tablet, desktop, and Wear OS app screens are in scope; Wear tiles, watch faces, and widgets are not.

## Tooling

Run `android-design-preview` from the shell (`android-design-preview <command> --help` documents every flag, exit code, and JSON field). When it is not on `PATH`, run this skill's bundled copy instead: `sh <skill-dir>/scripts/android-design-preview`, where `<skill-dir>` is the absolute path of the folder holding this `SKILL.md`. Every `android-design-preview` command below means whichever of the two works.

- Commands: `check`, `tree`, `selection`, `open`, `status`, `stop`, `render`, `install`.
- Exit codes: `0` success, `1` compile/render/check failure or no selection, `2` usage or choice, `3` server or prerequisites not ready.
- If the bundled script is missing too, or the command exits `3`, stop, give the user [references/setup.md](references/setup.md), and wait. Never change user sandbox config unasked.

## 1. Brief

When there is an app, read its relevant screens, theme, assets, and any existing `DESIGN.md` and `design/*.explore.kt` first. Answer `android-design`'s seven design questions in your reply before writing code:

- Pick one seed color from the brand or subject (a dish, an album, a trip), never the default `#6750A4`.
- Name the three or more layered tactics for any hero moment.
- Keep app code and dependencies unchanged.
- Use representative, non-sensitive data and assets. Use image generation only for files inside `design/assets/`, never instead of a Compose board.
- For alternatives, put multiple `direction("...")` blocks in one `.explore.kt` file so they compare side by side, each a distinct composition rather than a recoloring.

## 2. Write

Work in the app's repository root or the folder the user names; never write into this skill's own repository.

- Keep the board's tokens on the board: `designBoard(seed = ..., variant = ..., brandFont = ...)` (or `wearBoard(seed = ...)`). Do not create or edit `DESIGN.md` while exploring, because it records settled decisions: an existing one is a constraint you read in the brief, and it changes only when the person approves a direction (step 6).
- `design/<slug>.explore.kt` holds the board (`design/<slug>-wear.explore.kt` for Wear OS using `wearBoard(...)`).
- Read [references/compose-signatures.md](references/compose-signatures.md) for pinned Material 3 Expressive, Wear M3, and Exploration Kit signatures. Do **not** read `references/porting.md` until the user approves a direction and asks to wire it into the app.
- Follow the kit rules: tag the single primary CTA with `Modifier.testTag("ad:primary")`; keep raw `Color(...)` hex literals and `.copy(alpha = ...)` out of screen code; use `SymbolIcon(...)` and `designPainter("assets/...")`; author one adaptive screen (`listDetail`, `supportingPane`, or `BoxWithConstraints`) instead of duplicating per-breakpoint screens (aim for ~150 to 300 lines per direction).

## 3. Iterate and check

Every `check` writes PNGs on disk and returns their paths in `images`, plus `boardUrl`, layout summaries, and findings (each finding carries its fix `hint` and `reference` anchor in `android-design/references/`). Read a reference section when a finding points to it rather than pre-reading every reference file.

1. **First draft (full check):** run `android-design-preview check <file>` (unscoped) to check every direction, screen, overlay, and view (`light`, `dark`, `foldable`, `tablet`, `desktop`, or `watch` and `watch-large` on Wear).
2. **Targeted fixes (scoped loop):** edit the file, then run ONE clean `android-design-preview check <file> --direction <d> --view <v>` (add `--screen <s>` when a direction has multiple screens) for the single direction and view you changed. Never pipe, chain, or loop `android-design-preview` across multiple views during iteration. Scoped checks always return a 1x PNG path whether findings pass or fail. Call `android-design-preview tree <file> --direction <d> --view <v>` only when a finding needs exact bounds or `file:line`.
3. **Open the image before claiming a fix:** after a visual change, open the single returned 1x PNG path for the changed view with your file-reading tool to verify hierarchy, icon fit, asset legibility, and spacing balance. Never claim a visual fix from `tree` or JSON alone.
4. **Final gate (full check):** before showing the person, run unscoped `android-design-preview check <file>` once more, verify adaptation in `layout` summaries (`tablet`/`desktop` reveal what your adaptation answer promised), check `colors` for one strong fill, and open the returned `light` and `desktop` (`watch` and `watch-large` on Wear) PNGs.
5. **Fix cap:** cap full checks at two fix rounds (scoped iterations between them are uncapped). Report any unresolved findings instead of looping.

## 4. Show

Run `android-design-preview open <file>` on interactive runs (or share `boardUrl` from `check`). In your reply:

- State in one line what the exploration covers and name the tactics layered for hero moments.
- Report any remaining unresolved findings.
- Share the absolute `.explore.kt` path and clickable `boardUrl`, and state `"visual quality checked from layout metrics and native-resolution renders"`.
- Stop and wait for the user's reply.

## 5. Revise

- Whenever the user refers to `"this"`, `"here"`, or a clicked element, check `selection` first (from the latest `check` response or `android-design-preview selection <file>`). If no selection exists, ask the person to click the part they mean on the browser board; do not guess from source. If `selection.stale` is true, confirm the match using `callName` and `callChain`.
- Edit the same `design/<slug>.explore.kt` file (the server watches saves and pushes focus-first SSE updates). Iterate with scoped `check`, open the returned PNG after visual changes, and run a full `check` gate before presenting.

## 6. Approve, port, or delete

- **Record the approved direction:** when the person approves a direction, write its seed, scheme variant, and brand font into `DESIGN.md` at the repo root (create it when missing; change only those shared defaults). This is the only step that writes `DESIGN.md`, and porting reads it.
- **Explore-only:** after recording it, approval ends the task; do not touch app source code.
- **Build / port:** once the user approves a direction for implementation, read [references/porting.md](references/porting.md) and follow the host project's planning, branch, verification, and review workflow.
- **Delete:** remove `design/<slug>.explore.kt` (or `-wear.explore.kt`) when asked; keep `DESIGN.md` unless asked to remove it too.
