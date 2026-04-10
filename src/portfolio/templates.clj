(ns portfolio.templates
  (:require [clojure.string :as str]
            [hiccup2.core :as h]))

(def design-tokens
  {:colors {:navy "#0b182e"
            :blue "#1251b5"
            :sky "#5fa8ff"
            :coral "#f95959"
            :black "#000000"
            :white "#ffffff"
            :mist "#f9f7f7"
            :line "#d8d8d8"}
   :fonts {:display "\"Poppins\", \"Roboto\", sans-serif"
           :body "\"Poppins\", \"Roboto\", sans-serif"
           :quote "\"Inter\", sans-serif"}})

(defn brand-mark []
  [:div.brand-mark
   [:div.brand-stem]
   [:div.brand-top]
   [:div.brand-bottom]
   [:div.brand-accent]])

(defn- valid-href? [href]
  (and (string? href)
       (not (str/blank? href))
       (not= "#" href)))

(defn layout [{:keys [locale title description site navigation body page-class header-class footer-class show-footer]
               :or {page-class "theme-default"
                    header-class ""
                    footer-class ""
                    show-footer true}}]
  (let [home-href (if (= locale :fr) "/" "/en/")
        locale-switch-href (if (= locale :fr) "/en/" "/")
        footer-node
        (when show-footer
          [:footer {:class (str "site-footer " footer-class) :id "contact"}
           [:div.site-footer-inner
            [:div.footer-top
             [:a.footer-contact {:href (str "mailto:" (:email site))} (:contact-label site)]
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
             [:a.locale-switch {:href locale-switch-href}
              (if (= locale :fr) "EN" "FR")]]]])
        page
        [:html {:lang (name locale)}
         [:head
          [:meta {:charset "utf-8"}]
          [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
          [:title (str title " | " (:name site))]
          [:meta {:name "description" :content description}]
          [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
          [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin "anonymous"}]
          [:link {:rel "stylesheet"
                  :href "https://fonts.googleapis.com/css2?family=Roboto:ital,wght@0,400;0,500;0,600;0,700;1,400&family=Inter:ital,wght@1,400&display=swap"}]
          [:link {:rel "stylesheet" :href "/site.css"}]]
         [:body
          [:div {:class (str "page-shell " page-class)}
           [:header {:class (str "site-header " header-class)}
            [:div.site-header-inner
             [:a.logo-mark {:href home-href} (brand-mark)]
             [:nav.top-nav
              (for [{:keys [href label]} navigation]
                [:a.nav-link {:href href} label])]
             (when (valid-href? (:cv-link site))
               [:a.cv-button {:href (:cv-link site)} (:cv-label site)])]]
           body
           footer-node]]]]
    (str "<!DOCTYPE html>" (h/html page))))

(defn home-page [{:keys [name role about-tag about-title about-heading about-body portrait-url contact-label contact-href]}]
  [:div.home-page
   [:section.home-hero
    [:div.starfield]
    [:div.star-glow.star-glow-left]
    [:div.star-glow.star-glow-center]
    [:div.home-hero-inner
     [:div.home-center-logo (brand-mark)]
     [:h1.home-name name]
     [:p.home-role role]
     [:a.home-scroll {:href "#about" :aria-label "Scroll to about"}]
     [:div.home-about-anchor {:id "about"}]]]
   [:section.home-about
    [:div.starfield.starfield-soft]
    [:div.star-glow.star-glow-bottom]
    [:div.home-about-inner
     [:div.home-portrait
      [:img {:src portrait-url :alt name}]]
     [:div.home-about-copy
      [:div.about-tag about-tag]
      [:h2.about-display
       "WHO"
       [:span.about-display-accent " I "]
       "AM"]
      [:p.about-heading about-heading]
      [:p.about-body about-body]
      [:a.home-contact-button {:href contact-href} contact-label]]]]])

(defn project-card [{:keys [title summary href image alt stack home?]}]
  [:article {:class (str "project-card" (when home? " project-card--home"))}
   (when-not home?
     [:img.project-image {:src image :alt alt}])
   [:div.project-meta
    (when-not home?
      [:div.project-stack (str/join " · " stack)])
    [:h3 title]
    [:p summary]
    (if home?
      [:div.project-card-home-footer
       [:div.project-tech-list
        (for [tech stack]
          [:span.project-tech tech])]
       [:a.project-arrow {:href href :aria-label (str "Open " title)}]]
      [:a.text-link {:href href} "View project"])]])

(defn portfolio-section [{:keys [title cta-label cta-href projects home?]}]
  [:section {:class (str "portfolio-section" (when home? " portfolio-section--home")) :id "projects"}
   (if home?
     [:div.home-projects-layout
      [:div.home-projects-copy
       [:div.about-tag "PROJECTS"]
       [:h2.home-projects-title
        "THINGS"
        [:br]
        [:span.about-display-accent " I "]
        "DO"]]
      [:div.projects-grid
       (for [project projects]
         (project-card (assoc project :home? true)))]]
     [:<>
      [:div.section-heading
       [:h2 title]]
      [:div.projects-grid
       (for [project projects]
         (project-card project))]
      [:a.outline-pill {:href cta-href} cta-label]])])

(defn post-card [{:keys [category title excerpt uri tags]}]
  [:article.post-card
   [:div.post-card-content
    [:h3 [:a.post-card-link {:href uri} title]]
    [:p.post-card-excerpt excerpt]
    [:div.post-card-tags
     (for [tag (remove str/blank? (cons category tags))]
       [:span.project-tech tag])]]
   [:a.project-arrow {:href uri :aria-label (str "Open " title)}]])

(defn blog-index-section [{:keys [eyebrow title intro tags-intro tags posts]}]
  [:main.blog-page
   [:section.blog-hero
    [:div.starfield]
    [:div.star-glow.star-glow-left]
    [:div.star-glow.star-glow-center]
    [:div.blog-hero-inner
     [:div.about-tag eyebrow]
     [:h1.blog-title title]
     [:p.blog-intro intro]
     [:a.home-scroll {:href "#blog-list" :aria-label "Scroll to blog list"}]
     (when (seq tags)
       [:div.blog-tags-block
        [:p.blog-tags-intro tags-intro]
        [:div.blog-tags-grid
         (for [tag tags]
           [:span.project-tech tag])]])]]
   [:section.blog-list-section {:id "blog-list"}
    [:div.starfield.starfield-soft]
    [:div.blog-list
     (for [post posts]
       (post-card post))]]])

(defn article-page [{:keys [post labels related-posts]}]
  [:main.article-page
   [:section.article-hero-section
    [:div.starfield]
    [:div.star-glow.star-glow-center]
    [:div.article-hero-inner
     [:div.about-tag (:eyebrow labels)]
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
      [:div.article-toc-line]
      (map-indexed
      (fn [idx {:keys [title anchor]}]
         [:div.article-toc-item
          [:span.article-toc-number (format "%02d" (inc idx))]
          [:a.article-toc-title {:href (str "#" anchor)} title]])
       (:headings post))]
     [:div.article-main
      [:div.rich-html (h/raw (:html post))]
      (when-let [project-link (:project-link labels)]
        [:div.article-actions-row
         [:a.home-contact-button {:href project-link} (:project-label labels)]])
      (when (seq related-posts)
        [:div.article-related-mobile
         [:div.article-related-card
          [:h3 (:related-posts-label labels)]
          [:ul
           (for [related related-posts]
             [:li [:a {:href (:uri related)} (:title related)]])]]])]
     [:aside.article-related
      (when (seq related-posts)
        [:div.article-related-card
         [:h3 (:related-posts-label labels)]
         [:ul
          (for [related related-posts]
            [:li [:a {:href (:uri related)} (:title related)]])]])]]]])
