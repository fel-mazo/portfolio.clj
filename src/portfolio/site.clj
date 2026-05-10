(ns portfolio.site
  (:require [clojure.string :as str]
            [portfolio.content :as content]
            [portfolio.templates :as templates]))

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

(defn- absolute-url [uri]
  (str (site-url) uri))

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
   :image-url (or image-url (:portrait-url (site-data)))
   :robots robots})

(defn- localized-site [locale]
  (merge (select-keys (site-data) [:name :logo :site-url :email :socials :cv-link])
         (select-keys (content/locale-copy locale)
                      [:cv-label :contact-label :copyright
                       :privacy-label :terms-label :cookies-label])
         {:privacy-link "#" :terms-link "#" :cookies-link "#"}))

(defn- navigation [locale]
  (let [copy (content/locale-copy locale)
        prefix (locale-prefix locale)]
    [{:href (str prefix "/#about") :label (:about-nav copy)}
     {:href (str prefix "/#projects") :label (:projects copy)}
     {:href (str prefix "/#contact") :label (:contact copy)}
     {:href (str prefix "/blog/") :label (:blog copy)}]))

(defn render-home [locale]
  (let [copy (content/locale-copy locale)
        prefix (locale-prefix locale)
        site (localized-site locale)
        uri (if (= locale :fr) "/" "/en/")
        projects (:projects (content/site-config))]
    (templates/layout
     {:locale locale
      :base-path (base-path)
      :title (:home-title copy)
      :description (:home-description copy)
      :meta (page-meta uri)
      :site site
      :labels copy
      :navigation (navigation locale)
      :page-class "theme-home"
      :header-class "site-header--home"
      :footer-class "site-footer--home"
      :body
      [:main {:id "main-content"}
       (templates/home-page
        {:name (:hero-name copy)
         :role (:hero-role copy)
         :summary (:hero-summary copy)
         :about-tag (:about-tag copy)
         :about-title (:about-title copy)
         :about-heading (:about-heading copy)
         :about-body (:about-body copy)
         :portrait-url (:portrait-url (site-data))
         :scroll-label (:scroll-label copy)
         :socials (:socials site)
         :contact-label (:home-contact-cta copy)
         :contact-href (str prefix "/#contact")})
       (templates/portfolio-section
        {:title (:portfolio-home-tag copy)
         :heading (:portfolio-home-heading copy)
         :cta-label (:portfolio-cta copy)
         :cta-href (str prefix "/blog/")
         :project-link-label (:project-link-label copy)
         :projects projects
         :home? true})]})))

(defn render-blog-index [locale]
  (let [copy (content/locale-copy locale)
        uri (if (= locale :fr) "/blog/" "/en/blog/")]
    (templates/layout
     {:locale locale
      :base-path (base-path)
      :title (:blog-title copy)
      :description (:blog-description copy)
      :meta (page-meta uri)
      :site (localized-site locale)
      :labels copy
      :navigation (navigation locale)
      :page-class "theme-home"
      :header-class "site-header--home"
      :footer-class "site-footer--home"
      :body (templates/blog-index-section
             {:eyebrow (:blog-eyebrow copy)
              :title (:blog-heading copy)
              :intro (:blog-intro copy)
              :tags-intro (:blog-tags-intro copy)
              :scroll-label (:scroll-label copy)
              :blog-list-label (:blog-list-label copy)
              :post-link-label (:post-link-label copy)
              :tags (content/popular-tags locale)
              :posts (prefix-uris (content/posts-for-locale locale))})})))

(defn render-article [locale slug]
  (when-let [post (content/find-post locale slug)]
    (let [copy (content/locale-copy locale)
          prefix (locale-prefix locale)]
      (templates/layout
       {:locale locale
        :base-path (base-path)
        :title (:title post)
        :description (:excerpt post)
        :meta (page-meta (:uri post) :og-type "article")
        :site (localized-site locale)
        :labels copy
        :navigation (navigation locale)
        :page-class "theme-home"
        :header-class "site-header--home"
        :footer-class "site-footer--home"
        :body (templates/article-page
               {:post (prefix-uri post)
                :related-posts (prefix-uris (content/related-posts locale slug))
                :labels {:eyebrow (:blog-eyebrow copy)
                         :all-posts (str prefix "/blog/")
                         :all-posts-label (:all-posts copy)
                         :project-link (:project-link post)
                         :project-label (:project-label copy)
                         :reading-time-label (:reading-time-label copy)
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
      :page-class "theme-home"
      :header-class "site-header--home"
      :footer-class "site-footer--home"
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

(defn- robots-txt []
  (str "User-agent: *\n"
       "Allow: /\n"
       "Sitemap: " (absolute-url "/sitemap.xml") "\n"))

(defn- sitemap-xml []
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
       "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n"
       (apply str
              (for [uri (public-routes)]
                (str "  <url><loc>" (absolute-url uri) "</loc></url>\n")))
       "</urlset>\n"))

(defn page-for-uri [uri]
  (case uri
    "/" (home-response :fr)
    "/index.html" (home-response :fr)
    "/blog/" (blog-index-response :fr)
    "/en/" (home-response :en)
    "/en/index.html" (home-response :en)
    "/en/blog/" (blog-index-response :en)
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
