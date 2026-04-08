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

(defn layout [{:keys [locale title description site navigation body]}]
  (str
   "<!DOCTYPE html>"
   (h/html
    [:html {:lang (name locale)}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title (str title " | " (:name site))]
      [:meta {:name "description" :content description}]
      [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
      [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin "anonymous"}]
      [:link {:rel "stylesheet"
              :href "https://fonts.googleapis.com/css2?family=Inter:opsz,wght@14..32,400;14..32,600&family=Poppins:ital,wght@0,400;0,500;0,600;0,700;1,400&display=swap"}]
      [:link {:rel "stylesheet" :href "/site.css"}]]
     [:body
      [:div.page-shell
       [:header.site-header
        [:div.logo-mark (:logo site)]
        [:nav.top-nav
         (for [{:keys [href label]} navigation]
           [:a.nav-link {:href href} label])]
        [:a.cv-button {:href (:cv-link site)} (:cv-label site)]]
       body
       [:footer.site-footer {:id "contact"}
        [:div.footer-top
         [:a.footer-contact {:href (str "mailto:" (:email site))} (:contact-label site)]
         [:div.footer-socials
          (for [{:keys [href label]} (:socials site)]
            [:a.social-link {:href href :target "_blank" :rel "noreferrer"} label])]]
        [:div.footer-bottom
         [:div.footer-meta
          [:span (:copyright site)]
          [:a {:href (:privacy-link site)} (:privacy-label site)]
          [:a {:href (:terms-link site)} (:terms-label site)]
          [:a {:href (:cookies-link site)} (:cookies-label site)]]
         [:a.locale-switch
          {:href (if (= locale :fr) "/en/" "/")}
          (if (= locale :fr) "EN" "FR")]]]]]])))

(defn hero-section [{:keys [eyebrow name intro cta-label cta-href portrait-url]}]
  [:section.hero-section
   [:div.hero-copy
    [:p.hero-eyebrow eyebrow]
    [:h1.hero-title name]
    [:p.hero-intro intro]
    [:a.primary-pill {:href cta-href} cta-label]]
   [:div.hero-visual
    [:img {:src portrait-url :alt name}]]])

(defn stats-section [{:keys [title body stats languages cta-label cta-href]}]
  [:section.soft-panel {:id "about"}
   [:div.section-heading
    [:h2 title]]
   [:div.about-grid
    [:p.about-copy body]
    [:div.stats-grid
     (for [{:keys [value label]} stats]
       [:div.stat-card
        [:div.stat-value value]
        [:p.stat-label label]])]]
   [:div.languages-block
    [:h3.section-subtitle "Languages"]
    [:div.languages-grid
     (for [language languages]
       [:div.language-chip
        [:span.language-dot]
        [:span language]])]
    [:a.primary-pill {:href cta-href} cta-label]]])

(defn project-card [{:keys [title summary href image alt stack]}]
  [:article.project-card
   [:img.project-image {:src image :alt alt}]
   [:div.project-meta
    [:div.project-stack (str/join " · " stack)]
    [:h3 title]
    [:p summary]
    [:a.text-link {:href href} "View project"]]])

(defn portfolio-section [{:keys [title cta-label cta-href projects]}]
  [:section.portfolio-section {:id "projects"}
   [:div.section-heading
    [:h2 title]]
   [:div.projects-grid
    (for [project projects]
      (project-card project))]
   [:a.outline-pill {:href cta-href} cta-label]])

(defn post-card [{:keys [category title excerpt uri date-label reading-time]}]
  [:article.post-card
   [:div.post-card-meta category]
   [:h3 [:a.post-card-link {:href uri} title]]
   [:p.post-card-excerpt excerpt]
   [:div.post-card-footer
    [:span date-label]
    [:span "•"]
    [:span (str reading-time " min read")]]])

(defn blog-index-section [{:keys [eyebrow title intro posts]}]
  [:main
   [:section.blog-hero
    [:p.hero-eyebrow eyebrow]
    [:h1.blog-title title]
    [:p.blog-intro intro]]
   [:section.blog-list
    (for [post posts]
      (post-card post))]])

(defn article-page [{:keys [post labels]}]
  [:main
   [:section.article-shell
    [:div.article-hero
     [:p.hero-eyebrow (:eyebrow labels)]
     [:h1.article-title (:title post)]
     [:div.article-date (:date-label post)]
     [:p.article-excerpt (:excerpt post)]
    [:div.article-tags
      (for [tag (:tags post)]
        [:span.article-tag tag])]]
    [:article.article-body
     [:div.rich-html (h/raw (:html post))]
     [:div.article-actions
      [:a.outline-pill {:href (:all-posts labels)} (:all-posts-label labels)]]]]])
