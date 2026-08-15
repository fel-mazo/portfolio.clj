(ns portfolio.content-test
  "Content layer: loading, parsing and deriving post data.

   `portfolio.content` hands back plain maps, so these tests assert on those
   maps. Where a behaviour needs a specific corpus (ranking, capping,
   translations) the corpus is built in the test rather than borrowed from
   `resources/content`, so shipped copy can change freely."
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [portfolio.content :as content]
            [portfolio.test-support :as support])
  (:import (java.io File)
           (java.util.jar JarEntry JarOutputStream)))

(defn- temp-dir []
  (.toFile (java.nio.file.Files/createTempDirectory "portfolio-content-test"
                                                    (make-array java.nio.file.attribute.FileAttribute 0))))

(defn- write-post! [dir filename body]
  (let [target (io/file dir filename)]
    (spit target body)
    (.toURL (.toURI target))))

(defn- create-jar! [entries]
  (let [jar-file (File/createTempFile "portfolio-content" ".jar")]
    (with-open [output (JarOutputStream. (io/output-stream jar-file))]
      (doseq [[path content] entries]
        (.putNextEntry output (JarEntry. path))
        (when content
          (.write output (.getBytes content "UTF-8")))
        (.closeEntry output)))
    jar-file))

(defn- parse-body
  "Loads a single post from a temp file and returns the normalized map."
  [filename body]
  (let [dir (temp-dir)
        url (write-post! dir filename body)]
    (with-redefs [content/list-resource-urls (constantly [url])]
      (first (content/load-posts)))))

(defn- with-posts
  "Runs `f` with `content/posts` replaced by the given post maps."
  [posts f]
  (with-redefs [content/posts (constantly (vec posts))]
    (f)))

;; ---------------------------------------------------------------- loading

(deftest shipped-posts-are-well-formed
  (testing "every loaded post carries the keys the rest of the site relies on"
    (is (seq (content/posts)))
    (doseq [post (content/posts)]
      (let [where (str "post " (:slug post))]
        (is (contains? #{:fr :en} (:locale post)) where)
        (is (not (str/blank? (:slug post))) where)
        (is (not (str/blank? (:title post))) where)
        (is (not (str/blank? (:excerpt post))) where)
        (is (not (str/blank? (:html post))) where)
        (is (pos? (:reading-time post)) where)
        (is (= (if (= :fr (:locale post))
                 (str "/blog/" (:slug post) "/")
                 (str "/en/blog/" (:slug post) "/"))
               (:uri post))
            where)))))

(deftest posts-for-locale-partitions-the-corpus
  (testing "each locale sees only its own posts, and together they are the whole corpus"
    (let [fr (content/posts-for-locale :fr)
          en (content/posts-for-locale :en)]
      (is (seq fr))
      (is (seq en))
      (is (every? #(= :fr (:locale %)) fr))
      (is (every? #(= :en (:locale %)) en))
      (is (= (count (content/posts)) (+ (count fr) (count en)))))))

(deftest find-post-matches-on-locale-and-slug
  (testing "a slug only resolves within its own locale"
    (let [post (first (content/posts-for-locale :en))]
      (is (= post (content/find-post :en (:slug post))))
      (is (nil? (content/find-post :fr (:slug post))))
      (is (nil? (content/find-post :en "no-such-slug"))))))

(deftest load-posts-skips-malformed-front-matter
  (testing "a malformed post does not prevent valid posts from loading"
    (let [dir (temp-dir)
          good-url (write-post! dir
                                "valid-post.en.md"
                                (str "---\n"
                                     "{:title \"Valid post\" :locale \"en\" :date \"2026-04-01\"}\n"
                                     "---\n\n"
                                     "## Intro\n\n"
                                     "Healthy body."))
          bad-url (write-post! dir
                               "broken-post.en.md"
                               (str "---\n"
                                    "{:title \"Broken\"\n"
                                    "---\n\n"
                                    "This never parses."))
          stderr (java.io.StringWriter.)]
      (binding [*err* stderr]
        (with-redefs [content/list-resource-urls (constantly [good-url bad-url])]
          (let [posts (content/load-posts)]
            (is (= 1 (count posts)))
            (is (= "Valid post" (:title (first posts))))
            (is (= "/en/blog/valid-post.en/" (:uri (first posts))))
            (is (.contains (str stderr) "Warning: skipping post"))))))))

(deftest list-resource-urls-supports-jar-resources
  (testing "posts can be discovered from a packaged jar resource directory"
    (let [jar-file (create-jar! {"content/posts/" nil
                                 "content/posts/alpha.en.md" "alpha"
                                 "content/posts/beta.fr.md" "beta"
                                 "content/posts/nested/" nil
                                 "content/posts/nested/ignored.md" "ignored"})
          listing (with-redefs [content/classpath-entries (constantly [(.getAbsolutePath jar-file)])]
                    (#'content/list-resource-urls "content/posts"))]
      (is (= 2 (count listing)))
      (is (= ["alpha.en.md" "beta.fr.md"]
             (mapv #(last (str/split (.toExternalForm ^java.net.URL %) #"/"))
                   listing))))))

;; ---------------------------------------------------------------- headings

(deftest heading-anchors-survive-inline-markdown
  (testing "a heading with inline markdown still gets an id matching its toc entry"
    (let [post (parse-body "inline.en.md"
                           (str "---\n"
                                "{:title \"Inline\" :locale \"en\" :date \"2026-04-01\"}\n"
                                "---\n\n"
                                "## The **why** of it\n\n"
                                "Body.\n\n"
                                "## Use `fetch`\n\n"
                                "More body.\n"))
          headings (:headings post)]
      (is (= ["The why of it" "Use fetch"] (mapv :title headings)))
      (is (= ["the-why-of-it" "use-fetch"] (mapv :anchor headings)))
      (doseq [{:keys [anchor]} headings]
        (is (str/includes? (:html post) (str "id=\"" anchor "\""))))
      (is (str/includes? (:html post) "<strong>why</strong>")
          "inline markup must survive anchoring, not be flattened into the id"))))

(deftest heading-anchors-are-unique
  (testing "duplicate heading titles get distinct ids"
    (let [post (parse-body "dupes.en.md"
                           (str "---\n"
                                "{:title \"Dupes\" :locale \"en\" :date \"2026-04-01\"}\n"
                                "---\n\n"
                                "## Intro\n\nA.\n\n"
                                "## Intro\n\nB.\n\n"
                                "### Intro\n\nC.\n"))
          anchors (mapv :anchor (:headings post))]
      (is (= ["intro" "intro-2" "intro-3"] anchors))
      (doseq [anchor anchors]
        (is (= 1 (count (re-seq (re-pattern (str "id=\"" anchor "\"")) (:html post)))))))))

(deftest headings-inside-fenced-code-blocks-are-ignored
  (testing "a ## line inside a fence is not a table-of-contents entry"
    (let [post (parse-body "fenced.en.md"
                           (str "---\n"
                                "{:title \"Fenced\" :locale \"en\" :date \"2026-04-01\"}\n"
                                "---\n\n"
                                "## Real heading\n\n"
                                "```\n"
                                "## not a heading\n"
                                "```\n\n"
                                "Tail.\n"))]
      (is (= ["Real heading"] (mapv :title (:headings post))))
      (is (not (str/includes? (:html post) "id=\"not-a-heading\""))))))

(deftest heading-levels-are-recorded
  (testing "h2 and h3 are both collected, at their own level"
    (let [post (parse-body "levels.en.md"
                           (str "---\n"
                                "{:title \"Levels\" :locale \"en\" :date \"2026-04-01\"}\n"
                                "---\n\n"
                                "# Title\n\nA.\n\n"
                                "## Section\n\nB.\n\n"
                                "### Subsection\n\nC.\n"))]
      (is (= [{:level 2 :title "Section"} {:level 3 :title "Subsection"}]
             (mapv #(select-keys % [:level :title]) (:headings post)))
          "h1 belongs to the page title, not the table of contents"))))

(deftest every-heading-anchor-exists-in-rendered-html
  (testing "shipped posts keep :headings and :html ids consistent"
    (doseq [post (content/posts)
            {:keys [anchor]} (:headings post)]
      (is (str/includes? (:html post) (str "id=\"" anchor "\""))
          (str (:slug post) " is missing anchor " anchor)))))

;; ---------------------------------------------------------------- derived fields

(deftest excerpt-falls-back-to-the-first-body-line
  (testing "an absent :excerpt is derived from the first prose line, stripped of markup"
    (let [post (parse-body "excerpt.en.md"
                           (str "---\n"
                                "{:title \"T\" :locale \"en\" :date \"2026-04-01\"}\n"
                                "---\n\n"
                                "A **bold** _thin_ [link](https://example.com) and `code`.\n"))]
      (is (= "A bold thin link and code." (:excerpt post)))))
  (testing "an explicit :excerpt wins"
    (let [post (parse-body "explicit.en.md"
                           (str "---\n"
                                "{:title \"T\" :locale \"en\" :date \"2026-04-01\" :excerpt \"Chosen\"}\n"
                                "---\n\n"
                                "Ignored body line.\n"))]
      (is (= "Chosen" (:excerpt post))))))

(deftest date-labels-are-derived-per-locale
  (testing "english posts use the long US format"
    (let [post (parse-body "en-date.en.md"
                           "---\n{:title \"T\" :locale \"en\" :date \"2026-03-30\"}\n---\n\nBody.\n")]
      (is (= "March 30, 2026" (:date-label post)))))
  (testing "french posts use the day-month-year format without a comma"
    (let [post (parse-body "fr-date.fr.md"
                           "---\n{:title \"T\" :locale \"fr\" :date \"2026-04-08\"}\n---\n\nBody.\n")]
      (is (= "8 avril 2026" (:date-label post)))))
  (testing "an explicit front-matter label wins"
    (let [post (parse-body "override.en.md"
                           "---\n{:title \"T\" :locale \"en\" :date \"2026-03-30\" :date-label \"Whenever\"}\n---\n\nBody.\n")]
      (is (= "Whenever" (:date-label post)))))
  (testing "a missing or malformed date yields nil instead of dropping the post"
    (let [missing (parse-body "no-date.en.md"
                              "---\n{:title \"T\" :locale \"en\"}\n---\n\nBody.\n")
          broken (parse-body "bad-date.en.md"
                             "---\n{:title \"T\" :locale \"en\" :date \"nonsense\"}\n---\n\nBody.\n")]
      (is (= "T" (:title missing)))
      (is (nil? (:date-label missing)))
      (is (= "T" (:title broken)))
      (is (nil? (:date-label broken))))))

(deftest shipped-posts-carry-a-label-shaped-for-their-locale
  ;; Shape, not text: a new post or a re-dated one must not break this, but
  ;; wiring the wrong locale's formatter (or dropping the label) still does.
  (testing "english labels read `Month D, YYYY`"
    (doseq [post (content/posts-for-locale :en)]
      (is (re-matches #"[A-Z][a-z]+ \d{1,2}, \d{4}" (str (:date-label post)))
          (str (:slug post) " has label " (pr-str (:date-label post))))))
  (testing "french labels read `D month YYYY`"
    (doseq [post (content/posts-for-locale :fr)]
      (is (re-matches #"\d{1,2} \p{L}+ \d{4}" (str (:date-label post)))
          (str (:slug post) " has label " (pr-str (:date-label post)))))))

;; ---------------------------------------------------------------- relations

(deftest related-posts-exclude-zero-tag-overlap
  (testing "only posts sharing at least one tag are related"
    (doseq [locale [:fr :en]
            post (content/posts-for-locale locale)
            related (content/related-posts locale (:slug post))]
      (is (seq (set/intersection (set (:tags post)) (set (:tags related))))
          (str (:slug related) " shares no tag with " (:slug post)))
      (is (not= (:slug post) (:slug related))
          "a post must never be related to itself"))))

(deftest related-posts-rank-by-overlap-then-date-and-cap-at-four
  ;; Built here rather than taken from resources/content: the shipped corpus is
  ;; too small to reach the cap, so a test over it would pass with no cap at all.
  (let [current (support/stub-post :slug "current" :tags ["a" "b"] :date "2026-01-01")
        candidates (for [[slug tags date] [["two-a" ["a" "b"] "2026-02-01"]
                                           ["two-b" ["b" "a"] "2026-03-01"]
                                           ["one-a" ["a"] "2026-04-01"]
                                           ["one-b" ["b"] "2026-05-01"]
                                           ["one-c" ["a"] "2026-06-01"]
                                           ["none" ["z"] "2026-07-01"]]]
                     (support/stub-post :slug slug :tags tags :date date
                                        :uri (str "/en/blog/" slug "/")))
        other-locale (support/stub-post :slug "fr-twin" :locale :fr :tags ["a" "b"]
                                        :uri "/blog/fr-twin/" :date "2026-08-01")]
    (with-posts (concat [current other-locale] candidates)
      (fn []
        (let [related (content/related-posts :en "current")]
          (testing "ranked by shared-tag count, then by most recent date"
            (is (= ["two-b" "two-a" "one-c" "one-b"] (mapv :slug related))))
          (testing "capped at four even though five candidates overlap"
            (is (= 4 (count related))))
          (testing "posts from the other locale never leak in"
            (is (not-any? #(= :fr (:locale %)) related))))))))

(deftest popular-tags-rank-by-frequency-then-name-and-cap-at-eight
  (let [posts (concat (for [n (range 3)]
                        (support/stub-post :slug (str "common-" n) :tags ["zebra" "alpha"]))
                      (for [n (range 10)]
                        (support/stub-post :slug (str "rare-" n) :tags [(str "tag-" n)]))
                      [(support/stub-post :slug "fr" :locale :fr :tags ["french-only"])])]
    (with-posts posts
      (fn []
        (let [tags (content/popular-tags :en)]
          (testing "most frequent first, ties broken alphabetically"
            (is (= ["alpha" "zebra"] (take 2 tags))))
          (testing "capped at eight"
            (is (= 8 (count tags))))
          (testing "scoped to the requested locale"
            (is (not (contains? (set tags) "french-only")))))))))

(deftest popular-tags-are-tags-that-shipped-posts-actually-carry
  (doseq [locale [:fr :en]]
    (let [known (set (mapcat content/post-tags (content/posts-for-locale locale)))]
      (is (seq (content/popular-tags locale)) (str locale " has no tags at all"))
      (is (every? known (content/popular-tags locale))
          (str locale " advertises a tag no post carries")))))

(deftest alternate-post-follows-the-alternate-slug
  (let [fr (support/stub-post :slug "fr-post" :locale :fr :uri "/blog/fr-post/"
                              :alternate-slug "en-post")
        en (support/stub-post :slug "en-post" :locale :en :uri "/en/blog/en-post/"
                              :alternate-slug "fr-post")
        lonely (support/stub-post :slug "lonely" :locale :en :uri "/en/blog/lonely/")
        dangling (support/stub-post :slug "dangling" :locale :en :uri "/en/blog/dangling/"
                                    :alternate-slug "gone")]
    (with-posts [fr en lonely dangling]
      (fn []
        (testing "a linked pair resolves in both directions"
          (is (= "en-post" (:slug (content/alternate-post :fr "fr-post"))))
          (is (= "fr-post" (:slug (content/alternate-post :en "en-post")))))
        (testing "no :alternate-slug means no translation"
          (is (nil? (content/alternate-post :en "lonely"))))
        (testing "an :alternate-slug pointing nowhere resolves to nil, not a crash"
          (is (nil? (content/alternate-post :en "dangling"))))))))

;; ---------------------------------------------------------------- config

(deftest locale-copy-covers-both-locales-with-the-same-keys
  (let [fr (content/locale-copy :fr)
        en (content/locale-copy :en)]
    (is (seq fr))
    (is (= (set (keys fr)) (set (keys en)))
        "a key present in one locale but not the other renders as a blank label")
    (doseq [[key value] fr]
      (is (not (str/blank? (str value))) (str ":fr " key " is blank")))
    (doseq [[key value] en]
      (is (not (str/blank? (str value))) (str ":en " key " is blank")))))

(deftest site-config-exposes-the-keys-rendering-depends-on
  (doseq [key [:name :site-url :email :portrait-url :socials]]
    (is (some? (support/site-value key)) (str "site.edn is missing " key)))
  (is (str/starts-with? (support/site-value :site-url) "https://")
      "canonical and og urls are built by concatenating :site-url"))
