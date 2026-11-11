# Styles: how the CSS is layered

Load order is set in `src/main.ts`. Later layers win ties, so keep each layer to its job.

| Layer | File | Job |
| --- | --- | --- |
| 1. Base | `styles/main.css` -> `base.css` | Fonts, reset, design tokens on `:root` (palette, radii, shadows, container width). |
| 2. Legacy theme | `styles/therapist-theme.css`, `responsive.css` | Older shared staff layout. Avoid adding to these. |
| 3. UI theme | `styles/app-theme.css` | Loaded last, selectors prefixed with `#app`. Navbar, buttons, cards, titles shared by every staff page. |
| 4. Feature | `features/<name>/styles/*.css` | Page-specific rules, imported by the view (`<style scoped src>` preferred). |

## Palette tokens (`base.css`)

Use these instead of hex values: `--color-primary`, `--color-primary-strong`, `--color-ink`,
`--color-muted`, `--color-border-soft`. Radii: `--radius-*`. Shadows: `--shadow-*`.

## Feature file naming

Name files by what they style, not by role or theme era (no new `therapist.css`):

- `patients/styles/patient-workspace.css` - shared shell and directory rules for patient pages
- `staff/styles/staff-header-theme.css` - staff header overrides
- `appointments/styles/appointment-theme.css` - appointment page palette

Staff and manager views share one stylesheet per page. Put role-only differences in a small
separate file instead of copying the whole file.

## Known debt (not yet fixed)

- Rules for the same selector are split across base, feature and `app-theme.css`; `#app` overrides in `app-theme.css` exist to win those ties.
- `body:is(.therapist-ui, :not(.therapist-ui))` is a specificity trick; do not copy it.
- About 130 `!important` declarations, mostly in `main.css`, `responsive.css`, `app-theme.css`.
- `appointments.css`/`manager-appointments.css` and `services.css`/`manager-services.css` are near-duplicates.
