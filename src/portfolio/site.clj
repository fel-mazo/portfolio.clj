(ns portfolio.site
  (:require [clojure.string :as str]
            [portfolio.content :as content]
            [portfolio.templates :as templates])
  (:import [java.time LocalDate ZoneOffset]
           [java.time.format DateTimeFormatter]))

(defn- site-data []
  (:site (content/site-config)))

(defn- base-path []
  (or (System/getenv "BASE_PATH") (:base-path (site-data)) ""))

(defn- prefix-uri [post]
  (update post :uri #(str (base-path) %)))

(defn- prefix-uris [posts]
  (mapv prefix-uri posts))

(defn- locale-prefix [locale]
  (str (base-path) (if (= locale :fr) "" "/en")))

(defn- site-url []
  (:site-url (site-data)))

;; Deployment root. Under a non-empty base-path (a GitHub project page served
;; from /repo/) every absolute URL we publish — canonical, og:url, hreflang,
;; sitemap <loc>, feed <link> — has to carry the prefix, or it points at a 404.
(defn- site-root-url []
  (str (site-url) (base-path)))

(defn- absolute-url [uri]
  (str (site-root-url) uri))

(defn- absolute-image-url [url]
  (when url
    (if (str/starts-with? url "http")
      url
      (absolute-url url))))

(defn- html-response [status body]
  {:status status
   :headers {"content-type" "text/html; charset=utf-8"}
   :body body})

(defn- text-response [status body content-type]
  {:status status
   :headers {"content-type" content-type}
   :body body})

(defn- page-meta [uri & {:keys [og-type robots image-url]
                         :or {og-type "website"
                              robots "index,follow"}}]
  {:canonical-url (absolute-url uri)
   :og-type og-type
   :image-url (absolute-image-url (or image-url (:portrait-url (site-data))))
   :robots robots})

(defn- hreflang-map [locale self-uri alt-uri]
  (let [default-uri (if (= locale :fr) self-uri alt-uri)]
    {:self-lang (name locale)
     :self-url (absolute-url self-uri)
     :alt-lang (name (if (= locale :fr) :en :fr))
     :alt-url (absolute-url alt-uri)
     :default-url (absolute-url default-uri)}))

(defn- person-schema [locale]
  (let [site (site-data)
        copy (content/locale-copy locale)]
    {"@context" "https://schema.org"
     "@type" "Person"
     "name" (:name site)
     "url" (site-root-url)
     "jobTitle" (:job-title copy)
     "sameAs" (mapv :href (:socials site))}))

(defn- article-schema [post]
  {"@context" "https://schema.org"
   "@type" "BlogPosting"
   "headline" (:title post)
   "description" (:excerpt post)
   "datePublished" (:date post)
   "url" (absolute-url (:uri post))
   "author" {"@type" "Person"
             "name" (:name (site-data))
             "url" (site-root-url)}})

(defn- collection-schema [locale posts]
  {"@context" "https://schema.org"
   "@type" "CollectionPage"
   "name" (:blog-title (content/locale-copy locale))
   "url" (absolute-url (if (= locale :fr) "/blog/" "/en/blog/"))
   "hasPart" (mapv (fn [p] {"@type" "BlogPosting"
                             "headline" (:title p)
                             "url" (absolute-url (:uri p))})
                   posts)})

(defn- localized-site [locale]
  (merge (select-keys (site-data) [:name :site-url :socials])
         (select-keys (content/locale-copy locale) [:copyright])))

(defn- social-href
  "Href of the named social link, nil when the site has no such entry."
  [label]
  (some (fn [{:keys [href] :as social}]
          (when (= label (:label social)) href))
        (:socials (site-data))))

(defn- navigation [locale]
  (let [copy (content/locale-copy locale)
        prefix (locale-prefix locale)]
    [{:href (str prefix "/#about") :label (:about-nav copy)}
     {:href (str prefix "/#contact") :label (:contact copy)}
     {:href (str prefix "/blog/") :label (:blog copy)}]))

(defn render-home [locale]
  (let [copy (content/locale-copy locale)
        prefix (locale-prefix locale)
        site (localized-site locale)
        uri (if (= locale :fr) "/" "/en/")
        alt-uri (if (= locale :fr) "/en/" "/")]
    (templates/layout
     {:locale locale
      :base-path (base-path)
      :title (:home-title copy)
      :description (:home-description copy)
      :meta (page-meta uri)
      :hreflang (hreflang-map locale uri alt-uri)
      :json-ld (person-schema locale)
      :site site
      :labels copy
      :navigation (navigation locale)
      :body (templates/home-page
             {:name (:hero-name copy)
              :role (:hero-role copy)
              :summary (:hero-summary copy)
              :about-tag (:about-tag copy)
              :about-title (:about-title copy)
              :about-heading (:about-heading copy)
              :about-body (:about-body copy)
              :portrait-url (:portrait-url (site-data))
              :scroll-label (:scroll-label copy)
              :contact-label (:home-contact-cta copy)
              :contact-href (or (social-href "LinkedIn") (str prefix "/#contact"))})})))

(defn render-blog-index [locale]
  (let [copy (content/locale-copy locale)
        uri (if (= locale :fr) "/blog/" "/en/blog/")
        alt-uri (if (= locale :fr) "/en/blog/" "/blog/")
        posts (content/posts-for-locale locale)]
    (templates/layout
     {:locale locale
      :base-path (base-path)
      :title (:blog-title copy)
      :description (:blog-description copy)
      :meta (page-meta uri)
      :hreflang (hreflang-map locale uri alt-uri)
      :json-ld (collection-schema locale posts)
      :site (localized-site locale)
      :labels copy
      :navigation (navigation locale)
      :body (templates/blog-index-section
             {:eyebrow (:blog-eyebrow copy)
              :title (:blog-heading copy)
              :intro (:blog-intro copy)
              :tag-filter-label (:tag-filter-label copy)
              :blog-list-label (:blog-list-label copy)
              :post-link-label (:post-link-label copy)
              :tags (content/popular-tags locale)
              :posts (prefix-uris posts)})})))

(defn render-article [locale slug]
  (when-let [post (content/find-post locale slug)]
    (let [copy (content/locale-copy locale)
          prefix (locale-prefix locale)
          alt-post (content/alternate-post locale slug)]
      (templates/layout
       {:locale locale
        :base-path (base-path)
        :title (:title post)
        :description (:excerpt post)
        :meta (page-meta (:uri post) :og-type "article")
        :hreflang (when alt-post
                    (hreflang-map locale (:uri post) (:uri alt-post)))
        :json-ld (article-schema post)
        :site (localized-site locale)
        :labels copy
        :navigation (navigation locale)
        :body (templates/article-page
               {:post (prefix-uri post)
                :related-posts (prefix-uris (content/related-posts locale slug))
                :labels {:eyebrow (:blog-eyebrow copy)
                         :all-posts (str prefix "/blog/")
                         :all-posts-label (:all-posts copy)
                         :project-link (:project-link post)
                         :project-label (:project-label copy)
                         :reading-time-label (:reading-time-label copy)
                         :reading-time-unit (:reading-time-unit copy)
                         :label-separator (:label-separator copy)
                         :date-label-copy (:date-label-copy copy)
                         :related-posts-label (:related-posts-label copy)}})}))))

(defn render-not-found [locale]
  (let [copy (content/locale-copy locale)
        site (localized-site locale)
        prefix (locale-prefix locale)
        home-href (str prefix "/")]
    (templates/layout
     {:locale locale
      :base-path (base-path)
      :title (:not-found-title copy)
      :description (:not-found-description copy)
      :meta (page-meta "/404.html" :robots "noindex,nofollow")
      :site site
      :labels copy
      :navigation (navigation locale)
      :body (templates/not-found-page
             {:eyebrow "404"
              :heading (:not-found-heading copy)
              :body (:not-found-body copy)
              :cta-label (:not-found-cta copy)
              :cta-href home-href})})))

(defn- home-response [locale]
  (html-response 200 (render-home locale)))

(defn- blog-index-response [locale]
  (html-response 200 (render-blog-index locale)))

(defn- article-response [locale slug]
  (if-let [article (render-article locale slug)]
    (html-response 200 article)
    (html-response 404 (render-not-found locale))))

(defn- not-found-response [locale]
  (html-response 404 (render-not-found locale)))

(defn- public-routes []
  (concat ["/" "/blog/" "/en/" "/en/blog/"]
          (map :uri (content/posts))))

(defn- escape-xml [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- rfc822-date [iso-date]
  (try
    (let [local-date (LocalDate/parse iso-date)
          zoned (.atStartOfDay local-date ZoneOffset/UTC)]
      (.format zoned (DateTimeFormatter/ofPattern "EEE, dd MMM yyyy HH:mm:ss Z" java.util.Locale/ENGLISH)))
    (catch Exception _ nil)))

(defn- render-rss-feed [locale]
  (let [site (site-data)
        copy (content/locale-copy locale)
        prefix (if (= locale :fr) "" "/en")
        posts (content/posts-for-locale locale)]
    (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
         "<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\">\n"
         "<channel>\n"
         "  <title>" (escape-xml (:name site)) " - " (escape-xml (:blog-title copy)) "</title>\n"
         "  <link>" (absolute-url (str prefix "/blog/")) "</link>\n"
         "  <description>" (escape-xml (:blog-description copy)) "</description>\n"
         "  <language>" (name locale) "</language>\n"
         "  <atom:link href=\"" (absolute-url (str prefix "/feed.xml")) "\" rel=\"self\" type=\"application/rss+xml\" />\n"
         (apply str
                (for [post posts]
                  (str "  <item>\n"
                       "    <title>" (escape-xml (:title post)) "</title>\n"
                       "    <link>" (absolute-url (:uri post)) "</link>\n"
                       "    <guid>" (absolute-url (:uri post)) "</guid>\n"
                       "    <description>" (escape-xml (:excerpt post)) "</description>\n"
                       (when-let [pub-date (some-> (:date post) rfc822-date)]
                         (str "    <pubDate>" pub-date "</pubDate>\n"))
                       "  </item>\n")))
         "</channel>\n"
         "</rss>\n")))

(defn- robots-txt []
  (str "User-agent: *\n"
       "Allow: /\n"
       "Sitemap: " (absolute-url "/sitemap.xml") "\n"))

(defn- post-date-for-uri [uri posts]
  (some #(when (= uri (:uri %)) (:date %)) posts))

(defn- latest-date [posts]
  (some->> posts seq (map :date) (remove nil?) sort last))

(defn- sitemap-entry [uri lastmod]
  (if lastmod
    (str "  <url><loc>" (absolute-url uri) "</loc><lastmod>" lastmod "</lastmod></url>\n")
    (str "  <url><loc>" (absolute-url uri) "</loc></url>\n")))

(defn- sitemap-xml []
  (let [all-posts (content/posts)
        fallback-date (latest-date all-posts)]
    (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
         "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n"
         (apply str
                (for [uri (public-routes)]
                  (sitemap-entry uri (or (post-date-for-uri uri all-posts) fallback-date))))
         "</urlset>\n")))

(defn page-for-uri [uri]
  (case uri
    "/" (home-response :fr)
    "/index.html" (home-response :fr)
    "/blog/" (blog-index-response :fr)
    "/en/" (home-response :en)
    "/en/index.html" (home-response :en)
    "/en/blog/" (blog-index-response :en)
    "/feed.xml" (text-response 200 (render-rss-feed :fr) "application/xml; charset=utf-8")
    "/en/feed.xml" (text-response 200 (render-rss-feed :en) "application/xml; charset=utf-8")
    "/robots.txt" (text-response 200 (robots-txt) "text/plain; charset=utf-8")
    "/sitemap.xml" (text-response 200 (sitemap-xml) "application/xml; charset=utf-8")
    "/404.html" (html-response 404 (render-not-found :fr))
    (let [fr-match (re-matches #"/blog/([^/]+)/" uri)
          en-match (re-matches #"/en/blog/([^/]+)/" uri)]
      (cond
        fr-match (article-response :fr (second fr-match))
        en-match (article-response :en (second en-match))
        (str/starts-with? uri "/en/") (not-found-response :en)
        :else (not-found-response :fr)))))
