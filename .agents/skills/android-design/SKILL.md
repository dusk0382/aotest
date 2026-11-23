---
name: android-design
description: >
  Material 3 Expressive design judgment for Android apps in Jetpack Compose. Load it for any task that
  decides how an Android screen looks, even when the request reads as plain Compose coding: building or
  polishing a screen, reviewing UI code or screenshots, setting up a theme (color, typography, shape),
  choosing components, adapting layouts to tablet, foldable, and desktop windows (list-detail, supporting
  pane, what fills the space), Wear OS app screens, and adding motion. It owns the design decisions: load
  it first, then any skill that covers the APIs. Wear tiles, watch faces, and widgets are other skills'
  work.
---

# Android Design

Material 3 Expressive design decisions for Jetpack Compose. Expressive is Material 3 plus opt-in APIs, components, and tactics ("expansion pack").

Through-line: **spend emphasis deliberately.** Rank what the screen is for, then spend size, color contrast, shape, type, containment, and motion on the top goal, less on the next, and almost none on the rest. Uniform emphasis produces a **template** (catalog components assembled rather than composed).

Rules marked **(Google)** come from Google's guidance; rules marked **(default)** are this skill's starting points unless the direction justifies another choice.

**Surfaces.** Phone, tablet, desktop, and Wear OS app screens. For Wear OS, read [references/wear-os.md](references/wear-os.md) first (it overrides this file where they differ); use `wear-compose-m3` for scaffolding and migration. Wear tiles, watch faces, and home screen widgets belong to `wear-widgets` and Glance skills.

## Workflow

1. **Route** the request: proposal first, or straight to code ([Route](#route)).
2. **Answer the [design questions](#design-questions)** in your reply before any code.
3. **[Compose the screen](#compose-the-screen)** from those answers.
4. **Build** with the [reference](#reference) below until the screen compiles and renders.
5. **Run the [screen check](#screen-check)** on the rendered result.

### Route

When the request cares about the design of a new app, new screen, or redesign (polish, visual direction, Material 3 Expressive, or naming this skill), start with an exploration even if it says "build": follow `android-design-explore`, verify and show its live board via `android-design-preview`, and wait for approval before wiring Compose into the app. Skip exploration only if the user says so ("skip the proposal", "just code it") or an approved exploration already exists (port it via `android-design-explore`).

Plain builds that do not ask about design, and changes inside an existing design (fixes, tweaks, reviews, new states of an approved screen), go straight to code through the design questions and screen check. UI Builder requests follow `compose-ui-builder` while this skill still owns design decisions.

### Design questions

Answer all seven in your reply before writing code (1 to 2 sentences each):

1. **Rank the goals.** Name the single primary task, plus secondary and tertiary ones (Google: "Simplifying to one primary task on each page").
2. **Choose a direction.** State the emotional character, defining content or interaction, and one concrete visual idea uniting type, imagery, color, and shape ("the total set in tall numerals fills the top third", not "modern, clean"). If it matches how most apps in the category already look, it is not an idea yet.
3. **Decide the hero moment.** Is this one of the product's one or two hero moments? If yes, plan how tactics combine ([Hero moments](#hero-moments-and-restraint)). If no, keep it calm (Google).
4. **Choose the color source.** Dynamic (wallpaper), brand (generated scheme), or content-based (from an on-screen image) ([Color](#color)).
5. **Spend emphasis by rank.** Primary gets the most, usually size first; secondary gets fewer tactics; tertiary gets containment and type (Google). Content can dominate when it is the product, as long as the primary action stays unmistakable.
6. **Pick components from the [Components](#components) table**, not from older Material memory.
7. **Answer the adaptation question.** For each size class you render (compact; medium or expanded; large or extra large), name what extra space reveals (second pane, expanded rail, more detail) using the five questions in [references/layout.md](references/layout.md). Keep navigation consistent across breakpoints (rail on larger windows pairs with a navigation bar on compact). Control screens reflow and reveal content; never just stretch phone content wider (Google).

### Compose the screen

Components come last. Compose from the direction and content first:

1. **One element leads.** Pick what fills the most space (content, primary control, or key summary) and arrange everything else around it with empty space to focus attention (Google).
2. **Space follows rank.** The lead gets scale, ample space, and the brightest surface mapping (Google); supporting data packs compactly; tertiary details sit at edges or behind a tap.
3. **Type can be the structure.** A display title or numeral can anchor the layout with no card around it, using weight, size, color, and spacing for editorial moments (Google).
4. **Group by spacing or containers deliberately.** Proximity and alignment group implicitly and keep screens open; containers and surface steps group explicitly and add weight (Google). Use containers when a group must read or tap as one unit; use spacing for flowing lists. When containment marks list state (unread, pending), gather items in that state under one heading and container group, and put the rest as plain rows under their own heading; alternating contained and flat rows reads as stripes (default).
5. **Vary form by information type.** Match grid to content: hierarchical for editorial/detail, modular for equal items, column for flowing lists ([references/layout.md](references/layout.md)). Repeating one card shape everywhere creates the template look.
6. **Let content set color.** When an image or subject leads, derive screen color from it ([Color](#color)) instead of adding unrelated accents.

Keep familiar interaction patterns underneath. For shipped examples (metric card, editorial header, pill hero control) read [references/components.md](references/components.md#patterns-from-shipped-expressive-apps) after choosing the composition.

### Screen check

For an exploration, use `android-design-explore`'s Check step instead of rendering here.

Judge the rendered screen, not the code. Render via `compose-preview` (or `compose-ui-builder` for UI Builder outputs). If rendering is unavailable or fails, report "visual quality unverified" and refer to this repo's README setup instead of guessing from code or substituting renderers.

- Does the eye land on the primary goal immediately?
- Does every expressive choice serve the direction?
- Does color reinforce hierarchy and atmosphere, with accents only on elements that have a job?
- Are supporting actions easy to find and clearly tappable? Text-only buttons belong inside a clear action group (dialog, card, snackbar).
- Does it hold up with real content lengths, 200% text scaling, and a smaller window?
- Would it remain clear with expressive flourishes removed?
- Is it still a template? A screen resembling the Material component catalog is not done.

Fix the largest mismatch and re-capture; for stateful screens, also capture the alternate state that changes emphasis or layout. Stop after two fix rounds and report what remains.

Code checks:
- [ ] `MaterialExpressiveTheme` with chosen color source, Google Sans Flex for plain text, and at most one bundled brand face on Display, Headline, and Title Large.
- [ ] Components from the right column of [Components](#components).
- [ ] Intended color role pairs only: no `copy(alpha = ...)`, no hard-coded hex colors.
- [ ] Custom animations use `MaterialTheme.motionScheme`, not `tween()`.
- [ ] Text fields use `TextFieldState` (`rememberTextFieldState()`) with `SecureTextField` / `OutlinedSecureTextField` for passwords, and search bars back `SearchBarDefaults.InputField` with `TextFieldState`.
- [ ] Show/hide uses `AnimatedVisibility`, `AnimatedContent`, or conditional composition (never `alpha = 0f` alone); text over blurred images still sits on a scrim, gradient, or solid/tonal surface.
- [ ] Platform semantics match [Android conventions](#android-conventions); content draws edge-to-edge.

## Reference

### Theme setup

Wrap the app in `MaterialExpressiveTheme` with `MotionScheme.expressive()`, the chosen color scheme, and the type scale from [Typography](#typography). Requires `androidx.compose.material3:material3` **1.5.0** or later (Compose BOM `2026.11.01`; 1.4.0 lacks almost all Expressive APIs). Read [references/theming.md](references/theming.md) for theme code, toolchain requirements, opt-in APIs, seed schemes, type voices, shape morphing, motion specs, blur and shadows, and spacing.

### Emphasis and hierarchy

Deliberate hierarchy makes key actions faster to find and reduces errors (Google). Worked example across four screens: [references/components.md](references/components.md#worked-example-aura-breathing-app).

- **Size is the loudest lever, and it is relative.** The primary CTA leads its peer actions, whatever their size (content like a summary card or list row can be larger). Start from the default size with the strongest fill (small button; medium FAB by default) and grow only enough to lead; one focal point per screen. Grouped actions share one size by default, with the primary leading by fill, width, or shape. Reserve XL for control-led hero screens (player, breathing session). Never wrap or truncate button labels, and never make a container narrower than its label: shorten to 1 to 3 words, give more room, or step down a size across breakpoints (Google).
- **Placement:** put the key action where hands and eyes already are (e.g., low in vertical flow or above the keyboard) based on the interaction and content space (default).
- **Strongest fill on the primary action only.** Filled buttons sparingly; secondary actions use tonal or standard; toggles show state via filled icon or tonal container.
- **Contrast between roles:** `primary` for main action, `secondary` / `secondaryContainer` for supporting controls, `tertiary` for status or accents. Using `primary` everywhere flattens hierarchy.
- **Containment:** group related items in containers; give top content ample space and the brightest surface.
- **Summaries support the task, not outrank it.** On action-list screens (habits, inbox), keep the next actionable item most inviting. Give actionable rows clear containers, let completed items recede (quieter color, no strong fill), and group by state rather than interleaving ([Compose the screen](#compose-the-screen), step 4). Reserve selection highlights for user choice, not completion.
- **Order of work:** build hierarchy first (size, color, spacing, placement, containment), then add unique emphasis (shape, scale, morph).

### Hero moments and restraint

Hero moments layer multiple expressive tactics (type, shape, color, motion, flexible components) on the product's one or two most emotional, central interactions (player, dialer, completed goal, breathing session). Secondary screens stay calmer (Google).

- **One custom thing moves:** keep text stable while a hero shape animates (built-in component press morphs do not count).
- **When content is the product, content is the hero:** give album art or photos scale and let their color set the screen. Library-shape masks, full bleed, or layered titles are optional; a rounded crop works when the composition carries the idea.
- **Keep familiar patterns:** Expressive styles patterns rather than replacing them (keep playlists recognizable, keep labels where clarity needs them, match domain tone such as banking vs media). Clarity beats emotion every time (Google).

### Color

- **The generator builds the palette; you assign roles.**
  - *Dynamic* (wallpaper): Google's recommendation for personalization (M3 Expressive boosts chroma automatically). Identity comes from hierarchy, shape, and type.
  - *Brand*: generated from brand seed via Material Theme Builder or MaterialKolor at default `TonalSpot` style ([references/theming.md](references/theming.md#color-schemes)). Do not use MaterialKolor `PaletteStyle.Expressive` (it shifts hues away from the brand and is unrelated to M3 Expressive).
  - *Content-based*: colors a contained area from a visible on-screen image; cap a screen at two color sources (Google).
  - `expressiveLightColorScheme()` is only the light fallback when dynamic color is unavailable.
- **Accents mark meaning by rank.** Primary for crucial actions (FAB, main CTA); secondary for selection and supporting controls; tertiary for status or delight. Use vibrant accents sparingly.
- **Surfaces cover most of the screen.** Build hierarchy with `surface`, `surfaceContainer` (navigation), and the five container levels (`surfaceContainerLowest` to `Highest`, brightest on the most important container). Put accent containers only on elements with a job, never on every card.
- **Role pairs only, never alpha hacks.** Pair `onX` on `X`; `onSurface`, `onSurfaceVariant`, and `primary` on surfaces; `inverseOnSurface` and `inversePrimary` on `inverseSurface`. Containers are fills, never text colors. Lower emphasis via quieter roles (`onSurfaceVariant`), never `copy(alpha = ...)`. Dividers use `outlineVariant`; text field borders use `outline`.
- **Surface steps & vibrant styles:** follow each component's surface mapping (e.g., search containers more than one step apart). Vibrant styles are high emphasis and used sparingly: vibrant menus use tertiary; vibrant toolbars use `primaryContainer` ([references/components.md](references/components.md#toolbars)).
- **Semantic and inverse colors:** error/success colors never come from content color and keep consistent meaning app-wide (Google). `inverseSurface`/`inverseOnSurface` reverses one standout element (snackbar, key total).
- **One strong fill: the hero, or one coherent group** (default). Unrelated saturated cards side by side create noise. A hero screen still mixes two or three distinct accent container roles (`primaryContainer`, `secondaryContainer`, `tertiaryContainer`, or inverse) for key elements with distinct jobs while everything else stays on surfaces. Count what renders saturated: read rendered hex via `android-design-preview check` (`colors`) or `android-design-preview tree`, and move any overly loud container to a surface or quieter role.
- **Category colors:** two categories can use `primaryContainer` and `tertiaryContainer` (not secondary, which shares primary's hue); more need unharmonized static colors ([references/theming.md](references/theming.md#extra-colors-semantic-and-categories)), never improvised hex. Color the badge or icon, not a whole card, and use the same role for every mark of that category.
- **Charts and data graphics:** use solid roles (`primary`, `secondary`, `tertiary`, or static color main role) for 3:1 contrast against background, not pale containers.

### Typography

- **Role scale:** Display (short key text/numerals), Headline (short high-emphasis on phones), Title (secondary regions), Body (reading), Label (components). Use about five clearly contrasted styles per screen.
- **Emphasized styles** (`displayLargeEmphasized` through `labelSmallEmphasized`): apply to primary button labels, selected/unread items, key numbers, and headlines.
- **Google Sans Flex by default and always for plain text** (Title Medium/Small, Body, Label) on phone, tablet, and desktop (this skill's standard). Six axes (`wght`, `wdth`, `opsz`, `slnt`, `GRAD`, `ROND`); see [references/theming.md](references/theming.md#google-sans-flex).
- **Optional single brand face on Display, Headline, and Title Large:** choose at most one bundled face (`Fraunces`, `Bricolage Grotesque`, `Big Shoulders Display`, or `Doto` at Display only) when it serves the direction ([references/theming.md](references/theming.md#brand-faces-display-headline-and-title-large-only)). Keep `titleLarge` in the brand face too (small app bars and side sheets use it). Never use brand faces on Title Medium/Small, Body, or Label.
- **Google Sans Code for numbers and data** (timers, tabular values, codes, metadata) at any size.
- **Preserve scale sizes:** adjust line height and tracking for brand faces, not type sizes (Google).
- **Voice discipline:** give Display/Headline a real voice (strong weight, width, or roundness in Google Sans Flex, or purposeful brand face styling); keep Body and Label on default axes. Always match `opsz` to text size. Reuse two or three named voices per app rather than random axis values. Axes can signal state (heavy selected, light unselected at equal size; `GRAD` emphasizes without changing width).
- **Editorial techniques:** contrast two voices in one line for meaning (e.g., light condensed origin vs heavy wide destination); for decorative cropped/tinted background type, repeat the info readably and hide the graphic via `Modifier.clearAndSetSemantics {}`; align huge numeral + small unit in a `Row` with `Modifier.alignByBaseline()`.
- **Line height & casing:** ~1.2x on large styles (~1.25x for heavy/narrow custom voices), 1.5x on body; `fontFeatureSettings = "tnum"` for changing numbers; sentence case everywhere.

### Shape

- **Corner scale:** 4, 8, 12, 16, **20**, 28, **32**, **48**dp and full (`extraSmall` to `extraExtraLarge`; bold are Expressive additions). Buttons default to full; cards to medium.
- **Tension creates focus:** mix round and square to highlight the focal point. In a control cluster, contrast the primary control's shape with its neighbors (default: rounded square among circles, or vice versa).
- **Shape signals state:** use Expressive components' `shapes` parameters so buttons, icon buttons, toggles, and list items morph on press and selection.
- **Shape library (`MaterialShapes`):** 35 shapes for avatars, image masks, decorative graphics, achievements, and bespoke hero buttons; not text-heavy containers. Abstract shapes work freely as masks on photos/album art/avatars (Google). Standalone decorative graphics must depict something from the subject (sun, cloud, record label, never meaningless blobs) and keep recognizable static colors ([references/theming.md](references/theming.md#extra-colors-semantic-and-categories)). Clip and morphing recipes: [references/theming.md](references/theming.md#shape-library).
- **Uniform peers & nesting:** comparable data (metrics, calendar days) shares shape, size, and spacing without overlap (Google). Nested corner radius = outer radius minus padding (28dp card with 12dp padding holds 16dp corners). Segmented lists use 4dp inner, 16dp outer corners (selected morphs to 16dp all around).

### Motion

Spring physics themed through `MotionScheme` (never `tween()` for custom components):

- **Spatial** specs (position, size, rotation, corners) overshoot and settle; **Effects** specs (color, opacity) never overshoot.
- **Speeds:** fast (small controls), default (sheets, rails, partial screen), slow (full screen).
- **Schemes:** `MotionScheme.expressive()` by default and on hero moments; `MotionScheme.standard()` for utilitarian products (override a subtree with nested `MaterialTheme(motionScheme = ...)`). Springs stay interruptible and carry velocity. Code snippets, spring values, and `ReducedMotionScheme` (`snap()` specs, drop decorative morphs/parallax, keep meaningful fades): [references/theming.md](references/theming.md#motion).
- **Screen transitions stay simple:** container transform only for hero moments in shallow hierarchies; platform forward/backward for everyday hierarchy; quick full fade-out then fade-in between top-level destinations (never lateral slides; never fade a bottom sheet) (Google).

### Spacing and layout

- **Spacing sets mood:** denser feels focused, spacious feels calm; negative space around the lead adds emphasis (Google).
- **8dp scale** (4, 8, 12, 16, 24, 32, 48). Put padding/gaps on parents, not margins on children. Side margins: 16dp compact, 24dp medium and up.
- **Group with gaps and containers over dividers:** dividers belong only in uncontained lists. Keep reading text to 40 to 60 characters per line.
- **Breakpoints (600 and 840dp):** compact uses one pane + navigation bar; expanded and up use two full-height panes + navigation rail, revealing what your adaptation answer named. Read [references/layout.md](references/layout.md) for every multi-size layout (breakpoints, tall medium windows, canonical layouts, landscape, desktop mouse/keyboard).

### Components

Stock code reaches for the left column; lint will not flag it.

| Instead of | Use | Compose |
| --- | --- | --- |
| Medium/large top app bar | Flexible app bar (shorter, bigger title, subtitle) | `MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar` |
| Bottom app bar | Floating or docked toolbar | `HorizontalFloatingToolbar` (FAB overload), `FlexibleBottomAppBar` |
| Segmented buttons | Connected button group | `Row` of `ToggleButton` with `ButtonGroupDefaults.connected*ButtonShapes()` |
| Navigation drawer | Expanded navigation rail | `WideNavigationRail`, `ModalWideNavigationRail` |
| `NavigationBar` | Flexible navigation bar | `ShortNavigationBar` + `ShortNavigationBarItem` |
| Small FAB, speed dial | Medium/large FAB, FAB menu | `MediumFloatingActionButton`, `FloatingActionButtonMenu` |
| Spinner for short waits | Loading indicator (200ms to 5s) | `LoadingIndicator`, `ContainedLoadingIndicator` |
| Plain progress bar | Wavy progress where expressiveness fits (default strokes) | `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator` |
| `ListItem(headlineContent=)` with dividers | Segmented expressive list | `SegmentedListItem` spaced by `ListItemDefaults.SegmentedGap` |
| Flat dropdown menu | Grouped vertical menu | `DropdownMenuPopup` + `DropdownMenuGroup` |
| One-size buttons | Sized, morphing buttons (XS 32 to XL 136dp) | `Button(shapes = ButtonDefaults.shapesFor(height))` |

- **App bar vs toolbar:** app bars support navigation plus 1 (at most 2) essential actions, boosted with one filled/tonal icon button. Page controls go in a floating toolbar of icon buttons (never loose text buttons); local navigation uses a labeled floating toolbar with one selected pill, never alongside a navigation bar ([references/components.md](references/components.md#toolbars)).
- **One FAB per screen** for the top screen-wide action only; never on individual cards or minor actions (Google).
- **Button groups:** same size and shape by default; mixed sizes only in hero moments.
- Read [references/components.md](references/components.md) when choosing, writing, or customizing components.

### Accessibility and writing

- Contrast: 4.5:1 small text, 3:1 large text, icons, and controls. Generated/static schemes must honor system contrast ([references/theming.md](references/theming.md#contrast-levels)). Pair color with icon, label, or underline for every state/link.
- Touch targets 48dp with 8dp separation. Support 200% text scaling (text grows, padding/icons/controls do not, layouts reflow). Ellipsize only when full text is reachable (tooltip, link, expanded view); otherwise wrap (Google).
- Semantics in Compose (Google): section titles take `Modifier.semantics { heading() }`. A row that toggles carries `toggleable` or `selectable` with a `role` (or `SegmentedListItem(onClick = ...)` with `Modifier.semantics { role = Role.Switch; toggleableState = ToggleableState(checked) }` for switch rows, or its `checked` / `selected` overloads), and its `Checkbox`, `Switch`, or `RadioButton` gets a `null` callback, so the row is one 48dp target. Clickables, buttons, and `ListItem` already merge their children; don't add `mergeDescendants`. Decorative or repeated text uses `hideFromAccessibility()`; `clearAndSetSemantics` only when merging can't describe the control. Custom `Canvas` controls need semantics added by hand.
- Content descriptions state purpose ("Voice search"), not appearance or role. Sentence case, second person, no periods on single-sentence labels, contractions, exclamation points only for real celebrations.

### Android conventions

Avoid iOS habits (Google, "10 steps to Android"):

| iOS habit | Android |
| --- | --- |
| Centered navigation title by habit | Start-aligned title by default (centered only when composition calls for it); flexible app bars collapse on scroll |
| Back chevron; "Cancel" and "Done" text in top bar | Up arrow for hierarchy (system back handles back); close icon for full-screen modals with confirm action in app bar; dialogs keep labeled text buttons |
| Action sheets | Bottom sheets |
| Segmented control | Tabs for peer destinations; connected button group for options/view modes within one screen |
| Table rows with disclosure chevrons and hairline dividers | List items without chevrons, grouped with gaps ([Spacing and layout](#spacing-and-layout)) |
| SF Symbols | Material Symbols, one style (outlined, rounded, or sharp) app-wide |

- **Edge-to-edge:** backgrounds, images, and scrolling content draw behind system bars; text and controls stay inset; gesture bar transparent; status bar transparent unless content scrolls under it; tap targets clear of gesture insets; inputs pinned above keyboard (`edge-to-edge` skill).
- **Settings, onboarding, sign-in, permissions, notifications:** read [references/android-conventions.md](references/android-conventions.md) when touching any of these.
- **Support landscape, window resizing, and light/dark themes** on every screen; never lock orientation or theme.
