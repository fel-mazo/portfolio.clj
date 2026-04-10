# Portfolio Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refine the existing portfolio without changing its visual direction by replacing placeholder content, improving semantics/accessibility, and simplifying content loading for maintainability.

**Architecture:** Keep the current Ring plus Hiccup structure, but move content access behind small functions instead of startup-time globals. Preserve the current templates and CSS direction while making the data more real, the markup more semantic, and the tests broader around rendering and export-sensitive behavior.

**Tech Stack:** Clojure 1.12, Ring, Hiccup 2, markdown-clj, clojure.test, Babashka tasks

---

### Task 1: Lock in the expected rendering changes with tests

**Files:**
- Modify: `test/portfolio/content_test.clj`

- [ ] **Step 1: Write failing tests for rendered content and semantics**

Add assertions that the generated HTML no longer contains placeholder project names, placeholder blog tags, duplicate `<main>` elements, or fake TOC fallback section titles.

- [ ] **Step 2: Run the targeted test command to verify failure**

Run: `rtk clojure -M:test`
Expected: FAIL on the new placeholder/semantics assertions before implementation.

- [ ] **Step 3: Write minimal production changes to satisfy those tests**

Update content/config and templates just enough to remove placeholder output and fix the invalid page structure.

- [ ] **Step 4: Re-run the full test suite**

Run: `rtk bb test`
Expected: PASS with 0 failures and 0 errors.

### Task 2: Replace scaffold content with real, config-driven portfolio data

**Files:**
- Modify: `resources/content/site.edn`
- Modify: `src/portfolio/templates.clj`
- Modify: `src/portfolio/site.clj`

- [ ] **Step 1: Replace placeholder site and project content**

Add real project titles, summaries, stacks, and localized copy in `resources/content/site.edn`.

- [ ] **Step 2: Make templates render actual configured metadata**

Use real project data and post tags instead of hard-coded strings, and remove fake article placeholder blocks.

- [ ] **Step 3: Verify rendered output manually through export**

Run: `rtk bb export`
Expected: `dist/` contains pages with real project and post metadata, without lorem ipsum or `TECHNO` placeholders.

### Task 3: Refactor content access for maintainability

**Files:**
- Modify: `src/portfolio/content.clj`
- Modify: `src/portfolio/site.clj`
- Modify: `test/portfolio/content_test.clj`

- [ ] **Step 1: Write a failing test for reloadable content access**

Add a test that exercises the content loading functions via `with-redefs` so rendering reads from function-based content sources instead of fixed startup-time values.

- [ ] **Step 2: Run the test to verify the current implementation is too static**

Run: `rtk clojure -M:test`
Expected: FAIL because current rendering paths rely on top-level loaded vars.

- [ ] **Step 3: Refactor to function-based content access**

Introduce small helpers such as `site-config`, `posts`, and localized selectors that return fresh values and keep the rest of the app data-oriented.

- [ ] **Step 4: Re-run the test suite**

Run: `rtk bb test`
Expected: PASS with the new dynamic behavior covered.

### Task 4: Tighten semantics and accessibility without changing the look

**Files:**
- Modify: `src/portfolio/templates.clj`
- Modify: `src/portfolio/site.clj`
- Modify: `resources/public/site.css`

- [ ] **Step 1: Remove invalid nested landmarks and wire the TOC to real headings**

Ensure each page renders a single main landmark and article headings can be navigated from the TOC using generated anchors.

- [ ] **Step 2: Keep the layout visually equivalent**

Only add the small CSS needed for anchor targets or visually hidden helper text if required; avoid redesigning the page.

- [ ] **Step 3: Verify via tests and export**

Run: `rtk bb test`
Run: `rtk bb export`
Expected: PASS, and exported HTML shows one `main` per page with linked article sections.

### Task 5: Final verification

**Files:**
- Modify: `src/portfolio/content.clj`
- Modify: `src/portfolio/site.clj`
- Modify: `src/portfolio/templates.clj`
- Modify: `resources/content/site.edn`
- Modify: `resources/public/site.css`
- Modify: `test/portfolio/content_test.clj`

- [ ] **Step 1: Run the complete verification commands**

Run: `rtk bb test`
Run: `rtk bb export`
Expected: both commands exit 0.

- [ ] **Step 2: Inspect generated output for regressions**

Run: `rtk sed -n '1,220p' dist/index.html`
Run: `rtk sed -n '1,260p' dist/blog/index.html`
Expected: no placeholder portfolio content, no fake tags, and no nested main structure.
