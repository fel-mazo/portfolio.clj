(ns portfolio.ui.highlight
  (:require ["highlight.js/lib/core" :as hljs]
            ["highlight.js/lib/languages/c" :as c]
            ["highlight.js/lib/languages/elixir" :as elixir]))

;; language grammar files export the function directly — no :default export.
;; hljs lib/core.js does set highlight.default, so hljs/default is fine.
(doseq [[name grammar] {"c" c "elixir" elixir}]
  (.registerLanguage hljs/default name grammar))

(defn setup! []
  (doseq [block (array-seq (.querySelectorAll js/document "pre code"))]
    ;; markdown-clj emits the fence info string as a bare class ("elixir"),
    ;; which highlight.js resolves directly; unknown languages fall back to
    ;; plain text. Already-highlighted blocks are skipped by hljs itself.
    (.highlightElement hljs/default block)))
