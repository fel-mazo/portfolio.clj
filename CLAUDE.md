# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

Static portfolio site generator in Clojure. Bilingual (French default, English at `/en/`). Markdown blog posts, Hiccup templating, Ring/Jetty dev server, exports to static HTML.

## Commands

```bash
bb dev          # Dev server on :3000
bb test         # Run clojure.test suite
bb export       # Static export to dist/
clojure -M:nrepl  # nREPL on 127.0.0.1:7888
```

No single-test runner configured — run full suite with `bb test`.

## Architecture

Five modules in `src/portfolio/`, strict boundaries:

- **core.clj** — Ring/Jetty server entry point. No content/template logic.
- **site.clj** — URI routing → page rendering. Calls content for data, templates for HTML.
- **content.clj** — Loads/parses `site.edn` config + Markdown posts. Cached via `delay`. Pure data, no HTTP.
- **templates.clj** — Hiccup components. Pure functions, no I/O.
- **export.clj** — Writes rendered pages to `dist/`. Reuses `site` for rendering.

Data flow: `core` → `site` → `content` + `templates` → `export` (for static build)

## Content

- Site config/i18n: `resources/content/site.edn` (EDN with `:locales {:fr {...} :en {...}}`)
- Blog posts: `resources/content/posts/{slug}.{locale}.md` (e.g., `designing-api-boundaries.en.md`)
- Post front-matter is EDN (not YAML): `{:title "..." :date "..." :tags ["..."]}`
- Styles: `resources/public/site.css`

## Routing

- French (default): `/`, `/blog/`, `/blog/{slug}/`
- English: `/en/`, `/en/blog/`, `/en/blog/{slug}/`

## Gotchas

- **No hot-reload for content** — server restart (or REPL re-eval) needed after editing posts or `site.edn`.
- **No formatter** — match surrounding code style (2-space indent, kebab-case, aligned bindings).
- **Dependency-light** — Ring + Hiccup + markdown-clj only. No routing lib (Compojure/Reitit). Keep it light.
- **Content cached in `delay`** — first access loads, subsequent reuses. Can't refresh without restart.

## Conventions

- Kebab-case for all vars/functions.
- `clojure.string` aliased as `str`.
- Small pure functions preferred.
- Tests in `test/portfolio/*_test.clj` — must be required in `test_runner.clj` to run.
- Commit style: short imperative present tense (e.g., "Fix portfolio rendering and accessibility").
