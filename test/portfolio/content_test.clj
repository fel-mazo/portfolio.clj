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
      (is (= [{:level 2 :title "Introduction" :anchor "introduction"}
              {:level 2 :title "Prefer stable nouns over clever verbs" :anchor "prefer-stable-nouns-over-clever-verbs"}
              {:level 2 :title "Model state transitions on purpose" :anchor "model-state-transitions-on-purpose"}
              {:level 2 :title "Design for operators too" :anchor "design-for-operators-too"}
              {:level 2 :title "Conclusion" :anchor "conclusion"}]
             (:headings post)))
      (is (pos? (:reading-time post)))
      (is (string? (:excerpt post))))))

(deftest article-routes-exist
  (testing "known posts render to article pages"
    (doseq [post (content/posts)]
      (is (= 200 (:status (site/page-for-uri (:uri post))))))))

(deftest home-page-renders-real-links
  (testing "home page uses localized copy and avoids placeholder anchors"
    (let [html (:body (site/page-for-uri "/"))]
      (is (str/includes? html ">ABOUT<"))
      (is (str/includes? html ">Blog<"))
      (is (not (str/includes? html "href=\"#\""))))))

(deftest home-page-uses-real-project-content
  (testing "home page does not render scaffold portfolio placeholders"
    (let [html (:body (site/page-for-uri "/"))]
      (is (not (str/includes? html "TITRE PROJET")))
      (is (not (str/includes? html "Lorem ipsum")))
      (is (not (str/includes? html "TECHNO 1"))))))

(deftest rendered-pages-use-a-single-main-landmark
  (testing "home page keeps only one main landmark"
    (let [html (:body (site/page-for-uri "/"))]
      (is (= 1 (count (re-seq #"<main[ >]" html))))))
  (testing "blog index keeps only one main landmark"
    (let [html (:body (site/page-for-uri "/blog/"))]
      (is (= 1 (count (re-seq #"<main[ >]" html)))))))

(deftest blog-index-renders-real-post-tags
  (testing "blog cards use post metadata instead of placeholder tags"
    (let [html (:body (site/page-for-uri "/blog/"))]
      (is (str/includes? html ">Architecture<"))
      (is (str/includes? html ">Event-driven<"))
      (is (str/includes? html ">Fiabilite<"))
      (is (not (str/includes? html "TAG TECHNO")))
      (is (not (str/includes? html "TAG SUBJECTS"))))))

(deftest article-page-renders-linked-table-of-contents
  (testing "article toc links point to generated section anchors"
    (let [html (:body (site/page-for-uri "/en/blog/designing-api-boundaries-that-age-well/"))]
      (is (str/includes? html "href=\"#introduction\""))
      (is (str/includes? html "id=\"introduction\""))
      (is (not (str/includes? html ">SECTION 1<"))))))

(deftest rendering-uses-current-site-config
  (testing "page rendering pulls site content through functions at render time"
    (let [custom-config {:site {:name "Fahd El Mazouni"
                                :logo "FEM"
                                :email "hello@fahdelmazouni.dev"
                                :cv-link "#"
                                :portrait-url "https://example.com/portrait.jpg"
                                :socials []
                                :locales {:fr {:cv-label "CV"
                                               :contact-label "Contact"
                                               :copyright "2026"
                                               :privacy-label "Privacy"
                                               :terms-label "Terms"
                                               :cookies-label "Cookies"
                                               :about-nav "ABOUT"
                                               :projects "Projets"
                                               :blog "Blog"
                                               :contact "Contact"
                                               :home-title "Portfolio"
                                               :home-description "Description"
                                               :hero-name "Fahd El Mazouni"
                                               :hero-role "DEVELOPPEUR BACKEND"
                                               :about-tag "ABOUT"
                                               :about-title "WHO I AM"
                                               :about-heading "Heading from test"
                                               :about-body "Body from test"
                                               :home-contact-cta "CONTACT"
                                               :portfolio-title "Portfolio"
                                               :portfolio-cta "Voir tous les articles"
                                               :blog-title "Blog"
                                               :blog-description "Blog description"
                                               :blog-eyebrow "BLOG"
                                               :blog-heading "ARTICLES"
                                               :blog-intro "Intro"
                                               :blog-tags-intro "Themes"
                                               :all-posts "Tous les articles"
                                               :reading-time-label "Temps"
                                               :date-label-copy "Date"
                                               :related-posts-label "ARTICLES ASSOCIES"
                                               :project-label "VOIR LE PROJET"}}}
                         :projects []}]
      (with-redefs [content/load-site-config (constantly custom-config)]
        (let [html (:body (site/page-for-uri "/"))]
          (is (str/includes? html "Heading from test"))
          (is (str/includes? html "Body from test")))))))
