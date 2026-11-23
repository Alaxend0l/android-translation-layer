# About this fork

This hard fork is now named **Andrun** (renamed 2026-11-23). It remains
an **unofficial, AI-assisted hard fork** of
[Android Translation Layer](https://gitlab.com/android_translation_layer/android_translation_layer)
(ATL) and is not affiliated with, endorsed by, or supported by the
upstream ATL developers, postmarketOS, or Google. "Andrun" is a new
project name, chosen under GPLv3's freedom to rename (upstream copyright
and license notices are untouched); it evokes "Android" without using the
mark. A name scan found one unrelated dormant app ("andRUN", a 2012
one-button running app, `com.sugart.andrun`) - no relation, no overlap
with what Andrun is. The build outputs keep ATL's names (binary
`android-translation-layer`, `atl-*` tools) for now; renaming those is a
separate mechanical change (harness/runner paths depend on them).

Origin and continued credit: everything in `src/` builds on the upstream
ATL project by the postmarketOS developers.

- Changes here are made with the help of AI coding agents, driven by the
  `atl-harness` compatibility harness. Upstream has a no-AI-contributions
  policy, so **nothing from this fork is ever submitted upstream**: no merge
  requests, no issues, no bug reports about fork-only behaviour. Please do
  not report problems with this fork to upstream.
- Upstream changes are never fetched, merged or rebased here: the hard
  fork is **complete** (2026-11-23) — no code flows in either direction,
  and work is based on this repository's own lineage, not on upstream's
  development.
- Licensing is unchanged: ATL is GPLv3 (`LICENSE.txt`); upstream copyright
  notices are kept. Fork-specific changes are under the same license.
- Branch `main` = upstream `master` at the fork point (`22a96b28`) plus
  fork commits; from the fork point on, `main` and `local-fixes` are
  pure Andrun lineage.
