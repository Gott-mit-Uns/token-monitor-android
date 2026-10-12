# Implemented desktop tokens

The fixed v0.68.0 renderer defines the values. Android implementations are `TokenTheme.kt`, `AppearanceTheme.kt`, `DesktopAccount.kt`, `DesktopSettingsUi.kt`, and `DesktopEditControls.kt`; this document describes them and does not replace the source.

- Session running dot: 5dp, left6/top8 source offset and1.5dp panel ring; success #B7EAD4 dark/#18794E light, independent of custom accent. Timestamp growth triggers one1200ms pulse with per-segment ease-out at0/35/100%, blur3/10/4 and opacity0.45/0.95/0.55. Android radial glow is a declared platform adaptation. Token headline retains1000ms updates/800ms period changes. Both share one system duration-scale observer and stop requesting frames in background.

- Home session spinner:10dp drawing, original8 rectangles/900ms linear SMIL opacity values .95/.78/.62/.44/.30/.22/.16/.12, phase step112.5ms. Live rate icon:14dp,620ms cubic-bezier(.22,1,.36,1),42% peak; scale .86/1.13/1,blur0/5/0,opacity0/.68/0. Only fresh active revisions flash; background/reduction settle. Theme shares the resolved system/on/off motion scale.

- Palette: original Default, Obsidian and Porcelain colour maps in DesktopAppearance; primary/line/sunken/overlay roles share the resolved palette. Interface, display and code fonts have separate roles.
- Settings labels 11sp, summary 9.5sp, notes9sp, theme-code and status10sp; labels follow the selected text size and Android font scale. Theme code uses monospace.
- Editing inputs: source1px line,6px radius,7px vertical and8px horizontal padding, sunken alpha0.48. Focus is fixed source blue at0.72 opacity.
- Theme pills:6px gap,4/9px padding,999px radius;10px preset accent dot; selected accent border0.58/background0.08.
- Colour input:30×22 visual swatch,2px inset,6px outer/4px inner radius; inline reset18×18 with10sp glyph. Copy/paste actions30×30,7px radius and13sp glyph; header reset24×24/16sp has transparent chrome.
- Independent touch actions and text fields retain48dp target bounds. The smaller visual chrome is centred inside that area. Colour popups use a mobile RGB/hex editor with confirmation, cancellation and scrolling; the page keeps the original colour-row layout.

No generic Material size may overwrite the source visual size. Actual Android screenshots and interactions, together with the fixed source CSS, determine fidelity; test counts are not a design score.

- Trends main bar colour has a separate role: original `--chart-bar` is #6ab4f0, while `--chart-color` is #73bdf5. Custom chart colours override both; default/reset restores the bar fallback. Do not collapse the two default roles. Trends uses flexible remaining height, source 300×120 coordinates, .5/1 bar opacity and .24/.32 DAY zero markers. Four cards measure actual paragraphs, stretch value/caption to their column width, and draw equal-height backgrounds with source8/10 padding,2gap,8radius/column/row gaps.

- Settings inline choices: original text pills, 4/10px padding, radius999, selected overlay0.12 and text colour; hidden radio remains an accessibility role. List icons22px/13px; tool name11px/500 weight, hidden0.58, selected pin line0.28/background0.06.

- Background slider: original42% width,20dp visual input,2dp continuous accent gradient from0.22 to1,13dp thumb. A separate48dp container exposes one adjustable range and saves accessibility changes; native Slider handles pointer input. Background image controls precede motion and theme; choose/remove use6dp radius,4/7dp padding and control overlay0.049.

- Home module controls: original11sp labels/500 weight and hidden0.58, per-language titlecase text; full slots independent of visibility. New installs hide tools/devices; visual settings targets retain48dp.
- Headline Token transitions: original easeOutQuart,1000ms updates/800ms period changes, interrupted from the displayed value; reduced motion settles immediately and background return does not replay.

- Provider settings: original4dp disclosure shape,6dp success dot,3dp tag gaps,9sp labels with1/5dp padding and5dp radius. Semantic success is #b7ead4 on dark backgrounds and #18794e on light backgrounds, independent of custom accent/chart. Source blue stays #73bdf5. Android targets retain48dp; the button shape must not clip its label/tag column.

- Connection client fields:8dp vertical rhythm; status11sp/500, host10sp/22dp indent; pencil12dp, password paste17sp/30dp visual. Buttons retain6dp radius/7x8dp padding inside independent48dp touch bounds. Draft password masking uses platform PasswordVisualTransformation.

- Colour preview: one transient session overlay for page/charts/vendor palette; last valid colour while input is invalid. Popup retains saved baseline theme. Confirm persists once; cancel/dispose/background clears only its own preview session.

- Session view configuration: one nested divider at line alpha0.14; original11sp label/control row, wrapping when enlarged text no longer fits. View labels11sp/500 and hidden0.58; one mobile order menu, independent48dp controls; disable hiding the last visible view.

- Connection action buttons wrap whole when enlarged text exceeds available width; failure note9sp uses shared error colour and polite live region.

- View reset/show-all: source24px icon container and16px reset glyph/eye shape, with independent48dp targets; header note9sp, default/empty state disabled.
- Provider options: name11sp, usage checklist title10sp and checkbox labels11sp; source switch34x20dp with disabled thumb opacity0.38. Search uses the shared11sp field. Home low-limit values use the current provider colour below50%, source warning#f47788 plus4dp dot below20%; unknown values retain normal text.
