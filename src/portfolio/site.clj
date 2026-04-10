(ns portfolio.site
  (:require [portfolio.content :as content]
            [portfolio.templates :as templates]))

(defn- locale-prefix [locale]
  (if (= locale :fr) "" "/en"))

(defn- site-data []
  (:site (content/site-config)))

(defn- localized-site [locale]
  (let [global (site-data)
        copy (content/locale-copy locale)]
    {:name (:name global)
     :logo (:logo global)
     :email (:email global)
     :socials (:socials global)
     :cv-link (:cv-link global)
     :cv-label (:cv-label copy)
     :contact-label (:contact-label copy)
     :copyright (:copyright copy)
     :privacy-label (:privacy-label copy)
     :privacy-link "#"
     :terms-label (:terms-label copy)
     :terms-link "#"
     :cookies-label (:cookies-label copy)
     :cookies-link "#"}))

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
        projects (:projects (content/site-config))]
    (templates/layout
     {:locale locale
      :title (:home-title copy)
      :description (:home-description copy)
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
  (let [copy (content/locale-copy locale)]
    (templates/layout
     {:locale locale
      :title (:blog-title copy)
      :description (:blog-description copy)
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
              :posts (content/posts-for-locale locale)})})))

(defn render-article [locale slug]
  (when-let [post (content/find-post locale slug)]
    (let [copy (content/locale-copy locale)
          prefix (locale-prefix locale)]
      (templates/layout
       {:locale locale
        :title (:title post)
        :description (:excerpt post)
        :site (localized-site locale)
        :labels copy
        :navigation (navigation locale)
        :page-class "theme-home"
        :header-class "site-header--home"
        :footer-class "site-footer--home"
        :body (templates/article-page
               {:post post
                :related-posts (content/related-posts locale slug)
                :labels {:eyebrow (:blog-eyebrow copy)
                         :all-posts (str prefix "/blog/")
                         :all-posts-label (:all-posts copy)
                         :project-link (:project-link post)
                         :project-label (:project-label copy)
                         :reading-time-label (:reading-time-label copy)
                         :date-label-copy (:date-label-copy copy)
                         :related-posts-label (:related-posts-label copy)}})}))))

(defn page-for-uri [uri]
  (case uri
    "/" {:status 200 :body (render-home :fr)}
    "/index.html" {:status 200 :body (render-home :fr)}
    "/blog/" {:status 200 :body (render-blog-index :fr)}
    "/en/" {:status 200 :body (render-home :en)}
    "/en/index.html" {:status 200 :body (render-home :en)}
    "/en/blog/" {:status 200 :body (render-blog-index :en)}
    (let [fr-match (re-matches #"/blog/([^/]+)/" uri)
          en-match (re-matches #"/en/blog/([^/]+)/" uri)]
      (cond
        fr-match (if-let [page (render-article :fr (second fr-match))]
                   {:status 200 :body page}
                   {:status 404 :body "Not found"})
        en-match (if-let [page (render-article :en (second en-match))]
                   {:status 200 :body page}
                   {:status 404 :body "Not found"})
        :else {:status 404 :body "Not found"}))))
