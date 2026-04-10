# Repository Guidelines

## Project Structure & Module Organization
Application code lives in `src/portfolio`. `core.clj` starts the Ring/Jetty server, `site.clj` and `templates.clj` render pages, `content.clj` loads site metadata and Markdown posts, and `export.clj` builds the static site into `dist/`. Tests live in `test/portfolio`, with `test_runner.clj` as the suite entrypoint. Content and static assets are under `resources/`: site config in `resources/content/site.edn`, blog posts in `resources/content/posts/*.md`, and CSS in `resources/public/site.css`.

## Build, Test, and Development Commands
Use the Babashka tasks for day-to-day work:

- `bb dev`: start the local server via `clojure -M:run` on port `3000` by default.
- `bb test`: run the `clojure.test` suite through `portfolio.test-runner`.
- `bb export`: generate the static site in `dist/`.

Direct `clojure` aliases are also available: `clojure -M:run`, `clojure -M:test`, `clojure -M:export`, and `clojure -M:nrepl` for a local nREPL on `127.0.0.1:7888`.

## Coding Style & Naming Conventions
Follow the existing Clojure style in `src/portfolio`: two-space indentation, aligned binding forms, and small pure functions where practical. Use kebab-case for vars and functions (`load-posts`, `related-posts`), and name namespaces after their responsibility (`portfolio.content`, `portfolio.export`). Keep resource paths descriptive and locale-aware, for example `designing-api-boundaries.en.md`. Preserve the current dependency-light approach; there is no formatter configured, so match surrounding layout carefully.

## Testing Guidelines
Tests use `clojure.test`. Add new tests in `test/portfolio/*_test.clj` and require them from `test/portfolio/test_runner.clj` so they run in CI-style entrypoints. Prefer focused unit tests around content parsing, routing, and export behavior. Run `bb test` before opening a PR; if you change post loading or rendering, also run `bb export` and inspect the generated `dist/` output.

## Commit & Pull Request Guidelines
Recent history uses short, imperative commit subjects such as `Fix site links and add nREPL alias`. Keep that pattern, and avoid placeholder messages like `wip` on shared branches. Pull requests should describe the user-visible change, list the commands you ran (`bb test`, `bb export`), and include screenshots when altering rendered pages or CSS. Link related issues when applicable.
