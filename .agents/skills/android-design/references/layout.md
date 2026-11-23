# Layout

Adaptive layout for phones, foldables, tablets, and desktop windows, from Material's layout guidance (2026 layout scaffold update). For Compose adaptive APIs in depth (MediaQuery, Grid, FlexBox, Navigation 3 scenes), also load the `adaptive` and `navigation-3` skills.

## Breakpoints

Design for window size, not devices; orientation, folding, split screen, and free-form windows change it at runtime.

**Height counts too.** A window under 480dp tall (a phone or open flippable in landscape) is compact height: it reports medium width, but "two pane layouts are not practical" there, so keep one pane (Google).

| Breakpoint | Width | Panes | Navigation | Margins |
| --- | --- | --- | --- | --- |
| Compact | under 600dp | 1 | Navigation bar (or modal expanded rail) | 16dp |
| Medium | 600 to 839dp | 1 recommended; 2 only for low-density content, 50/50 | Collapsed rail with 1 pane; nav bar with 2 panes | 24dp, 24dp spacer |
| Expanded | 840 to 1199dp | 2 recommended | Collapsed or expanded rail | 24dp, 24dp spacer |
| Large | 1200 to 1599dp | 2 recommended | Rail, collapsed or expanded | 24dp, 24dp spacer |
| Extra large | 1600dp+ | 2 (3 with a side sheet) | Expanded rail | 24dp, 24dp spacer |

Moving up a breakpoint, ask five questions. For control-based layouts like media players, "allow the controls to reflow and content to reveal" (adapt-layout).

1. **Reveal:** what hidden UI can appear (expanded rail, second pane, more list detail)? "Additional space doesn't just mean making the same thing bigger."
2. **Divide:** one pane or two?
3. **Resize:** cards, feeds, lists, keeping text at 40 to 60 characters per line.
4. **Reposition:** move actions from the bottom to the leading edge, add negative space, keep controls reachable (avoid the top 25% of a tablet held in landscape).
5. **Swap:** nav bar to rail, bottom sheet to menu, full-screen dialog to basic dialog, FAB to extended FAB. Only swap functionally equivalent components.

**Use the height of a tall window.** A medium window in portrait (an open foldable or a tablet held upright) is usually one pane (two only for low-density content, as at any medium width): "A layout can have two panes in landscape orientation, and one pane in portrait." Keep the compact layout's vertical order and grow it into the height: "UI elements that are small on compact screens can grow as breakpoints increase," and "Resizing can highlight imagery." Enlarge the hero art or cards, show more list rows, and place supporting content (a supporting pane, up next, details) below the primary content. Grow only until the window is full: the primary action keeps the strongest emphasis (SKILL.md, Emphasis and hierarchy), so it stays on screen with nothing clipped, and art that would push it off scales down instead. A side-by-side composition made for landscape leaves the lower part of a portrait window empty. Google's guides list "Set the same scale on larger screens" as a don't: "take advantage of the space and consider distance from the screen." Content that must keep its aspect ratio should "constrain aspect and balance empty space," sharing the leftover space around it rather than leaving one empty block below. Check every larger view for stretching: a component or a block of content drawn wider or taller than its content needs is a layout that did not adapt.

## Scaffold

- **Bars** frame the page: the app bar at the top, the navigation bar at the bottom, next to system safety regions (status and gesture bars). Content never goes in safety regions (handle insets; see the `edge-to-edge` skill).
- **Rails** surround panes. On compact screens the bottom rail region above the nav bar holds toolbars, chat inputs, and FABs. On large screens the leading rail holds navigation and the trailing rail holds page controls (a vertical floating toolbar).
- **Panes** hold all content: 1 to 3, at least one flexible. Fixed panes are 360dp (expanded) or 412dp (large and up). In split layouts, keep the spacer visually centered even with a rail.
- **Panes fill the window.** Material's examples show "the body pane fills the rest of the window" and "1 pane filling the rest of the window": a pane runs the full height of the body region, whatever its content. In Compose, the panes sit in a `Row(Modifier.fillMaxSize())`, each with `Modifier.weight(...)` or a fixed width and `Modifier.fillMaxHeight()`. Lay the content out to the pane: a list or queue scrolls inside its own pane ("scrolling rows or columns with lazy rows and lazy columns"), hero art grows with the pane, and art that must keep its aspect ratio should "constrain aspect and balance empty space." A row of content-height panes along the top of an expanded window leaves the rest of it empty.
- **Nothing inside a pane stretches.** "Set a max width on content and components to prevent stretching full width"; the don't is "Stretch components, instead allow the component to change presentation and set a max width" (Google). The pane is full height, but a card or container inside it keeps its content's height and a max width, so a short preview or a group of settings is not drawn as a filled block reaching the bottom of the window. At expanded widths and up, a list pane is a fixed pane beside a flexible one rather than a half of the window, so its rows keep a readable width. A detail pane with nothing selected shows an empty state (Google), which is not a layout failure.
- Panes display **co-planar** (side by side), **floating** (like a dialog), or **docked** (like a bottom sheet), and adapt by **show and hide**, **levitate** (become floating or docked), or **reflow** (stack vertically).
- Panes can blend into the background (implicit grouping) or use surface colors (explicit). In multi-pane layouts use surface roles to show which pane is primary, for example `surfaceBright` for the active pane beside a `surfaceDim` rail.

## Canonical layouts

- **Feed:** a grid of cards; one column on compact, more columns as width grows; lead items can span columns to create hierarchy.
- **List-detail:** one pane on compact (list or detail), two panes from expanded. Show the selection state only in two-pane mode and the back button only in single-pane mode; keep scroll position when switching; show an empty state in the detail pane when nothing is selected. Navigation 3: `ListDetailSceneStrategy` with entries tagged `ListDetailScene.ListPane` and `ListDetailScene.DetailPane` gives this plus predictive back.
- **Supporting pane:** primary content about two-thirds; the supporting pane sits below it on compact and medium (a bottom sheet works on compact) and beside it at 360dp on expanded (412dp from large). The default directive behind `SupportingPaneSceneStrategy` and the supporting pane navigator does this; don't hand-split it by percentage. Use it only when the secondary content means something only next to the primary; parent-child content is list-detail.
- **Transitions between one and two panes:** going back to one pane shows the view the person was last in ("If a layout showed the list view previously, it should return to that view"); with nothing selected, two panes show a placeholder detail. Keep read and unread state and scroll position.
- **Never hardcode orientation or window size:** "it's important not to cache or hardcode any values about display size, window size or orientation." Measure the app window (window size classes), not the device, and keep scroll position, entered text, and playback position across rotation, fold, and resize.

## Navigation by size

- Compact: `ShortNavigationBar` with vertical items (icon above label).
- Medium: `ShortNavigationBar` with horizontal items (`NavigationItemIconPosition.Start`) for two-pane layouts, or a collapsed `WideNavigationRail` for one pane.
- Expanded and up: `WideNavigationRail`, collapsed or expanded; the expanded rail replaces the navigation drawer. `ModalWideNavigationRail` for compact screens that need more than five destinations.
- Read the window with `currentWindowAdaptiveInfoV2()`: the deprecated `currentWindowAdaptiveInfo()` reports large and extra large only with `supportLargeAndXLargeWidth = true`.
- `NavigationSuiteScaffold` switches automatically: the navigation bar when width or height is compact or the device is in tabletop posture, the rail otherwise.
- One navigation component per screen; never a rail and a bar together.

## Density and text

- Density is a user choice, not an automatic breakpoint change. Never increase density on focused tasks (menus) or alerts (dialogs, snackbars).
- Support 200% text scaling: text and line height grow, padding stays; stack side-by-side buttons and allow vertical scrolling when text grows.

## Grids and max widths

- Compact screens use a 4-column grid. Pick the grid by content: hierarchical for editorial and detail screens, modular for equal items (galleries), column for flexible one-direction flow. Break it only when content needs it.
- **Compose 1.13 layout primitives:** use `Grid` for 2D structure (give a track holding a lazy list `GridTrackSize.MinMax(0.dp, 1.fr)`), and `FlexBox(config = { wrap(FlexWrap.Wrap); gap(8.dp) })` instead of `FlowRow` or `FlowColumn` for small wrapping sets of chips or badges (`FlexBox` defaults to `NoWrap`) (Google).
- 8dp grid for layout and components, 4dp for icons, type, and small elements.
- Image ratios: 16:9, 3:2, 4:3, 1:1, 3:4, 2:3. Decide per breakpoint whether an image keeps its ratio, changes it, or keeps a fixed height.
- **Never stretch** buttons, text fields, bottom sheets, tabs, or single-pane content across a wide window; give them a max width and change presentation instead (bottom sheet to side sheet, list to cards, FAB to extended FAB).
- **Keep context on large screens:** small menus and edit modals don't cover the whole window, and context menus open next to the item the user selected (Google).
- Pin critical actions and inputs (FAB, message field) instead of letting them scroll away. If content didn't scroll in portrait, it shouldn't in landscape.

## Postures and landscape

- Landscape phones are medium width with compact height: a rail, or horizontal navigation bar items.
- Tabletop (half-folded): content on the top half, large controls on the bottom; suits video and calls.
- Keep text and controls out of the hinge; widen the gutter there. Place dialogs and pop-up menus so they don't overlay the fold (Google).
- Book posture (half open, vertical fold) suits reading long text: split content left and right.
- Cover screens stay focused: hero art as the background, one big anchor control (play), a rail if navigation is needed.

## Desktop windows and input

"More screen, more done": denser, not bigger. Touch, mouse, and keyboard are equally important.

- **Scale:** type one or two steps larger for viewing distance; short copy around 60 characters; reveal more with progressive disclosure as the window grows. Faster motion for small nearby changes, slower or simpler for large movements.
- **Single pane and sheets on desktop:** if keeping a single pane on desktop, cap and center its width rather than stretching phone UI. Replace bottom sheets on desktop with right-click context menus (press-and-hold or overflow), scrimless side sheets (tools and configuration panels), or dialogs (modal decisions in the user's line of sight); any bottom sheet left on desktop must be detached from the bottom edge (Google).
- **Pointer:** every primary journey works with left click alone. Right-click opens a context menu (not long press). Hover shows state, tooltips, and secondary actions (selection checkboxes appear on hover), plus carousel next/previous buttons on non-hero carousels (offer "Show all" persistently); never rely on hover alone. Cursor icons signal what is possible (prohibitive cursor over invalid drop targets); pointer dragging starts immediately with a translucent dragged item and a single-click alternative (such as an upload button). Precise pointers allow tighter targets for secondary hover actions.
- **Keyboard:** Tab follows reading order, arrow keys move within a component, Escape closes temporary UI (it also clears search, deselects all, and cancels an edit without saving), initial focus lands on the key element (search, the primary action, the first field of a task), focus is always visible. Standard shortcuts: Enter sends, Space plays and pauses media, Ctrl/Cmd + click (one-by-one), Shift + click (range), Ctrl/Cmd + A (select all), Ctrl/Cmd + R (with a dedicated refresh button), Ctrl/Cmd + scroll (pan-first zoom; plain scroll on zoom-first maps) plus `+`/`-` zoom buttons; list shortcuts in the Keyboard Shortcuts Helper.
- **States:** custom interactive elements show a visible focused state and a hover state, both with accessible contrast. Show a scrollbar while a mouse or trackpad scrolls (Google).
- **Windows:** support multiple instances, drag and drop between apps, and picture-in-picture for playback; put navigation or search in a custom window header bar where it helps.

| App type | Large-window pattern |
| --- | --- |
| Media | Details and related items beside the player; browse while playing; tabletop viewing; PiP |
| Reading | Two-page spread on foldables; comfortable line length; collapsible notes pane |
| Shopping | Filters in a supporting pane; product detail beside the list |
| Social and chat | List-detail conversations; comments in a supporting pane; Enter sends |
| Creative tools | Movable palettes; context menus; stylus hover, tilt, and pressure |
