(ns portfolio.site
  (:require [portfolio.content :as content]
            [portfolio.templates :as templates]))

(defn- localized-site [locale]
  (let [global (:site content/site-config)
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
        prefix (if (= locale :fr) "" "/en")]
    [{:href (str prefix "/#projects") :label (:projects copy)}
     {:href (str prefix "/blog/") :label (:blog copy)}
     {:href (str prefix "/#contact") :label (:contact copy)}]))

(defn render-home [locale]
  (let [copy (content/locale-copy locale)
        prefix (if (= locale :fr) "" "/en")
        site (localized-site locale)]
    (templates/layout
     {:locale locale
      :title (:home-title copy)
      :description (:home-description copy)
      :site site
      :navigation (navigation locale)
      :body
      [:main
       (templates/hero-section
        {:eyebrow (:hero-eyebrow copy)
         :name (:hero-name copy)
         :intro (:hero-intro copy)
         :cta-label (:hero-cta copy)
         :cta-href (str prefix "/#about")
         :portrait-url (:portrait-url (:site content/site-config))})
       (templates/stats-section
        {:title (:about-title copy)
         :body (:about-body copy)
         :stats (:stats copy)
         :languages (:languages copy)
         :cta-label (:about-cta copy)
         :cta-href (str prefix "/blog/")})
       (templates/portfolio-section
        {:title (:portfolio-title copy)
         :cta-label (:portfolio-cta copy)
         :cta-href (str prefix "/blog/")
         :projects (:projects content/site-config)})]})))

(defn render-blog-index [locale]
  (let [copy (content/locale-copy locale)]
    (templates/layout
     {:locale locale
      :title (:blog-title copy)
      :description (:blog-description copy)
      :site (localized-site locale)
      :navigation (navigation locale)
      :body (templates/blog-index-section
             {:eyebrow (:blog-eyebrow copy)
              :title (:blog-heading copy)
              :intro (:blog-intro copy)
              :posts (content/posts-for-locale locale)})})))

(defn render-article [locale slug]
  (when-let [post (content/find-post locale slug)]
    (let [copy (content/locale-copy locale)
          prefix (if (= locale :fr) "" "/en")]
      (templates/layout
       {:locale locale
        :title (:title post)
        :description (:excerpt post)
        :site (localized-site locale)
        :navigation (navigation locale)
        :body (templates/article-page
               {:post post
                :labels {:eyebrow (:blog-eyebrow copy)
                         :all-posts (str prefix "/blog/")
                         :all-posts-label (:all-posts copy)}})}))))

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
