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

(deftest home-project-cards-do-not-render-fake-links
  (let [html (:body (site/page-for-uri "/"))]
    (is (not (str/includes? html "data-card-link")))
    (is (not (str/includes? html "Voir sur GitHub")))
    (is (not (str/includes? html "View on GitHub")))))
