# Repository Guidelines

Canonical guidance for this repo. `CLAUDE.md` points here; keep changes in this file.

## What This Is

Static portfolio site generator. Clojure renders HTML (Hiccup) from EDN config and Markdown posts;
ClojureScript (shadow-cljs) adds browser behaviour as progressive enhancement. Bilingual: French at
the root, English under `/en/`. `bb export` produces static HTML in `dist/`, which CI deploys to
GitHub Pages.

## Build, Test, and Development Commands

`npm ci` first — every task below except `bb test` shells out to `./node_modules/.bin/shadow-cljs`.

- `bb dev [port]` — starts the shadow-cljs watcher **and** the Ring/Jetty server together. Port
  comes from the arg, else `PORT`, else `3000`.
- `bb cljs:watch` / `bb cljs:release` — shadow-cljs alone.
- `bb export` — `shadow-cljs release app`, then `clojure -M:export`.
- `bb test` — the `clojure.test` suite via `portfolio.test-runner`. No single-test runner.
- `bb new-post "Title"` — writes the English skeleton `resources/content/posts/{slug}.en.md` with
  the EDN front-matter filled in (slug derived from the title, accents stripped; date is today),
  then prints the prompt for an AI agent to translate it into French: where to write the file, how
  to slug and adapt the front-matter, and to cross-link both with `:alternate-slug`. Posts are
  written in English and translated; every post ships in both locales. Refuses to overwrite.
  Translations carry `:ai-translated true`, which renders the disclosure notice on the article page.
- `clojure -M:nrepl` — nREPL on `127.0.0.1:7888`.

Direct aliases: `clojure -M:run`, `-M:test`, `-M:export`, `-M:nrepl`.

## Architecture

Clojure, `src/portfolio/`:

- **core.clj** — Ring/Jetty entry point. `wrap-resource "public"` serves CSS/JS/fonts in dev.
- **site.clj** — URI → rendered page. Owns routing for both locales plus `/404.html`,
  `/robots.txt`, `/sitemap.xml`, `/feed.xml`, `/en/feed.xml`.
- **content.clj** — loads `site.edn` and posts off the classpath, parses EDN front-matter, renders
  Markdown, and derives slug, locale, uri, excerpt, reading time, localized date label and heading
  anchors. Pure data, no HTTP. Cached in `delay`s.
- **templates.clj** — Hiccup components, pure, no I/O. Emits the deferred `<script>` tag for
  `/js/main.js`.
- **export.clj** — static build into `dist/`.

Data flow: `core` (or `export`) → `site` → `content` + `templates`.

ClojureScript, `src/portfolio/ui/`. All of it is enhancement — every page must render and work with
JS disabled.

- **main.cljs** — the only entry point. shadow-cljs calls `portfolio.ui.main/init!` as both
  `:init-fn` and `:devtools :after-load`, so each `setup!` must be safe to re-run on hot reload.
- **nav.cljs** — mobile drawer: toggle/close/backdrop/link/Escape, keeps `aria-expanded`,
  `data-open` and `body.nav-open` in sync, hides the logo floater while open, and closes when the
  viewport leaves the 800px breakpoint.
- **scroll.cljs** — click handlers for `[data-scroll-target]` and `[data-scroll-top]`.
- **toc.cljs** — scroll-driven active highlight over `[data-toc-anchor]` items, rAF-throttled.
- **tags.cljs** — client-side tag filtering on the blog index: `.blog-tag-filter` buttons toggle
  `.blog-tag-hidden` on `.post-card[data-tags]`.
- **logo_morph.cljs** — homepage hero logo morphing into the navbar mark on scroll; state lives in
  `defonce` atoms with an explicit teardown, and it tears itself down on the first resize rather
  than animating against stale geometry.
- **motion.cljs** — shared `prefers-reduced-motion` helper (`reduced-motion?`, `scroll-behavior`,
  `on-change!`). Anything animated goes through it; `scroll` and `logo-morph` already do.

`templates.clj` emits the `data-*` attributes and class names these namespaces query — changing one
side means changing the other.

## Build & Deploy Details

- `shadow-cljs.edn`: one `:app` browser build, `:advanced` optimizations, output to
  `resources/public/js`, asset path `/js`.
- `export.clj` cleans `dist/` before writing, guarded so it refuses to recursively delete anything
  outside the repo or the temp dir. It copies `resources/public` but skips shadow-cljs dev output
  (`js/manifest.edn`, `js/cljs-runtime/`), since a release build does not purge a previous watch
  build. It writes `dist/CNAME` when the `CNAME` env var is set.
- CI (`.github/workflows/ci.yml`): `npm ci` → `bb test` → `bb export` (with `CNAME`) → assert
  `dist/404.html`, `robots.txt`, `sitemap.xml`, `favicon.svg` exist → upload and deploy Pages on
  push to `main`.

## Content

- `resources/content/site.edn` — config and every UI string, under `:site {:locales {:fr … :en …}}`.
- `resources/content/posts/{name}.{locale}.md` — front-matter is **EDN, not YAML**. The `:locale`
  and `:slug` keys decide locale and URL; the filename suffix is convention only. Optional
  `:alternate-slug` links a post to its translation.
- A post that fails to parse is skipped with a warning on stderr instead of failing the build — if a
  post silently vanishes, check stderr.
- `resources/public/site.css`; fonts are self-hosted under `resources/public/fonts/` (no CDN).

## Coding Style & Naming Conventions

Two-space indentation, aligned binding forms, kebab-case vars and namespaces, small pure functions.
No formatter is configured, so match surrounding layout. `clojure.string` is aliased as `str`.

Keep the Clojure dependency list minimal — Ring, Hiccup, markdown-clj, slf4j-simple, and no routing
library (routing is plain dispatch in `site.clj`). npm and shadow-cljs are a real build dependency
now, but they are build-time only: the site ships no runtime JS dependencies.

## Testing Guidelines

`clojure.test` in `test/portfolio/*_test.clj`, each required from `test/portfolio/test_runner.clj`
or it will not run. Cover content parsing, routing, and export behaviour; do not assert on prose or
markup that ordinary content edits will churn (such tests were deleted in `a4bbee2`). Run `bb test`
before opening a PR, and if you touch post loading, rendering, or export, run `bb export` and
inspect `dist/`.

## Gotchas

- **No content hot-reload** — `site.edn` and posts sit behind `delay`s; restart the server (or
  re-eval in the REPL) after editing them. The ClojureScript side *does* hot-reload via shadow-cljs.
- **`resources/public/js/` is generated and gitignored** — never edit or commit it. Without a
  running watcher or a prior release build, `/js/main.js` 404s in dev.

## Commit & Pull Request Guidelines

Short, imperative commit subjects (`Fix site links and add nREPL alias`); no `wip` on shared
branches. Pull requests should describe the user-visible change, list the commands you ran
(`bb test`, `bb export`), include screenshots when rendered pages or CSS change, and link related
issues.
