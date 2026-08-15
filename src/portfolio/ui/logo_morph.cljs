(ns portfolio.ui.logo-morph
  (:require [portfolio.ui.motion :as motion]))

(defn- ease-in-out-cubic [p]
  (if (< p 0.5)
    (* 4 p p p)
    (- 1 (/ (.pow js/Math (+ (* -2 p) 2) 3) 2))))

(defonce ^:private state (atom nil))
(defonce ^:private preference-watch (atom nil))

(defn- teardown! []
  (when-let [{:keys [floater hero-logo nav-logo scroll-handler resize-handler]} @state]
    (when floater (.remove floater))
    (when hero-logo (set! (.. hero-logo -style -visibility) ""))
    (when nav-logo
      (set! (.. nav-logo -style -opacity) "")
      (set! (.. nav-logo -style -pointerEvents) ""))
    (when scroll-handler
      (.removeEventListener js/window "scroll" scroll-handler))
    (when resize-handler
      (.removeEventListener js/window "resize" resize-handler))
    (reset! state nil)))

(declare setup!)

(defn- watch-preference! []
  (when-not @preference-watch
    (reset! preference-watch
      (motion/on-change!
        (fn [reduced?] (if reduced? (teardown!) (setup!)))))))

(defn setup! []
  (teardown!)
  (watch-preference!)
  (when-not (motion/reduced-motion?)
    (when (.querySelector js/document ".home-page")
      (let [hero-logo (.querySelector js/document ".home-center-logo")
            nav-logo  (.querySelector js/document ".logo-mark")]
        (when (and hero-logo nav-logo)
          (.scrollTo js/window 0 0)
          (let [hero-box   (.getBoundingClientRect hero-logo)
                nav-box    (.getBoundingClientRect nav-logo)
                svg        (.cloneNode (.querySelector hero-logo "svg") true)
                _          (set! (.. svg -style -width) "100%")
                _          (set! (.. svg -style -height) "100%")
                floater    (.createElement js/document "div")
                _          (set! (.-cssText (.-style floater))
                             (str "position:fixed;z-index:90;pointer-events:none;color:#fff;"
                                  "width:" (.-width hero-box) "px;height:" (.-height hero-box) "px;"
                                  "left:" (.-left hero-box) "px;top:" (.-top hero-box) "px;"
                                  "transform-origin:center;will-change:transform,opacity;"
                                  "transition:opacity 0.15s;"))
                _          (.appendChild floater svg)
                _          (.setAttribute floater "data-logo-floater" "")
                _          (.appendChild (.-body js/document) floater)
                _          (set! (.. hero-logo -style -visibility) "hidden")
                _          (set! (.. nav-logo -style -opacity) "0")
                hero-cx    (+ (.-left hero-box) (/ (.-width hero-box) 2))
                hero-cy    (+ (.-top hero-box) (/ (.-height hero-box) 2))
                nav-cx     (+ (.-left nav-box) (/ (.-width nav-box) 2))
                nav-cy     (+ (.-top nav-box) (/ (.-height nav-box) 2))
                dx         (- nav-cx hero-cx)
                dy         (- nav-cy hero-cy)
                target-s   (/ (.-width nav-box) (.-width hero-box))
                scroll-r   (.max js/Math (- hero-cy nav-cy) 250)
                ticking    (volatile! false)
                update!    (fn []
                             (let [p (-> (/ (.-scrollY js/window) scroll-r) (max 0) (min 1))
                                   e (ease-in-out-cubic p)
                                   tx (* dx e)
                                   ty (+ (* (- (.-scrollY js/window)) (- 1 e)) (* dy e))
                                   s  (+ 1 (* (- target-s 1) e))]
                               (set! (.. floater -style -transform)
                                 (str "translate(" tx "px," ty "px) scale(" s ")"))
                               (if (>= p 0.95)
                                 (do (set! (.. floater -style -opacity) "0")
                                     (set! (.. nav-logo -style -opacity) "")
                                     (set! (.. nav-logo -style -pointerEvents) ""))
                                 (do (set! (.. floater -style -opacity) "1")
                                     (set! (.. nav-logo -style -opacity) "0")
                                     (set! (.. nav-logo -style -pointerEvents) "none")))))
                scroll-handler (fn []
                                 (when-not @ticking
                                   (js/requestAnimationFrame
                                     (fn [] (update!) (vreset! ticking false)))
                                   (vreset! ticking true)))
                resize-handler (fn [] (teardown!))]
            (.addEventListener js/window "scroll" scroll-handler #js {:passive true})
            (update!)
            (reset! state {:floater floater
                           :hero-logo hero-logo
                           :nav-logo nav-logo
                           :scroll-handler scroll-handler
                           :resize-handler resize-handler})
            (.addEventListener js/window "resize" resize-handler #js {:once true})))))))
