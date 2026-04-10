(ns portfolio.export
  (:require [clojure.java.io :as io]
            [portfolio.content :as content]
            [portfolio.site :as site])
  (:gen-class))

(def export-dir "dist")

(defn- relative-uri [uri]
  (let [trimmed (subs uri 1)]
    (if (empty? trimmed) "index.html" trimmed)))

(defn- write-page! [uri html]
  (let [path (relative-uri uri)
        target (if (= path "index.html")
                 (io/file export-dir path)
                 (io/file export-dir path "index.html"))]
    (.mkdirs (.getParentFile target))
    (spit target html)))

(defn- export-static-asset! [resource-path]
  (if-let [resource (io/resource resource-path)]
    (let [target (io/file export-dir (.getName (io/file resource-path)))]
      (.mkdirs (.getParentFile target))
      (io/copy (io/input-stream resource) target))
    (binding [*out* *err*]
      (println "Warning: static asset not found:" resource-path))))

(defn -main [& _]
  (.mkdirs (io/file export-dir))
  (doseq [uri ["/" "/blog/" "/en/" "/en/blog/"]]
    (write-page! uri (:body (site/page-for-uri uri))))
  (doseq [post (content/posts)]
    (write-page! (:uri post)
                 (:body (site/page-for-uri (:uri post)))))
  (export-static-asset! "public/site.css"))
