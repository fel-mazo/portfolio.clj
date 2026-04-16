(ns portfolio.export-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [portfolio.export :as export]))

(defn- temp-dir []
  (.toFile (java.nio.file.Files/createTempDirectory
            "portfolio-export-test"
            (make-array java.nio.file.attribute.FileAttribute 0))))

(deftest export-writes-launch-assets
  (let [dir (temp-dir)]
    (with-redefs [export/export-dir (.getAbsolutePath dir)]
      (export/-main)
      (is (.exists (io/file dir "404.html")))
      (is (.exists (io/file dir "robots.txt")))
      (is (.exists (io/file dir "sitemap.xml")))
      (is (.exists (io/file dir "favicon.svg"))))))

(deftest sitemap-contains-real-public-routes
  (let [dir (temp-dir)]
    (with-redefs [export/export-dir (.getAbsolutePath dir)]
      (export/-main)
      (let [xml (slurp (io/file dir "sitemap.xml"))]
        (is (str/includes? xml "<loc>https://fel-mazo.com/</loc>"))
        (is (str/includes? xml "<loc>https://fel-mazo.com/en/blog/designing-api-boundaries-that-age-well/</loc>"))
        (is (not (str/includes? xml "404.html")))))))
