(ns portfolio.site-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [portfolio.site :as site]))

(deftest english-pages-use-localized-nav-landmark
  (is (str/includes? (:body (site/page-for-uri "/en/"))
                     "aria-label=\"Main navigation\""))
  (is (not (str/includes? (:body (site/page-for-uri "/en/"))
                          "aria-label=\"Navigation principale\""))))

(deftest pages-emit-launch-metadata
  (let [html (:body (site/page-for-uri "/"))]
    (is (str/includes? html "rel=\"canonical\""))
    (is (str/includes? html "property=\"og:title\""))
    (is (str/includes? html "name=\"twitter:card\""))
    (is (str/includes? html "href=\"/favicon.svg\""))))

(deftest unknown-routes-render-branded-404-pages
  (let [{:keys [status body]} (site/page-for-uri "/missing")]
    (is (= 404 status))
    (is (str/includes? body "PAGE INTROUVABLE"))
    (is (not= "Not found" body))))

(deftest text-routes-return-correct-content-types
  (is (= "text/plain; charset=utf-8"
         (get-in (site/page-for-uri "/robots.txt") [:headers "content-type"])))
  (is (= "application/xml; charset=utf-8"
         (get-in (site/page-for-uri "/sitemap.xml") [:headers "content-type"]))))

(deftest pages-include-hreflang-tags
  (let [fr-html (:body (site/page-for-uri "/"))
        en-html (:body (site/page-for-uri "/en/"))]
    (is (str/includes? fr-html "hreflang=\"fr\""))
    (is (str/includes? fr-html "hreflang=\"en\""))
    (is (str/includes? fr-html "hreflang=\"x-default\""))
    (is (str/includes? en-html "hreflang=\"fr\""))
    (is (str/includes? en-html "hreflang=\"en\""))
    (is (str/includes? en-html "hreflang=\"x-default\""))))

(deftest blog-index-includes-hreflang-tags
  (let [html (:body (site/page-for-uri "/blog/"))]
    (is (str/includes? html "hreflang=\"fr\""))
    (is (str/includes? html "hreflang=\"en\""))))

(deftest home-page-includes-person-json-ld
  (let [html (:body (site/page-for-uri "/"))]
    (is (str/includes? html "application/ld+json"))
    (is (str/includes? html "\"@type\":\"Person\""))
    (is (str/includes? html "\"name\":\"Fahd El Mazouni\""))))

(deftest article-page-includes-blog-posting-json-ld
  (let [html (:body (site/page-for-uri "/en/blog/designing-api-boundaries-that-age-well/"))]
    (is (str/includes? html "\"@type\":\"BlogPosting\""))
    (is (str/includes? html "\"headline\":"))))

(deftest blog-index-includes-collection-json-ld
  (let [html (:body (site/page-for-uri "/blog/"))]
    (is (str/includes? html "\"@type\":\"CollectionPage\""))))

(deftest rss-feed-returns-valid-xml
  (let [{:keys [status body headers]} (site/page-for-uri "/feed.xml")]
    (is (= 200 status))
    (is (= "application/xml; charset=utf-8" (get headers "content-type")))
    (is (str/includes? body "<rss"))
    (is (str/includes? body "<channel>"))))

(deftest en-rss-feed-contains-english-posts
  (let [body (:body (site/page-for-uri "/en/feed.xml"))]
    (is (str/includes? body "<item>"))
    (is (str/includes? body "Designing API boundaries"))))

(deftest pages-include-rss-auto-discovery-link
  (let [html (:body (site/page-for-uri "/"))]
    (is (str/includes? html "application/rss+xml"))))
