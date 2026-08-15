(ns portfolio.export
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [portfolio.content :as content]
            [portfolio.site :as site])
  (:gen-class))

(def export-dir "dist")

(defn- file-uri? [uri]
  (re-find #"\.[a-z]+$" uri))

(defn- target-file [uri]
  (let [trimmed (subs uri 1)]
    (cond
      (empty? trimmed) (io/file export-dir "index.html")
      (file-uri? uri) (io/file export-dir trimmed)
      :else (io/file export-dir trimmed "index.html"))))

(defn- write-page! [uri html]
  (let [target (target-file uri)]
    (.mkdirs (.getParentFile target))
    (spit target html)))

(defn- export-public-dir! []
  (let [public-dir (io/file "resources/public")]
    (when (.exists public-dir)
      (doseq [file (remove #(.isDirectory %) (file-seq public-dir))]
        (let [relative-path (.relativize (.toPath public-dir) (.toPath file))
              target (io/file export-dir (str relative-path))]
          (.mkdirs (.getParentFile target))
          (io/copy file target))))))

(defn- containing-roots []
  (->> [(System/getProperty "user.dir") (System/getProperty "java.io.tmpdir")]
       (remove str/blank?)
       (map #(.toPath (.getCanonicalFile (io/file %))))))

(defn- inside-root? [^java.nio.file.Path path]
  (boolean (some #(and (.startsWith path %) (not= path %)) (containing-roots))))

;; A recursive delete is destructive: only ever run it on the configured
;; export dir, and only when that resolves to a real subdirectory of the repo
;; or the temp dir (the tests rebind `export-dir` to a temp dir).
(defn- deletable-export-dir? [^java.io.File dir]
  (and (not (str/blank? export-dir))
       (not= "/" (str/trim export-dir))
       (let [path (.toPath (.getCanonicalFile dir))]
         (and (pos? (.getNameCount path))
              (inside-root? path)))))

(defn- delete-dir! [^java.io.File dir]
  (doseq [file (reverse (file-seq dir))]
    (.delete file)))

(defn- clean-export-dir! []
  (let [dir (io/file export-dir)]
    (cond
      (not (deletable-export-dir? dir))
      (binding [*out* *err*]
        (println "Refusing to clean export dir outside the repo or temp dir:"
                 (pr-str export-dir)))

      (.exists dir)
      (delete-dir! dir))))

(defn- write-cname! []
  (when-let [domain (not-empty (System/getenv "CNAME"))]
    (spit (io/file export-dir "CNAME") domain)))

(defn -main [& _]
  (clean-export-dir!)
  (.mkdirs (io/file export-dir))
  (doseq [uri ["/" "/blog/" "/en/" "/en/blog/" "/404.html" "/robots.txt" "/sitemap.xml" "/feed.xml" "/en/feed.xml"]]
    (write-page! uri (:body (site/page-for-uri uri))))
  (doseq [post (content/posts)]
    (write-page! (:uri post)
                 (:body (site/page-for-uri (:uri post)))))
  (export-public-dir!)
  (write-cname!))
