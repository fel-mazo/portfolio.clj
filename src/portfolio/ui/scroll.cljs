(ns portfolio.ui.scroll
  (:require [portfolio.ui.motion :as motion]))

(defn setup! []
  (doseq [btn (array-seq (.querySelectorAll js/document "[data-scroll-target]"))]
    (.addEventListener btn "click"
      (fn []
        (when-let [target (.querySelector js/document (.getAttribute btn "data-scroll-target"))]
          (.scrollIntoView target #js {:behavior (motion/scroll-behavior) :block "start"})))))
  (doseq [btn (array-seq (.querySelectorAll js/document "[data-scroll-top]"))]
    (.addEventListener btn "click"
      (fn [] (.scrollTo js/window #js {:top 0 :behavior (motion/scroll-behavior)})))))
