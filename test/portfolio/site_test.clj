(ns portfolio.site-test
  "Routing and rendering.

   Assertions target structure (landmarks, attributes, status codes, URL
   shapes) and wiring (does this element carry the value the content layer
   produced?). Expected labels are read back from `content/locale-copy` and
   `site.edn`, so rewording copy never breaks a test here."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [portfolio.content :as content]
            [portfolio.site :as site]
            [portfolio.templates :as templates]
            [portfolio.test-support :as support]))

(defn- class-set [attrs]
  (set (remove str/blank? (str/split (get attrs "class" "") #"\s+"))))

(defn- only-attrs
  "Attributes of the single element carrying `class`, asserting there is one."
  [html class]
  (let [matches (support/attrs-with-class html class)]
    (is (= 1 (count matches)) (str "expected exactly one ." class))
    (first matches)))

(defn- with-posts [posts f]
  (with-redefs [content/posts (constantly (vec posts))]
    (f)))

;; ---------------------------------------------------------------- routing

(deftest routes-answer-with-the-expected-status-and-content-type
  (testing "public pages are 200 text/html"
    (doseq [uri (into ["/" "/index.html" "/blog/" "/en/" "/en/index.html" "/en/blog/"]
                      (support/post-uris))]
      (let [{:keys [status headers]} (support/page uri)]
        (is (= 200 status) uri)
        (is (= "text/html; charset=utf-8" (get headers "content-type")) uri))))
  (testing "non-HTML routes declare their own content type"
    (doseq [[uri content-type] {"/robots.txt" "text/plain; charset=utf-8"
                                "/sitemap.xml" "application/xml; charset=utf-8"
                                "/feed.xml" "application/xml; charset=utf-8"
                                "/en/feed.xml" "application/xml; charset=utf-8"}]
      (let [{:keys [status headers]} (support/page uri)]
        (is (= 200 status) uri)
        (is (= content-type (get headers "content-type")) uri))))
  (testing "unknown routes are 404 but still render a full page"
    (doseq [uri ["/missing" "/en/missing" "/blog/no-such-post/" "/en/blog/no-such-post/" "/404.html"]]
      (let [{:keys [status headers body]} (support/page uri)]
        (is (= 404 status) uri)
        (is (= "text/html; charset=utf-8" (get headers "content-type")) uri)
        (is (= 1 (count (support/elements-with-tag body "main"))) uri)))))

(deftest not-found-pages-are-localized-and-kept-out-of-the-index
  (doseq [[uri locale] {"/missing" :fr "/en/missing" :en "/404.html" :fr}]
    (let [html (support/html-for uri)
          text (support/decode-entities html)]
      (is (= (name locale) (get-in (first (support/elements-with-tag html "html")) [:attrs "lang"])) uri)
      (is (str/includes? text (support/copy locale :not-found-heading)) uri)
      (is (str/includes? text (support/copy locale :not-found-cta)) uri)
      (is (= "noindex,nofollow" (support/meta-content html "robots")) uri)))
  (testing "pages that do exist stay indexable"
    (doseq [uri ["/" "/blog/" "/en/"]]
      (is (= "index,follow" (support/meta-content (support/html-for uri) "robots")) uri))))

;; ---------------------------------------------------------------- landmarks

(deftest every-page-has-exactly-one-main-landmark
  (doseq [uri (support/all-page-uris)]
    (let [html (support/html-for uri)
          mains (support/elements-with-tag html "main")]
      (is (= 1 (count mains)) uri)
      (is (= "main-content" (get-in (first mains) [:attrs "id"])) uri))))

(deftest every-page-declares-its-locale-and-the-standard-landmarks
  (doseq [uri (support/all-page-uris)]
    (let [html (support/html-for uri)
          locale (support/locale-of uri)
          roles (support/attr-values html "role")]
      (is (= (name locale) (get-in (first (support/elements-with-tag html "html")) [:attrs "lang"])) uri)
      (is (contains? roles "banner") uri)
      (is (contains? roles "contentinfo") uri)
      (testing "the skip link targets the main landmark"
        (is (= "#main-content" (get (only-attrs html "skip-link") "href")) uri)))))

(deftest accessible-names-are-wired-to-the-locale-copy
  (doseq [locale [:fr :en]]
    (let [prefix (if (= locale :fr) "" "/en")
          home (support/html-for (str prefix "/"))
          blog (support/html-for (str prefix "/blog/"))
          other (if (= locale :fr) :en :fr)]
      (testing "chrome shared by every page"
        (is (= (support/copy locale :logo-home-label) (get (only-attrs home "logo-mark") "aria-label")))
        (is (= (support/copy locale :nav-label) (get (only-attrs home "top-nav") "aria-label")))
        (is (= (support/copy locale :nav-toggle-label) (get (only-attrs home "nav-toggle") "aria-label")))
        (is (= (support/copy locale :nav-close-label) (get (only-attrs home "nav-close") "aria-label")))
        (is (= (support/copy locale :scroll-label) (get (only-attrs home "home-scroll") "aria-label")))
        (is (= (support/copy locale :back-to-top) (get (only-attrs home "back-to-top") "aria-label"))))
      (testing "the blog index labels its filter group and its cards"
        (let [filters (only-attrs blog "article-tags")]
          (is (= "group" (get filters "role")))
          (is (= (support/copy locale :tag-filter-label) (get filters "aria-label"))))
        (is (= (support/copy locale :blog-list-label) (get (only-attrs blog "blog-list-section") "aria-label")))
        (is (every? #(= (support/copy locale :post-link-label) (get % "aria-label"))
                    (support/attrs-with-class blog "project-arrow"))))
      (testing "the other locale's copy never leaks onto this page"
        (let [labels (support/attr-values home "aria-label")]
          (is (not (contains? labels (support/copy other :nav-label))))
          (is (not (contains? labels (support/copy other :nav-toggle-label)))))))))

(deftest every-page-keeps-the-home-theme-classes
  (doseq [uri (support/all-page-uris)]
    (let [html (support/html-for uri)]
      (is (contains? (class-set (only-attrs html "page-shell")) "theme-home") uri)
      (is (contains? (class-set (only-attrs html "site-header")) "site-header--home") uri)
      (is (contains? (class-set (only-attrs html "site-footer")) "site-footer--home") uri))))

(deftest pages-render-no-dead-links
  (doseq [uri (support/all-page-uris)]
    (let [hrefs (support/attr-values (support/html-for uri) "href")]
      (is (not (contains? hrefs "#")) uri)
      (is (not-any? str/blank? hrefs) uri))))

;; ---------------------------------------------------------------- head metadata

(deftest pages-emit-launch-metadata
  (doseq [uri (support/all-page-uris)]
    (let [html (support/html-for uri)]
      (is (seq (support/links-with-rel html "canonical")) uri)
      (is (some? (support/meta-content html "og:title")) uri)
      (is (some? (support/meta-content html "og:description")) uri)
      (is (= "summary_large_image" (support/meta-content html "twitter:card")) uri)
      (is (= ["/favicon.svg"] (map #(get % "href") (support/links-with-rel html "icon"))) uri))))

(deftest canonical-and-og-urls-are-absolute-and-match-the-route
  (doseq [uri (support/all-page-uris)]
    (let [html (support/html-for uri)
          canonical (get (first (support/links-with-rel html "canonical")) "href")]
      (is (= (support/absolute uri) canonical) uri)
      (is (= canonical (support/meta-content html "og:url")) uri)))
  (testing "articles are tagged as articles, other pages as websites"
    (is (= "article" (support/meta-content (support/html-for (first (support/post-uris))) "og:type")))
    (is (= "website" (support/meta-content (support/html-for "/") "og:type")))))

(deftest og-image-is-absolute
  ;; Social crawlers drop relative image urls, so this must survive the
  ;; portrait path in site.edn staying relative.
  (let [portrait (support/site-value :portrait-url)]
    (is (not (str/starts-with? portrait "http"))
        "site.edn still ships a relative portrait, which is what makes this test meaningful")
    (doseq [uri (support/all-page-uris)]
      (let [image (support/meta-content (support/html-for uri) "og:image")]
        (is (= (support/absolute portrait) image) uri)
        (is (= image (support/meta-content (support/html-for uri) "twitter:image")) uri)))))

(deftest pages-link-their-own-locale-s-feed
  (doseq [uri (support/all-page-uris)]
    (let [html (support/html-for uri)
          feeds (filter #(= "application/rss+xml" (get % "type"))
                        (support/links-with-rel html "alternate"))
          expected (if (= :fr (support/locale-of uri)) "/feed.xml" "/en/feed.xml")]
      (is (= [expected] (map #(get % "href") feeds)) uri)
      (is (= 200 (:status (support/page expected))) uri))))

(deftest font-preloads-point-at-existing-woff2-files
  (let [hrefs (map #(get % "href") (support/links-with-rel (support/html-for "/") "preload"))]
    (is (seq hrefs))
    (is (every? #(str/ends-with? % ".woff2") hrefs))
    (is (not (str/includes? (support/html-for "/") ".ttf")))
    (doseq [href hrefs]
      (is (some? (io/resource (str "public" href)))
          (str "preloaded font missing from resources: " href)))))

;; ---------------------------------------------------------------- hreflang

(deftest hreflang-links-pair-the-two-locales
  (doseq [[fr-uri en-uri] [["/" "/en/"] ["/blog/" "/en/blog/"]]]
    (doseq [uri [fr-uri en-uri]]
      (let [alternates (->> (support/links-with-rel (support/html-for uri) "alternate")
                            (filter #(get % "hreflang"))
                            (map (juxt #(get % "hreflang") #(get % "href")))
                            (into {}))]
        (is (= {"fr" (support/absolute fr-uri)
                "en" (support/absolute en-uri)
                "x-default" (support/absolute fr-uri)}
               alternates)
            uri)))))

(deftest articles-carry-hreflang-only-when-a-translation-exists
  (testing "shipped articles without an :alternate-slug advertise no alternates"
    (doseq [post (content/posts)
            :when (nil? (content/alternate-post (:locale post) (:slug post)))]
      (let [alternates (->> (support/links-with-rel (support/html-for (:uri post)) "alternate")
                            (filter #(get % "hreflang")))]
        (is (empty? alternates) (:uri post))
        (is (seq (support/links-with-rel (support/html-for (:uri post)) "canonical"))
            "dropping hreflang must not drop the canonical link too"))))
  (testing "a translated pair links to each other"
    (with-posts [(support/stub-post :slug "fr-post" :locale :fr :uri "/blog/fr-post/"
                                    :alternate-slug "en-post")
                 (support/stub-post :slug "en-post" :locale :en :uri "/en/blog/en-post/"
                                    :alternate-slug "fr-post")]
      (fn []
        (doseq [uri ["/blog/fr-post/" "/en/blog/en-post/"]]
          (let [alternates (->> (support/links-with-rel (support/html-for uri) "alternate")
                                (filter #(get % "hreflang"))
                                (map (juxt #(get % "hreflang") #(get % "href")))
                                (into {}))]
            (is (= {"fr" (support/absolute "/blog/fr-post/")
                    "en" (support/absolute "/en/blog/en-post/")
                    "x-default" (support/absolute "/blog/fr-post/")}
                   alternates)
                uri)))))))

;; ---------------------------------------------------------------- structured data

(deftest json-ld-escapes-angle-brackets
  (let [escape @#'templates/escape-json-str]
    (is (= "\\u003c/script\\u003e" (escape "</script>")))
    (is (not (str/includes? (escape "a < b > c") "<")))
    (is (not (str/includes? (escape "a < b > c") ">")))))

(deftest home-pages-describe-the-person
  (doseq [locale [:fr :en]]
    (let [json (first (support/json-ld (support/html-for (if (= :fr locale) "/" "/en/"))))]
      (is (some? json))
      (is (str/includes? json "\"@type\":\"Person\""))
      (is (= (support/site-value :name) (support/json-field json "name")))
      (is (= (support/site-value :site-url) (support/json-field json "url")))
      (is (= (support/copy locale :job-title) (support/json-field json "jobTitle"))))))

(deftest article-pages-describe-the-article
  (doseq [post (content/posts)]
    (let [json (first (support/json-ld (support/html-for (:uri post))))]
      (is (str/includes? json "\"@type\":\"BlogPosting\"") (:uri post))
      (is (= (:title post) (support/json-field json "headline")) (:uri post))
      (is (= (:date post) (support/json-field json "datePublished")) (:uri post))
      (is (= (support/absolute (:uri post)) (support/json-field json "url")) (:uri post)))))

(deftest blog-indexes-describe-the-whole-collection
  (doseq [locale [:fr :en]]
    (let [uri (if (= :fr locale) "/blog/" "/en/blog/")
          json (first (support/json-ld (support/html-for uri)))
          posts (content/posts-for-locale locale)]
      (is (str/includes? json "\"@type\":\"CollectionPage\"") uri)
      (is (= (support/absolute uri) (support/json-field json "url")) uri)
      (is (= (count posts) (count (re-seq #"\"@type\":\"BlogPosting\"" json)))
          (str uri " must list every post of its locale"))
      (doseq [post posts]
        (is (str/includes? json (support/absolute (:uri post))) (:uri post))))))

;; ---------------------------------------------------------------- feeds and sitemap

(deftest rss-feeds-carry-exactly-their-own-locale-s-posts
  (doseq [locale [:fr :en]]
    (let [uri (if (= :fr locale) "/feed.xml" "/en/feed.xml")
          body (:body (support/page uri))
          links (set (map second (re-seq #"<link>([^<]+)</link>" body)))]
      (is (str/starts-with? body "<?xml version=\"1.0\" encoding=\"UTF-8\"?>") uri)
      (is (str/includes? body (str "<language>" (name locale) "</language>")) uri)
      (is (= (count (content/posts-for-locale locale))
             (count (re-seq #"<item>" body)))
          uri)
      (doseq [post (content/posts)]
        (if (= locale (:locale post))
          (is (contains? links (support/absolute (:uri post))) (:uri post))
          (is (not (contains? links (support/absolute (:uri post))))
              (str (:uri post) " must not appear in the " locale " feed")))))))

(deftest sitemap-lists-every-public-route-with-a-lastmod
  (let [xml (:body (support/page "/sitemap.xml"))
        entries (into {} (map (fn [[_ loc lastmod]] [loc lastmod]))
                      (re-seq #"<url><loc>([^<]+)</loc><lastmod>([^<]+)</lastmod></url>" xml))]
    (doseq [uri (into ["/" "/blog/" "/en/" "/en/blog/"] (support/post-uris))]
      (is (contains? entries (support/absolute uri)) uri))
    (is (every? #(re-matches #"\d{4}-\d{2}-\d{2}" %) (vals entries)))
    (testing "posts date their own entry"
      (doseq [post (content/posts)]
        (is (= (:date post) (get entries (support/absolute (:uri post)))) (:uri post))))
    (testing "error and machine routes stay out of the sitemap"
      (doseq [excluded ["/404.html" "/robots.txt" "/sitemap.xml" "/feed.xml"]]
        (is (not (str/includes? xml excluded)) excluded)))))

(deftest robots-txt-points-at-the-sitemap
  (let [body (:body (support/page "/robots.txt"))]
    (is (str/includes? body "User-agent: *"))
    (is (str/includes? body (str "Sitemap: " (support/absolute "/sitemap.xml"))))))

;; ---------------------------------------------------------------- page bodies

(deftest article-tables-of-contents-link-to-real-anchors
  (doseq [post (content/posts)]
    (let [html (support/html-for (:uri post))
          items (support/attrs-with-class html "article-toc-item")
          ids (support/attr-values html "id")]
      (is (= (mapv :anchor (:headings post)) (mapv #(get % "data-toc-anchor") items))
          (str (:uri post) " toc must mirror the post's headings, in order"))
      (doseq [item items]
        (is (= (str "#" (get item "data-toc-anchor")) (get item "href")) (:uri post))
        (is (contains? ids (get item "data-toc-anchor"))
            (str (:uri post) " links to a missing anchor " (get item "data-toc-anchor")))))))

(deftest related-posts-card-renders-only-when-there-are-related-posts
  (testing "a post with tag-sharing siblings gets a card listing each of them"
    (with-posts [(support/stub-post :slug "a" :uri "/en/blog/a/" :tags ["x"] :date "2026-03-01")
                 (support/stub-post :slug "b" :uri "/en/blog/b/" :tags ["x"] :date "2026-02-01")
                 (support/stub-post :slug "c" :uri "/en/blog/c/" :tags ["x"] :date "2026-01-01")]
      (fn []
        (let [html (support/html-for "/en/blog/a/")]
          (is (= 1 (count (support/attrs-with-class html "article-related-card"))))
          (is (contains? (support/attr-values html "href") "/en/blog/b/"))
          (is (contains? (support/attr-values html "href") "/en/blog/c/"))))))
  (testing "a post with no tag overlap gets no card at all"
    (with-posts [(support/stub-post :slug "a" :uri "/en/blog/a/" :tags ["x"])
                 (support/stub-post :slug "b" :uri "/en/blog/b/" :tags ["y"])]
      (fn []
        (let [html (support/html-for "/en/blog/a/")]
          (is (empty? (support/attrs-with-class html "article-related-card")))
          (is (not (contains? (support/attr-values html "href") "/en/blog/b/"))))))))

(deftest blog-index-exposes-the-data-the-tag-filter-script-reads
  ;; templates.clj emits these attributes and portfolio.ui.tags queries them;
  ;; the two sides only stay in sync if something checks the contract.
  (doseq [locale [:fr :en]]
    (let [uri (if (= :fr locale) "/blog/" "/en/blog/")
          html (support/html-for uri)
          buttons (support/attrs-with-class html "blog-tag-filter")
          cards (support/attrs-with-class html "post-card")
          posts (content/posts-for-locale locale)]
      (is (= (vec (content/popular-tags locale)) (mapv #(get % "data-tag") buttons)) uri)
      (is (every? #(= "button" (get % "type")) buttons) uri)
      (is (= (count posts) (count cards)) uri)
      (testing "every card advertises its own tags, so no filter hides a matching post"
        (is (= (mapv #(str/join "," (content/post-tags %)) posts)
               (mapv #(get % "data-tags") cards))
            uri))
      (testing "every filter button matches at least one card"
        (doseq [button buttons]
          (is (some #(contains? (set (str/split (get % "data-tags") #",")) (get button "data-tag"))
                    cards)
              (str uri " filter " (get button "data-tag") " matches no card")))))))

(deftest blog-index-cards-link-to-their-posts
  (doseq [locale [:fr :en]]
    (let [uri (if (= :fr locale) "/blog/" "/en/blog/")
          hrefs (support/attr-values (support/html-for uri) "href")]
      (doseq [post (content/posts-for-locale locale)]
        (is (contains? hrefs (:uri post)) (:uri post))))))

(deftest portrait-declares-intrinsic-dimensions
  ;; Without width/height the hero portrait shifts layout as it loads.
  (let [html (support/html-for "/")
        img (first (filter #(= (support/site-value :portrait-url) (get % "src"))
                           (map :attrs (support/elements-with-tag html "img"))))]
    (is (seq (support/attrs-with-class html "home-portrait")))
    (is (some? img) "the hero portrait must be rendered from :portrait-url")
    (is (re-matches #"\d+" (get img "width")))
    (is (re-matches #"\d+" (get img "height")))
    (is (not (str/blank? (get img "alt"))))))

;; ---------------------------------------------------------------- configuration

(deftest rendering-reads-site-config-at-render-time
  ;; A regression here would mean config captured at namespace load, which is
  ;; invisible in production but breaks the REPL workflow documented in AGENTS.md.
  (let [sentinel "SENTINEL-ABOUT-HEADING"
        patched (assoc-in (content/site-config) [:site :locales :fr :about-heading] sentinel)]
    (with-redefs [content/site-config (constantly patched)]
      (is (str/includes? (support/html-for "/") sentinel)))))

(deftest base-path-prefixes-every-internal-link
  ;; Project-page deploys serve the site from a subdirectory.
  (let [patched (assoc-in (content/site-config) [:site :base-path] "/portfolio")]
    (with-redefs [content/site-config (constantly patched)]
      (let [html (site/render-blog-index :fr)
            hrefs (support/attr-values html "href")
            internal (remove #(str/starts-with? % "http") hrefs)]
        (is (seq internal))
        (doseq [href internal
                :when (not (str/starts-with? href "#"))
                :when (not (str/starts-with? href "mailto:"))]
          (is (str/starts-with? href "/portfolio/") href))
        (is (contains? (support/attr-values html "src") "/portfolio/js/main.js"))))))

(deftest base-path-prefixes-every-published-absolute-url
  ;; Internal links were prefixed but the absolute URLs we publish were not, so
  ;; a project-page deploy advertised canonical/og:url/sitemap/feed targets that
  ;; 404. Anything carrying the origin has to carry the base path with it.
  (let [patched (assoc-in (content/site-config) [:site :base-path] "/portfolio")
        origin (:site-url (:site patched))
        root (str origin "/portfolio")]
    (with-redefs [content/site-config (constantly patched)]
      (doseq [uri ["/" "/en/" "/blog/" "/en/blog/"]]
        (let [html (:body (site/page-for-uri uri))]
          (doseq [attr ["href" "content"]
                  value (support/attr-values html attr)
                  :when (str/starts-with? value origin)]
            (is (str/starts-with? value root) (str uri " -> " value)))))
      (doseq [route ["/sitemap.xml" "/feed.xml" "/en/feed.xml" "/robots.txt"]]
        (let [body (:body (site/page-for-uri route))]
          (is (str/includes? body root) route)
          (is (not (re-find (re-pattern (str (java.util.regex.Pattern/quote origin) "/(?!portfolio)"))
                            body))
              (str route " publishes an unprefixed absolute URL")))))))
