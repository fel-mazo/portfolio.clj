(ns portfolio.templates
  (:require [clojure.string :as str]
            [hiccup2.core :as h]))

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

(def ^:private behavior-script
  "(function () {
     var header = document.querySelector('.site-header');
     var toggle = document.querySelector('.nav-toggle');
     var closeButton = document.querySelector('.nav-close');
     var navRoot = document.getElementById('site-navigation');
     var backdrop = document.querySelector('.nav-backdrop');
     var setOpen = function (open) {
       if (!toggle || !navRoot) { return; }
       document.body.classList.toggle('nav-open', open);
       toggle.setAttribute('aria-expanded', String(open));
       navRoot.setAttribute('data-open', String(open));
       if (header) { header.setAttribute('data-nav-open', String(open)); }
     };
     if (toggle && navRoot) {
       toggle.addEventListener('click', function () {
         setOpen(toggle.getAttribute('aria-expanded') !== 'true');
       });
       if (closeButton) {
         closeButton.addEventListener('click', function () { setOpen(false); });
       }
       if (backdrop) {
         backdrop.addEventListener('click', function () { setOpen(false); });
       }
       navRoot.querySelectorAll('a').forEach(function (link) {
         link.addEventListener('click', function () { setOpen(false); });
       });
       document.addEventListener('keydown', function (event) {
         if (event.key === 'Escape') {
           setOpen(false);
         }
       });
     }
     document.querySelectorAll('[data-scroll-target]').forEach(function (button) {
       button.addEventListener('click', function () {
         var target = document.querySelector(button.getAttribute('data-scroll-target'));
         if (target) {
           target.scrollIntoView({ behavior: 'smooth', block: 'start' });
         }
       });
     });
     document.querySelectorAll('[data-scroll-top]').forEach(function (button) {
       button.addEventListener('click', function () {
         window.scrollTo({ top: 0, behavior: 'smooth' });
       });
     });
   }());")

(defn layout [{:keys [locale title description site labels navigation body meta page-class header-class footer-class show-footer base-path]
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
       [:head
        [:meta {:charset "utf-8"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
        [:title page-title]
        [:meta {:name "description" :content description}]
        [:link {:rel "icon" :type "image/svg+xml" :href (str base-path "/favicon.svg")}]
        [:link {:rel "canonical" :href canonical-url}]
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
        [:link {:rel "preload" :href (str base-path "/fonts/roboto-400.ttf") :as "font" :type "font/ttf" :crossorigin "anonymous"}]
        [:link {:rel "preload" :href (str base-path "/fonts/inter-700.ttf") :as "font" :type "font/ttf" :crossorigin "anonymous"}]
        [:link {:rel "stylesheet" :href (str base-path "/site.css")}]]
       [:body
        [:a.skip-link {:href "#main-content"} (:skip-link-label labels)]
        [:div {:class (str "page-shell " page-class)}
         [:header {:class (str "site-header " header-class)
                   :id "top"
                   :role "banner"}
          [:div.site-header-inner
           [:a.logo-mark {:href home-href :aria-label (:logo-home-label labels)} (brand-mark)]
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
        [:script (h/raw behavior-script)]]]))))

(defn home-page [{:keys [name role summary about-tag about-title about-heading about-body portrait-url contact-label contact-href scroll-label socials]}]
  [:div.home-page
   [:section.home-hero
    [:div.starfield]
    [:div.star-glow.star-glow-left]
    [:div.star-glow.star-glow-center]
    [:div.home-hero-inner
     [:div.home-center-logo (brand-mark)]
     [:h1.home-name name]
     [:p.home-role role]
     [:p.home-summary summary]
     [:div.hero-socials
      (for [{:keys [href label]} socials]
        [:a.hero-social-link {:href href :target "_blank" :rel "noreferrer"} label])]
     [:button.home-scroll {:type "button"
                           :data-scroll-target "#about"
                           :aria-label scroll-label}]
     [:div.home-about-anchor {:id "about"}]]]
   [:section.home-about
    [:div.starfield.starfield-soft]
    [:div.star-glow.star-glow-bottom]
    [:div.home-about-inner
     [:div.home-portrait
     [:img {:src portrait-url :alt name}]]
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

(defn project-card [{:keys [title summary href image alt stack home? project-link-label]}]
  [:article {:class (str "project-card" (when home? " project-card--home"))}
   (when-not home?
     [:img.project-image {:src image :alt alt}])
   [:div.project-meta
    (when-not home?
      [:div.project-stack (str/join " · " stack)])
    [:h3 title]
    [:p summary]
    (when home?
      [:div.project-card-home-footer
       [:div.project-tech-list
        (for [tech stack]
          [:span.project-tech tech])]])
    (when (and (not home?) href)
      [:a.text-link {:href href} project-link-label])]])

(defn portfolio-section [{:keys [title heading cta-label cta-href project-link-label projects home?]}]
  [:section {:class (str "portfolio-section" (when home? " portfolio-section--home")) :id "projects"}
   (if home?
     [:div.home-projects-layout
      [:div.home-projects-copy
       [:div.about-tag title]
       [:h2.home-projects-title heading]]
      [:div.projects-grid
       (for [project projects]
         (project-card (assoc project :home? true :project-link-label project-link-label)))]]
     [:<>
      [:div.section-heading
       [:h2 title]]
      [:div.projects-grid
       (for [project projects]
         (project-card (assoc project :project-link-label project-link-label)))]
      [:a.outline-pill {:href cta-href} cta-label]])])

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
     [:section {:class (str "blog-hero" (when compact? " blog-hero--compact"))}
    [:div.starfield]
    [:div.star-glow.star-glow-left]
    [:div.star-glow.star-glow-center]
    [:div.blog-hero-inner
     [:div.about-tag eyebrow]
     [:h1.blog-title title]
     [:p.blog-intro intro]
     [:button.home-scroll {:type "button"
                           :data-scroll-target "#blog-list"
                           :aria-label scroll-label}]
     [:a.blog-list-jump {:href "#blog-list" :aria-label blog-list-label} blog-list-label]
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
      [:div {:class (str "article-layout" (when-not has-related-posts? " article-layout--solo"))}
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
        (when has-related-posts?
          [:div.article-related-mobile
           (related-posts-card labels related-posts)])]
       (when has-related-posts?
         [:aside.article-related
          (related-posts-card labels related-posts)])]]]))
