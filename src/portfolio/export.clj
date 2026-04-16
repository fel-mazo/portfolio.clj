(ns portfolio.export
  (:require [clojure.java.io :as io]
            [portfolio.content :as content]
            [portfolio.site :as site])
  (:gen-class))

(def export-dir "dist")

(defn- root-file? [uri]
  (#{"404.html" "robots.txt" "sitemap.xml"} (subs uri 1)))

(defn- target-file [uri]
  (let [trimmed (subs uri 1)]
    (cond
      (empty? trimmed) (io/file export-dir "index.html")
      (root-file? uri) (io/file export-dir trimmed)
      :else (io/file export-dir trimmed "index.html"))))

(defn- write-page! [uri html]
  (let [target (target-file uri)]
    (.mkdirs (.getParentFile target))
    (spit target html)))

(defn- export-static-asset! [resource-path]
  (if-let [resource (io/resource resource-path)]
    (let [target (io/file export-dir (.getName (io/file resource-path)))]
      (.mkdirs (.getParentFile target))
      (io/copy (io/input-stream resource) target))
    (binding [*out* *err*]
      (println "Warning: static asset not found:" resource-path))))

(defn- export-public-dir! []
  (let [public-dir (io/file "resources/public")]
    (when (.exists public-dir)
      (doseq [file (remove #(.isDirectory %) (file-seq public-dir))]
        (let [relative-path (.relativize (.toPath public-dir) (.toPath file))
              target (io/file export-dir (str relative-path))]
          (.mkdirs (.getParentFile target))
          (io/copy file target))))))

(defn -main [& _]
  (.mkdirs (io/file export-dir))
  (doseq [uri ["/" "/blog/" "/en/" "/en/blog/" "/404.html" "/robots.txt" "/sitemap.xml"]]
    (write-page! uri (:body (site/page-for-uri uri))))
  (doseq [post (content/posts)]
    (write-page! (:uri post)
                 (:body (site/page-for-uri (:uri post)))))
  (export-public-dir!))
