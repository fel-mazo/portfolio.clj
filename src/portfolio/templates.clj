(ns portfolio.templates
  (:require [clojure.string :as str]
            [hiccup2.core :as h]))

(defn brand-mark
  ([] (brand-mark {}))
  ([{:keys [size] :or {size 41}}]
   (let [w (int (* (/ size 41) 37))]
     [:svg {:width w :height size :viewBox "0 0 37 41" :fill "none" :aria-label "fel-mazo"}
      [:path {:d "M25.3002 17.6855H10.7269V24.1986H25.3002V17.6855Z" :fill "#5FA8FF"}]
      [:path {:d "M0.00356838 3.13724V41.0001H8.21709V6.67361H25.2966V0.0500298H3.08722C1.38319 0.0500298 0 1.43321 0 3.13724H0.00356838Z" :fill "currentColor"}]
      [:path {:d "M36.0199 37.8628V0H27.8063V14.4949V17.3076V23.8242V34.3122V34.3229H10.7269V40.9465H32.9362C34.6402 40.9465 36.0234 39.5633 36.0234 37.8593L36.0199 37.8628Z" :fill "currentColor"}]])))

(defn- escape-json-str [s]
  (-> (str s)
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")
      (str/replace "\n" "\\n")
      (str/replace "\r" "\\r")
      (str/replace "\t" "\\t")))

(defn- to-json [v]
  (cond
    (nil? v) "null"
    (boolean? v) (str v)
    (number? v) (str v)
    (string? v) (str "\"" (escape-json-str v) "\"")
    (keyword? v) (str "\"" (escape-json-str (name v)) "\"")
    (sequential? v) (str "[" (str/join "," (map to-json v)) "]")
    (map? v) (str "{" (str/join "," (map (fn [[k val]] (str (to-json k) ":" (to-json val))) v)) "}")
    :else (str "\"" (escape-json-str (str v)) "\"")))

(defn- valid-href? [href]
  (and (string? href)
       (not (str/blank? href))
       (not= "#" href)))


(defn- head-tags [{:keys [page-title description base-path canonical-url og-type image-url robots hreflang json-ld locale]}]
  (list
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:title page-title]
   [:meta {:name "description" :content description}]
   [:link {:rel "icon" :type "image/svg+xml" :href (str base-path "/favicon.svg")}]
   [:link {:rel "canonical" :href canonical-url}]
   (when hreflang
     (list
      [:link {:rel "alternate" :hreflang (:self-lang hreflang) :href (:self-url hreflang)}]
      [:link {:rel "alternate" :hreflang (:alt-lang hreflang) :href (:alt-url hreflang)}]
      [:link {:rel "alternate" :hreflang "x-default" :href (:default-url hreflang)}]))
   [:meta {:property "og:title" :content page-title}]
   [:meta {:property "og:description" :content description}]
   [:meta {:property "og:type" :content og-type}]
   [:meta {:property "og:url" :content canonical-url}]
   [:meta {:property "og:image" :content image-url}]
   [:meta {:name "twitter:card" :content "summary_large_image"}]
   [:meta {:name "twitter:title" :content page-title}]
   [:meta {:name "twitter:description" :content description}]
   [:meta {:name "twitter:image" :content image-url}]
   [:meta {:name "robots" :content robots}]
   (when json-ld
     [:script {:type "application/ld+json"} (h/raw (to-json json-ld))])
   [:link {:rel "alternate" :type "application/rss+xml"
           :title page-title
           :href (str base-path (if (= locale :fr) "/feed.xml" "/en/feed.xml"))}]
   [:link {:rel "preload" :href (str base-path "/fonts/roboto-400.ttf") :as "font" :type "font/ttf" :crossorigin "anonymous"}]
   [:link {:rel "preload" :href (str base-path "/fonts/inter-700.ttf") :as "font" :type "font/ttf" :crossorigin "anonymous"}]
   [:link {:rel "stylesheet" :href (str base-path "/site.css")}]))

(defn layout [{:keys [locale title description site labels navigation body meta page-class header-class footer-class show-footer base-path hreflang json-ld]
               :or {page-class "theme-default"
                    header-class ""
                    footer-class ""
                    show-footer true
                    base-path ""}}]
  (let [home-href (str base-path (if (= locale :fr) "/" "/en/"))
        locale-switch-href (str base-path (if (= locale :fr) "/en/" "/"))
        {:keys [canonical-url og-type image-url robots]
         :or {og-type "website"
              robots "index,follow"}} meta
        page-title (str title " | " (:name site))]
    (str
     "<!DOCTYPE html>"
     (h/html
      [:html {:lang (name locale)}
       [:head (head-tags {:page-title page-title :description description :base-path base-path
                          :canonical-url canonical-url :og-type og-type :image-url image-url
                          :robots robots :hreflang hreflang :json-ld json-ld :locale locale})]
       [:body
        [:a.skip-link {:href "#main-content"} (:skip-link-label labels)]
        [:div {:class (str "page-shell " page-class)}
         [:header {:class (str "site-header " header-class)
                   :id "top"
                   :role "banner"}
          [:div.site-header-inner
           [:a.logo-mark {:href home-href :aria-label (:logo-home-label labels)} (brand-mark {:size 41})]
           [:button.nav-toggle
            {:type "button"
             :aria-label (:nav-toggle-label labels)
             :aria-controls "site-navigation"
             :aria-expanded "false"}
            [:span.nav-toggle-line]
            [:span.nav-toggle-line]
            [:span.nav-toggle-line]]
           [:div.site-header-actions {:id "site-navigation"}
            [:button.nav-close
             {:type "button"
              :aria-label (:nav-close-label labels)}
             (:nav-close-label labels)]
            [:nav.top-nav {:aria-label (:nav-label labels)}
             (for [{:keys [href label]} navigation]
               [:a.nav-link {:href href} label])]
            (when (valid-href? (:cv-link site))
              [:a.cv-button {:href (:cv-link site)} (:cv-label site)])]
           [:button.nav-backdrop
            {:type "button"
             :tabindex "-1"
             :aria-label (:nav-close-label labels)}]]]
         body
         (when show-footer
           [:footer {:class (str "site-footer " footer-class) :id "contact" :role "contentinfo"}
            [:div.site-footer-inner
             [:div.footer-top
              [:p.footer-kicker (:footer-kicker labels)]
              [:p.footer-tagline (:footer-tagline labels)]
              [:a.footer-contact {:href (str "mailto:" (:email site))} (:email site)]
              [:div.footer-socials
               (for [{:keys [href label]} (:socials site)]
                 [:a.social-link {:href href :target "_blank" :rel "noreferrer"} label])]]
             [:div.footer-bottom
              [:div.footer-meta
               [:span (:copyright site)]
               (for [[href label] [[(:privacy-link site) (:privacy-label site)]
                                   [(:terms-link site) (:terms-label site)]
                                   [(:cookies-link site) (:cookies-label site)]]
                     :when (valid-href? href)]
                 [:a {:href href} label])]
              [:button.back-to-top
               {:type "button"
                :data-scroll-top "true"
                :aria-label (:back-to-top labels)}
               (:back-to-top labels)]
              [:a.locale-switch {:href locale-switch-href}
               (if (= locale :fr) "EN" "FR")]]]])]
        [:script {:src (str base-path "/js/main.js") :defer true}]]]))))

(defn- chevron-down []
  [:svg {:width 30 :height 15 :viewBox "0 0 30 15" :fill "none" :aria-hidden "true"}
   [:path {:d "M 1 1 L 15 13 L 29 1" :stroke "currentColor" :stroke-width "2" :fill "none" :stroke-linecap "round" :stroke-linejoin "round"}]])

(defn home-page [{:keys [name role summary about-tag about-title about-heading about-body portrait-url contact-label contact-href scroll-label socials]}]
  [:div.home-page
   [:section.home-hero
    [:div.starfield]
    [:div.star-glow.star-glow-left]
    [:div.star-glow.star-glow-center]
    [:div.home-hero-inner
     [:div.home-center-logo (brand-mark {:size 194})]
     [:h1.home-name name]
     [:p.home-role role]
     [:p.home-summary summary]
     [:button.home-scroll {:type "button"
                           :data-scroll-target "#about"
                           :aria-label scroll-label}
      (chevron-down)]
     [:div.home-about-anchor {:id "about"}]]]
   [:section.home-about
    [:div.starfield.starfield-soft]
    [:div.star-glow.star-glow-bottom]
    [:div.home-about-inner
     [:div.home-portrait
      [:img {:src portrait-url :alt name :loading "lazy"}]]
     [:div.home-about-copy
      [:div.about-tag about-tag]
      [:h2.about-display about-title]
      [:p.about-heading about-heading]
      [:p.about-body about-body]
      [:a.home-contact-button {:href contact-href} contact-label]]]]])

(defn not-found-page [{:keys [eyebrow heading body cta-label cta-href]}]
  [:main {:id "main-content" :class "not-found-page"}
   [:section.not-found-hero
    [:div.starfield]
    [:div.star-glow.star-glow-center]
    [:div.not-found-inner
     [:div.about-tag eyebrow]
     [:h1.not-found-title heading]
     [:p.not-found-body body]
     [:a.home-contact-button {:href cta-href} cta-label]]]])

(defn post-card [{:keys [category title excerpt uri tags post-link-label]}]
  [:article.post-card
   [:div.post-card-content
    [:h3 [:a.post-card-link {:href uri} title]]
    [:p.post-card-excerpt excerpt]
    [:div.post-card-tags
      (for [tag (remove str/blank? (cons category tags))]
        [:span.project-tech tag])]]
   [:a.project-arrow {:href uri :aria-label post-link-label}]])

(defn blog-index-section [{:keys [eyebrow title intro tags-intro tags posts scroll-label blog-list-label post-link-label]}]
  (let [compact? (or (<= (count posts) 1)
                     (<= (count tags) 3))]
    [:main {:id "main-content"
            :class (str "blog-page" (when compact? " blog-page--compact"))}
     [:div.starfield]
     [:div.star-glow.star-glow-left]
     [:div.star-glow.star-glow-center]
     [:section {:class (str "blog-hero" (when compact? " blog-hero--compact"))}
      [:div.blog-hero-inner
       [:span.article-pill eyebrow]
       [:h1.blog-title title]
       [:p.blog-intro intro]
       [:a.blog-scroll-link {:href "#blog-list" :aria-label scroll-label}
        (chevron-down)]]]
     (when (seq tags)
       [:section.blog-tags-section
        [:div.blog-tags-inner
         [:p.blog-tags-intro tags-intro]
         [:div.blog-tags-grid
          (for [tag tags]
            [:span.project-tech tag])]]])
     [:section.blog-list-section {:id "blog-list" :aria-label blog-list-label}
      [:div.blog-list
       (for [post posts]
         (post-card (assoc post :post-link-label post-link-label)))]]]))

(defn- related-posts-card [labels related-posts]
  [:div.article-related-card
   [:h3 (:related-posts-label labels)]
   [:ul
    (for [related related-posts]
      [:li [:a {:href (:uri related)} (:title related)]])]])

(defn article-page [{:keys [post labels related-posts]}]
  (let [has-related-posts? (seq related-posts)]
    [:main.article-page {:id "main-content"}
     [:section.article-hero-section
      [:div.starfield]
      [:div.star-glow.star-glow-center]
      [:div.article-hero-inner
       [:a.article-back {:href (:all-posts labels)} (str "← " (:all-posts-label labels))]
       [:span.article-pill (:eyebrow labels)]
       [:h1.article-title (:title post)]
       [:p.article-excerpt (:excerpt post)]
       [:div.article-meta-row
        [:span (str (:reading-time-label labels) " : " (:reading-time post) " min")]
        [:span (str (:date-label-copy labels) " : " (:date-label post))]]
       [:div.article-tags
        (for [tag (:tags post)]
          [:span.project-tech tag])]]]
     [:section.article-content-section
      [:div.starfield.starfield-soft]
      [:div.article-layout
       [:aside.article-toc
        (map-indexed
         (fn [idx {:keys [title anchor]}]
           [:a.article-toc-item {:href (str "#" anchor) :data-toc-anchor anchor}
            [:span.article-toc-number (format "%02d" (inc idx))]
            [:span.article-toc-title title]])
         (:headings post))]
       [:div.article-main
        [:div.rich-html (h/raw (:html post))]
        (when (or (:project-link labels) has-related-posts?)
          [:div.article-bottom-row
           (when-let [project-link (:project-link labels)]
             [:a.home-contact-button {:href project-link} (:project-label labels)])
           (when has-related-posts?
             (related-posts-card labels related-posts))])]]]]))
