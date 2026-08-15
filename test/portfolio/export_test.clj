(ns portfolio.export-test
  "Static export: which files land in dist/, and which never should.

   The page *contents* are covered by portfolio.site-test against the same
   renderer, so these tests only assert on the file tree. Each export is a
   full render, so the suite shares one run per behaviour rather than one per
   assertion."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [portfolio.content :as content]
            [portfolio.export :as export]
            [portfolio.test-support :as support]))

(defn- temp-dir []
  (.toFile (java.nio.file.Files/createTempDirectory
            "portfolio-export-test"
            (make-array java.nio.file.attribute.FileAttribute 0))))

(defn- write-file! [dir path content]
  (let [target (io/file dir path)]
    (.mkdirs (.getParentFile target))
    (spit target content)
    target))

(defn- relative-paths
  "Every file under `dir`, as `/`-separated paths relative to it."
  [dir]
  (let [root (.toPath dir)]
    (->> (file-seq dir)
         (remove #(.isDirectory %))
         (map #(str/replace (str (.relativize root (.toPath %))) java.io.File/separator "/"))
         set)))

(defn- exported
  "Runs a full export into a fresh temp dir and returns [dir paths]."
  [& {:keys [public-dir]}]
  (let [dir (temp-dir)]
    (with-redefs [export/export-dir (.getAbsolutePath dir)
                  export/public-dir (or public-dir export/public-dir)]
      (export/-main))
    [dir (relative-paths dir)]))

(deftest export-writes-a-file-for-every-public-route
  (let [[_ paths] (exported)]
    (testing "machine-readable routes keep their own filename"
      (doseq [path ["404.html" "robots.txt" "sitemap.xml" "feed.xml" "en/feed.xml"]]
        (is (contains? paths path) path)))
    (testing "directory routes become index.html"
      (doseq [path ["index.html" "blog/index.html" "en/index.html" "en/blog/index.html"]]
        (is (contains? paths path) path)))
    (testing "every post gets its own page at its own uri"
      (doseq [post (content/posts)]
        (is (contains? paths (str (subs (:uri post) 1) "index.html")) (:uri post))))
    (testing "static assets are copied alongside the pages"
      (is (contains? paths "favicon.svg"))
      (is (contains? paths "site.css")))))

(deftest exported-pages-are-the-rendered-pages
  (let [[dir _] (exported)]
    (is (= (support/html-for "/") (slurp (io/file dir "index.html"))))
    (let [post (first (content/posts))]
      (is (= (support/html-for (:uri post))
             (slurp (io/file dir (subs (:uri post) 1) "index.html")))))))

(deftest export-cleans-stale-files
  (let [dir (temp-dir)]
    (with-redefs [export/export-dir (.getAbsolutePath dir)]
      (write-file! dir "blog/deleted-post/index.html" "stale")
      (write-file! dir "orphan.txt" "stale")
      (export/-main)
      (is (not (.exists (io/file dir "blog/deleted-post/index.html")))
          "pages for removed posts must not survive an export")
      (is (not (.exists (io/file dir "orphan.txt"))))
      (is (.exists (io/file dir "index.html"))
          "cleaning must not stop the export from writing pages"))))

(deftest export-refuses-to-clean-outside-the-repo-or-temp-dir
  ;; clean-export-dir! runs a recursive delete; the guard is the only thing
  ;; between a misconfigured export-dir and someone's home directory.
  (let [stderr (java.io.StringWriter.)
        outside (io/file "/portfolio-export-should-never-exist")]
    (binding [*err* stderr]
      (with-redefs [export/export-dir "/"]
        (#'export/clean-export-dir!))
      (with-redefs [export/export-dir (.getAbsolutePath outside)]
        (#'export/clean-export-dir!)))
    (is (= 2 (count (re-seq #"Refusing to clean export dir" (str stderr)))))
    (is (not (.exists outside)))))

(deftest export-is-idempotent
  (let [dir (temp-dir)]
    (with-redefs [export/export-dir (.getAbsolutePath dir)]
      (export/-main)
      (let [first-run (relative-paths dir)]
        (export/-main)
        (is (= first-run (relative-paths dir))
            "a second consecutive export must leave no extra files")))))

(deftest export-skips-shadow-cljs-dev-output
  (let [public (temp-dir)]
    (write-file! public "js/main.js" "released bundle")
    (write-file! public "js/manifest.edn" "{}")
    (write-file! public "js/cljs-runtime/goog.base.js" "dev runtime")
    (write-file! public "js/cljs-runtime/shadow.cljs.devtools.client.js" "websocket client")
    (write-file! public "site.css" "body{}")
    (let [[_ paths] (exported :public-dir (.getAbsolutePath public))]
      (is (contains? paths "js/main.js") "the released bundle must still ship")
      (is (contains? paths "site.css") "ordinary assets must not be dropped by the exclusion")
      (is (not-any? #(str/starts-with? % "js/cljs-runtime/") paths)
          "the shadow-cljs watch runtime must never be exported")
      (is (not (contains? paths "js/manifest.edn"))))))
