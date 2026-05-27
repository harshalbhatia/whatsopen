# Manual TalkBack verification checklist

Static a11y audit (Task G) is complete. The following items require running the app with TalkBack on a device/emulator.

Run TalkBack: `Settings → Accessibility → TalkBack → On`. Use volume-up + volume-down to toggle.

## CallLogCard composite
- Swipe to a row. Verify announcement reads as ONE node in this order: call type ("Incoming call") → "from" → country code → phone number → date.
- Verify the inner "Chat" button is NOT independently focusable (it is merged into the card by `semantics(mergeDescendants = true)`).
- Verify single double-tap activates "open chat".

## FilterChip state
- Tap each chip (Incoming, Missed, Non-contacts) and confirm TalkBack announces "selected" / "not selected" (or "checked" / "not checked") on each toggle.
- Verify label is re-announced when state changes.

## Non-contacts permission flow
- Revoke contacts permission. Turn on TalkBack.
- Focus the Non-contacts chip. Verify the announcement includes "tap to grant contacts permission" (via `filter_non_contacts_needs_permission` string).
- Tap chip. Verify the system permission dialog announces correctly.

## PermissionCta button focus
- On CallLogs screen with permission denied, swipe through. Confirm focus order: title → description → "Grant Permission" button.
- Verify the decorative call-log icon (alpha 0.5) is skipped (not focused).

## ByNumberScreen submit
- Verify "Start Chat in WhatsApp" button announces label correctly with the leading icon merged (icon `contentDescription = null`, Text labels it).
- Submit empty fields. Verify error supportingText is announced (TextField's `isError = true` should auto-announce).

## ClipboardScreen error
- Submit empty input. Verify supportingText announces the error message when field gains focus.

## Touch target sizes
- Enable Developer Options → "Show layout bounds".
- Confirm all `FilterChip`, `Button`, and icon-bearing interactive elements render ≥48dp tap area.

## Color contrast
- Install Accessibility Scanner from Play Store.
- Run on each screen.
- Watch for: `onSurfaceVariant` body text contrast, empty-state icon at `alpha(0.5f)`.

## Pseudolocale RTL/layout
- Enable Developer Options → "Pseudo locales" → "en-XA" or "ar-XB".
- Verify `call_log_card_a11y_description` template still reads naturally (translators may reorder `%1$s from %2$s %3$s, %4$s`).
- Verify country-code prefix layout doesn't break in RTL.

## Predictive back
- On Android 14+ device. Long edge-swipe inside CallLogs screen.
- Verify preview animation shows the previous screen.

## Splash
- Cold-launch the app. Verify branded splash with `ic_launcher_foreground` on `md_theme_*_primaryContainer` background appears briefly before app UI.
- Verify dark mode uses `md_theme_dark_primaryContainer`.

## Filter live region (recommended enhancement, not implemented)
- When filter chips change the visible list, AT users get no announcement that the list grew/shrank.
- Consider adding `Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "N results" }` above the LazyColumn.
- Not a regression — original fragment had the same behavior.
