(ns portfolio.ui.tags)

(defn setup! []
  (let [buttons (array-seq (.querySelectorAll js/document ".blog-tag-filter"))
        cards   (array-seq (.querySelectorAll js/document ".post-card[data-tags]"))
        active  (atom nil)]
    (when (and (seq buttons) (seq cards))
      (doseq [btn buttons]
        (.addEventListener btn "click"
          (fn []
            (let [tag (.getAttribute btn "data-tag")
                  new-tag (when (not= tag @active) tag)]
              (reset! active new-tag)
              (doseq [b buttons]
                (.toggle (.-classList b) "blog-tag-active" (= new-tag (.getAttribute b "data-tag"))))
              (doseq [card cards]
                (let [card-tags (.getAttribute card "data-tags")
                      visible? (or (nil? new-tag)
                                   (some #(= new-tag %) (.split card-tags ",")))]
                  (.toggle (.-classList card) "blog-tag-hidden" (not visible?)))))))))))
