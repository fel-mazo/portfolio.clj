(ns portfolio.ui.toc)

(defn setup! []
  (let [toc-items (array-seq (.querySelectorAll js/document "[data-toc-anchor]"))]
    (when (seq toc-items)
      (let [headings (->> toc-items
                          (keep (fn [item]
                                  (let [id (.getAttribute item "data-toc-anchor")]
                                    (when-let [el (.getElementById js/document id)]
                                      {:el el :toc-item item}))))
                          vec)
            ticking  (volatile! false)
            update!  (fn []
                       (let [active (reduce-kv
                                      (fn [acc i {:keys [el]}]
                                        (if (<= (.-top (.getBoundingClientRect el)) 140) i acc))
                                      nil headings)]
                         (doseq [item toc-items]
                           (.remove (.-classList item) "active"))
                         (when active
                           (.add (.-classList (:toc-item (headings active))) "active"))))]
        (.addEventListener js/window "scroll"
          (fn []
            (when-not @ticking
              (vreset! ticking true)
              (js/requestAnimationFrame
                (fn [] (update!) (vreset! ticking false)))))
          #js {:passive true})
        (update!)))))
