(ns portfolio.content
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
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

(defn- extract-headings [body]
  (->> (str/split-lines body)
       (keep (fn [line]
               (when-let [[_ hashes heading] (re-matches #"^(#{2,3})\s+(.+)$" line)]
                 {:level (count hashes)
                  :title heading
                  :anchor (slugify heading)})))
       vec))

(defn- inject-heading-anchors [html headings]
  (reduce (fn [rendered {:keys [level title anchor]}]
            (let [pattern (re-pattern
                           (str "(?s)<h" level ">"
                                (java.util.regex.Pattern/quote title)
                                "</h" level ">"))
                  replacement (str "<h" level " id=\"" anchor "\">" title "</h" level ">")]
              (str/replace-first rendered pattern replacement)))
          html
          headings))

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
                        (or "")))
        locale (keyword (or (:locale front-matter) "fr"))
        headings (extract-headings body)
        html (-> body markdown/md-to-html-string (inject-heading-anchors headings))]
    (merge
     front-matter
     {:slug slug
      :locale locale
      :uri (if (= locale :fr)
             (str "/blog/" slug "/")
             (str "/en/blog/" slug "/"))
      :reading-time (reading-time body)
      :headings headings
      :content body
      :html html
      :date-label (:date-label front-matter)
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
  (->> (posts-for-locale locale)
       (remove #(= slug (:slug %)))
       (take 4)))

(defn popular-tags [locale]
  (->> (posts-for-locale locale)
       (mapcat :tags)
       frequencies
       (sort-by (juxt (comp - val) key))
       (map key)
       (take 8)))

(defn locale-copy [locale]
  (get-in (site-config) [:site :locales locale]))
