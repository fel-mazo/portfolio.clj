(ns portfolio.ui.nav)

(defn- set-open! [toggle nav-root header open?]
  (when (and toggle nav-root)
    (.. js/document -body -classList (toggle "nav-open" open?))
    (.setAttribute toggle "aria-expanded" (str open?))
    (.setAttribute nav-root "data-open" (str open?))
    (when header
      (.setAttribute header "data-nav-open" (str open?)))))

(defn setup! []
  (let [header   (.querySelector js/document ".site-header")
        toggle   (.querySelector js/document ".nav-toggle")
        close-btn (.querySelector js/document ".nav-close")
        nav-root (.getElementById js/document "site-navigation")
        backdrop (.querySelector js/document ".nav-backdrop")]
    (when (and toggle nav-root)
      (.addEventListener toggle "click"
        (fn [] (set-open! toggle nav-root header
                 (not= "true" (.getAttribute toggle "aria-expanded")))))
      (when close-btn
        (.addEventListener close-btn "click"
          (fn [] (set-open! toggle nav-root header false))))
      (when backdrop
        (.addEventListener backdrop "click"
          (fn [] (set-open! toggle nav-root header false))))
      (doseq [link (array-seq (.querySelectorAll nav-root "a"))]
        (.addEventListener link "click"
          (fn [] (set-open! toggle nav-root header false))))
      (.addEventListener js/document "keydown"
        (fn [e] (when (= "Escape" (.-key e))
                  (set-open! toggle nav-root header false)))))))
