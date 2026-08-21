(ns portfolio.test-support
  "Helpers that let tests assert on structure and data instead of on prose.

   The HTML helpers parse opening tags into attribute maps, so assertions do
   not depend on the order Hiccup emits attributes in, nor on the entity
   encoding it applies to their values. Expected copy is read back out of
   `content/locale-copy` rather than hardcoded, so rewording `site.edn` is
   free while the wiring stays covered."
  (:require [clojure.string :as str]
            [portfolio.content :as content]
            [portfolio.site :as site]))

;; ---------------------------------------------------------------- HTML

(def ^:private entities
  {"&amp;" "&" "&lt;" "<" "&gt;" ">" "&quot;" "\"" "&apos;" "'" "&#39;" "'" "&nbsp;" " "})

(defn decode-entities [text]
  (str/replace (str text) #"&(?:amp|lt|gt|quot|apos|nbsp|#39);" #(get entities % %)))

(def ^:private element-pattern
  #"<([a-zA-Z][a-zA-Z0-9]*)((?:\s+[a-zA-Z_:][-a-zA-Z0-9_:.]*(?:=\"[^\"]*\")?)*)\s*/?>")

(def ^:private attr-pattern #"([a-zA-Z_:][-a-zA-Z0-9_:.]*)=\"([^\"]*)\"")

(defn- parse-attrs [attr-string]
  (into {}
        (map (fn [[_ k v]] [k (decode-entities v)]))
        (re-seq attr-pattern attr-string)))

(defn elements
  "Every opening tag in `html` as {:tag \"a\" :attrs {\"href\" \"/\"}}."
  [html]
  (map (fn [[_ tag attr-string]] {:tag tag :attrs (parse-attrs attr-string)})
       (re-seq element-pattern html)))

(defn- classes [element]
  (set (remove str/blank? (str/split (get-in element [:attrs "class"] "") #"\s+"))))

(defn elements-with-tag [html tag]
  (filter #(= tag (:tag %)) (elements html)))

(defn attrs-with-class
  "Attribute maps of every element whose class list contains `class`."
  [html class]
  (->> (elements html)
       (filter #(contains? (classes %) class))
       (map :attrs)))

(defn attr-values
  "Decoded values of `attr` across the whole document."
  [html attr]
  (into #{} (keep #(get (:attrs %) attr)) (elements html)))

(defn meta-content
  "Content of the <meta> tag identified by `key` on either name or property."
  [html key]
  (some (fn [{:keys [tag attrs]}]
          (when (and (= "meta" tag)
                     (or (= key (get attrs "name"))
                         (= key (get attrs "property"))))
            (get attrs "content")))
        (elements html)))

(defn links-with-rel
  "Attribute maps of every <link rel=...> matching `rel`."
  [html rel]
  (->> (elements-with-tag html "link")
       (map :attrs)
       (filter #(= rel (get % "rel")))))

;; ---------------------------------------------------------------- JSON-LD

(defn json-ld
  "Raw JSON-LD payloads embedded in the page."
  [html]
  (map second (re-seq #"(?s)<script type=\"application/ld\+json\">(.*?)</script>" html)))

(defn- unescape-json [s]
  (-> s
      (str/replace "\\u003c" "<")
      (str/replace "\\u003e" ">")
      (str/replace "\\n" "\n")
      (str/replace "\\t" "\t")
      (str/replace "\\\"" "\"")
      (str/replace "\\\\" "\\")))

(defn json-field
  "First value of the string field `key`, wherever it sits in the payload."
  [json key]
  (some-> (re-find (re-pattern (str "\"" key "\":\"((?:[^\"\\\\]|\\\\.)*)\"")) json)
          second
          unescape-json))

;; ---------------------------------------------------------------- pages

(defn page [uri] (site/page-for-uri uri))

(defn html-for [uri] (:body (page uri)))

(def static-page-uris ["/" "/blog/" "/fr/" "/fr/blog/" "/404.html"])

(defn post-uris [] (mapv :uri (content/posts)))

(defn all-page-uris [] (into static-page-uris (post-uris)))

(defn locale-of
  "The locale a rendered URI belongs to. English owns the root; French lives
  under /fr/. Legacy /en/ stubs are English but never carry locale content."
  [uri]
  (if (str/starts-with? uri "/fr/") :fr :en))

;; ---------------------------------------------------------------- content

(defn copy [locale key] (get (content/locale-copy locale) key))

(defn site-value [key] (get (:site (content/site-config)) key))

(defn absolute [uri] (str (site-value :site-url) uri))

(defn stub-post
  "A minimally complete post map, for tests that need content they control."
  [& {:as overrides}]
  (merge {:slug "stub"
          :locale :en
          :uri "/blog/stub/"
          :title "Stub"
          :excerpt "Stub excerpt."
          :date "2026-01-01"
          :date-label "January 1, 2026"
          :tags []
          :headings []
          :reading-time 1
          :content "Body."
          :html "<p>Body.</p>"}
         overrides))
