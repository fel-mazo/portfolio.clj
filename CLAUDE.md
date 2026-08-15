# CLAUDE.md

Read **[AGENTS.md](AGENTS.md)** — it is the source of truth for this repo: what the project is, the
`bb` task list, the Clojure and ClojureScript architecture, content format, build/deploy, testing,
style, gotchas, and commit/PR conventions. Do not duplicate that content here; edit `AGENTS.md`
instead.

Claude Code specifics:

- Run `npm ci` once per fresh checkout before any `bb` task other than `bb test`.
- There is no single-test runner and no formatter — run `bb test` for the whole suite, and match
  surrounding layout by hand.
- The dev server picks up Clojure, content and CSS edits on the next request — no restart needed.
  Restart only after changing `deps.edn` or a namespace's `ns` form.
- `resources/public/js/` and `dist/` are generated. Never edit or commit them.
