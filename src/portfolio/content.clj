(ns portfolio.content
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [markdown.core :as markdown]))

(defn load-site-config []
  (if-let [resource (io/resource "content/site.edn")]
    (edn/read-string (slurp resource))
    (throw (ex-info "Missing required resource: content/site.edn" {}))))

(def ^:private cached-site-config (delay (load-site-config)))
(defn site-config [] @cached-site-config)

(defn- classpath-entries []
  (-> (System/getProperty "java.class.path")
      (str/split (re-pattern (java.util.regex.Pattern/quote (System/getProperty "path.separator"))))))

(defn- file-resource-urls [classpath-entry path]
  (let [dir (io/file classpath-entry path)]
    (if (and dir (.exists dir))
      (->> (.listFiles dir)
           (filter #(.isFile %))
           (sort-by #(.getName %))
           (map #(.toURL (.toURI %))))
      [])))

(defn- jar-resource-urls [classpath-entry path]
  (let [jar-file (io/file classpath-entry)
        jar-file-url (.toURL (.toURI jar-file))
        resource-prefix (str (str/replace path #"/+$" "") "/")]
    (with-open [jar (java.util.jar.JarFile. jar-file)]
      (->> (enumeration-seq (.entries jar))
           (keep (fn [entry]
                   (let [name (.getName ^java.util.jar.JarEntry entry)]
                     (when (and (not (.isDirectory ^java.util.jar.JarEntry entry))
                                (str/starts-with? name resource-prefix)
                                (let [nested-path (subs name (count resource-prefix))]
                                  (not (str/includes? nested-path "/"))))
                       (java.net.URL.
                        (str "jar:" (.toExternalForm jar-file-url) "!/" name))))))
           (sort-by #(.toExternalForm ^java.net.URL %))))))

(defn- list-resource-urls [path]
  (->> (classpath-entries)
       (mapcat (fn [entry]
                 (cond
                   (.isDirectory (io/file entry)) (file-resource-urls entry path)
                   (str/ends-with? entry ".jar") (jar-resource-urls entry path)
                   :else [])))
       (sort-by #(.toExternalForm ^java.net.URL %))))

(defn- parse-front-matter [raw]
  (let [[_ fm body] (re-matches #"(?s)^---\n(.*?)\n---\n(.*)$" raw)]
    [(some-> fm edn/read-string) (or body raw)]))

(defn- resource-basename [resource-url]
  (some->> (.toExternalForm ^java.net.URL resource-url)
           (re-find #"([^/]+)$")
           second))

(defn- reading-time [text]
  (let [words (count (re-seq #"\S+" text))]
    (max 1 (long (Math/ceil (/ words 220.0))))))

(defn- slugify [value]
  (-> value
      str/lower-case
      (str/replace #"[^a-z0-9]+" "-")
      (str/replace #"(^-+|-+$)" "")))

(def ^:private html-entities
  {"&amp;" "&" "&lt;" "<" "&gt;" ">" "&quot;" "\"" "&apos;" "'" "&#39;" "'" "&nbsp;" " "})

(defn- decode-entities [text]
  (str/replace text #"&(?:amp|lt|gt|quot|apos|nbsp|#39);" #(get html-entities % %)))

(defn- heading-title
  "Plain-text title for a rendered heading: drops inline tags and entities."
  [inner-html]
  (-> inner-html
      (str/replace #"<[^>]*>" "")
      decode-entities
      (str/replace #"\s+" " ")
      str/trim))

(defn- strip-id-attr [attrs]
  (str/replace attrs #"(?i)\s+id\s*=\s*(?:\"[^\"]*\"|'[^']*'|[^\s>]+)" ""))

(defn- unique-anchor
  "First unused anchor from base, base-2, base-3, ..."
  [used base]
  (let [base (if (str/blank? base) "section" base)]
    (loop [n 1]
      (let [candidate (if (= n 1) base (str base "-" n))]
        (if (contains? used candidate)
          (recur (inc n))
          candidate)))))

(def ^:private heading-pattern #"(?s)<h([23])((?:\s[^>]*)?)>(.*?)</h\1>")

(defn- annotate-headings
  "Injects a unique id on every rendered h2/h3 and returns [html headings].
   Deriving both from the same pass keeps :headings and the ids in :html
   consistent by construction, and skips headings that markdown rendered as
   code (fenced blocks) rather than as heading elements."
  [html]
  (let [matcher (re-matcher heading-pattern html)
        buffer (StringBuffer.)]
    (loop [used #{}
           headings []]
      (if (.find matcher)
        (let [level (Integer/parseInt (.group matcher 1))
              attrs (strip-id-attr (or (.group matcher 2) ""))
              inner (.group matcher 3)
              title (heading-title inner)
              anchor (unique-anchor used (slugify title))
              replacement (str "<h" level " id=\"" anchor "\"" attrs ">" inner "</h" level ">")]
          (.appendReplacement matcher buffer (java.util.regex.Matcher/quoteReplacement replacement))
          (recur (conj used anchor)
                 (conj headings {:level level :title title :anchor anchor})))
        (do (.appendTail matcher buffer)
            [(str buffer) headings])))))

(def ^:private date-formatters
  {:fr (java.time.format.DateTimeFormatter/ofPattern "d MMMM yyyy" java.util.Locale/FRENCH)
   :en (java.time.format.DateTimeFormatter/ofPattern "MMMM d, yyyy" java.util.Locale/ENGLISH)})

(defn- format-date-label
  "Localized label for an ISO date, or nil when the date is missing/unparsable."
  [date locale]
  (when (string? date)
    (try
      (.format (java.time.LocalDate/parse (str/trim date))
               (get date-formatters locale (:en date-formatters)))
      (catch Exception _ nil))))

;; A card renders :category and :tags as one undifferentiated row of chips, and
;; the tag filter has to offer that same vocabulary or a chip exists that no
;; button can match. Category leads; a category repeated in :tags collapses.
(defn post-tags [post]
  (distinct (remove str/blank? (cons (:category post) (:tags post)))))

(defn- normalize-post [resource-url]
  (let [raw (slurp resource-url)
        [front-matter body] (parse-front-matter raw)
        slug (or (:slug front-matter)
                 (-> (resource-basename resource-url)
                     (str/replace #"\.[^.]+$" "")))
        excerpt (or (:excerpt front-matter)
                    (-> body
                        str/split-lines
                        (->> (remove str/blank?) first)
                        (or "")
                        (str/replace #"#{1,6}\s+" "")
                        (str/replace #"\*{1,2}([^*]+)\*{1,2}" "$1")
                        (str/replace #"_{1,2}([^_]+)_{1,2}" "$1")
                        (str/replace #"\[([^\]]+)\]\([^)]+\)" "$1")
                        (str/replace #"`([^`]+)`" "$1")))
        locale (keyword (or (:locale front-matter) "fr"))
        [html headings] (annotate-headings (markdown/md-to-html-string body))]
    (merge
     front-matter
     {:slug slug
      :locale locale
      :uri (if (= locale :fr)
             (str "/blog/" slug "/")
             (str "/en/blog/" slug "/"))
      :reading-time (reading-time body)
      :all-tags (post-tags front-matter)
      :headings headings
      :date-label (or (:date-label front-matter)
                      (format-date-label (:date front-matter) locale))
      :content body
      :html html
      :excerpt excerpt})))

(defn- parse-post-safe [resource-url]
  (try
    (normalize-post resource-url)
    (catch Exception ex
      (binding [*out* *err*]
        (println "Warning: skipping post" (.toExternalForm ^java.net.URL resource-url) "-" (.getMessage ex)))
      nil)))

(defn load-posts []
  (->> (list-resource-urls "content/posts")
       (keep parse-post-safe)
       (sort-by (juxt :locale :date) #(compare %2 %1))
       vec))

(def ^:private cached-posts (delay (load-posts)))
(defn posts [] @cached-posts)

(defn posts-for-locale [locale]
  (filter #(= locale (:locale %)) (posts)))

(defn find-post [locale slug]
  (some #(when (and (= locale (:locale %))
                    (= slug (:slug %)))
           %)
        (posts)))

(defn related-posts [locale slug]
  (let [current (find-post locale slug)
        current-tags (set (:tags current))
        overlap (fn [post] (count (set/intersection current-tags (set (:tags post)))))]
    (->> (posts-for-locale locale)
         (remove #(= slug (:slug %)))
         (filter #(pos? (overlap %)))
         (sort-by (juxt overlap :date) #(compare %2 %1))
         (take 4))))

(defn popular-tags [locale]
  (->> (posts-for-locale locale)
       (mapcat post-tags)
       frequencies
       (sort-by (juxt (comp - val) key))
       (map key)
       (take 8)))

(defn alternate-post [locale slug]
  (let [post (find-post locale slug)
        alt-locale (if (= locale :fr) :en :fr)
        alt-slug (:alternate-slug post)]
    (when alt-slug (find-post alt-locale alt-slug))))

(defn locale-copy [locale]
  (get-in (site-config) [:site :locales locale]))
