(ns portfolio.content-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [portfolio.content :as content]
            [portfolio.site :as site])
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

(deftest loads-posts-by-locale
  (testing "posts are split by locale"
    (is (seq (content/posts-for-locale :fr)))
    (is (seq (content/posts-for-locale :en)))))

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

(deftest parsed-posts-derive-metadata
  (testing "post parsing keeps content-derived metadata stable"
    (let [post (first (content/posts-for-locale :en))]
      (is (= "designing-api-boundaries-that-age-well" (:slug post)))
      (is (= [{:level 2 :title "Introduction"}
              {:level 2 :title "Prefer stable nouns over clever verbs"}
              {:level 2 :title "Model state transitions on purpose"}
              {:level 2 :title "Design for operators too"}
              {:level 2 :title "Conclusion"}]
             (:headings post)))
      (is (pos? (:reading-time post)))
      (is (string? (:excerpt post))))))

(deftest article-routes-exist
  (testing "known posts render to article pages"
    (doseq [post content/posts]
      (is (= 200 (:status (site/page-for-uri (:uri post))))))))

(deftest home-page-renders-real-links
  (testing "home page uses localized copy and avoids placeholder anchors"
    (let [html (:body (site/page-for-uri "/"))]
      (is (str/includes? html ">ABOUT<"))
      (is (str/includes? html ">Blog<"))
      (is (not (str/includes? html "href=\"#\""))))))
