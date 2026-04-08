(ns portfolio.content
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [markdown.core :as markdown]))

(def site-config
  (-> "content/site.edn" io/resource slurp edn/read-string))

(defn- resource-path [path]
  (some-> path io/resource io/file))

(defn- list-resource-files [path]
  (let [dir (resource-path path)]
    (if (and dir (.exists dir))
      (->> (.listFiles dir)
           (filter #(.isFile %))
           (sort-by #(.getName %)))
      [])))

(defn- parse-front-matter [raw]
  (let [[_ fm body] (re-matches #"(?s)^---\n(.*?)\n---\n(.*)$" raw)]
    [(some-> fm edn/read-string) (or body raw)]))

(defn- reading-time [text]
  (let [words (count (re-seq #"\S+" text))]
    (max 1 (long (Math/ceil (/ words 220.0))))))

(defn- normalize-post [file]
  (let [raw (slurp file)
        [front-matter body] (parse-front-matter raw)
        slug (or (:slug front-matter)
                 (-> (.getName file)
                     (str/replace #"\.[^.]+$" "")))
        excerpt (or (:excerpt front-matter)
                    (-> body
                        str/split-lines
                        (->> (remove str/blank?) first)
                        (or "")))
        locale (keyword (or (:locale front-matter) "fr"))]
    (merge
     front-matter
     {:slug slug
      :locale locale
      :uri (if (= locale :fr)
             (str "/blog/" slug "/")
             (str "/en/blog/" slug "/"))
      :reading-time (reading-time body)
      :content body
      :html (markdown/md-to-html-string body)
      :date-label (:date-label front-matter)
      :excerpt excerpt})))

(def posts
  (->> (list-resource-files "content/posts")
       (map normalize-post)
       (sort-by (juxt :locale :date) #(compare %2 %1))
       vec))

(defn posts-for-locale [locale]
  (filter #(= locale (:locale %)) posts))

(defn featured-posts [locale]
  (take 3 (posts-for-locale locale)))

(defn find-post [locale slug]
  (some #(when (and (= locale (:locale %))
                    (= slug (:slug %)))
           %)
        posts))

(defn locale-copy [locale]
  (get-in site-config [:site/locales locale]))
