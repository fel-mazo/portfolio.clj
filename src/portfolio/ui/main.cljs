(ns portfolio.ui.main
  (:require [portfolio.ui.nav :as nav]
            [portfolio.ui.scroll :as scroll]
            [portfolio.ui.toc :as toc]
            [portfolio.ui.logo-morph :as logo-morph]
            [portfolio.ui.tags :as tags]
            [portfolio.ui.highlight :as highlight]))

(defn init! []
  (nav/setup!)
  (scroll/setup!)
  (toc/setup!)
  (logo-morph/setup!)
  (tags/setup!)
  (highlight/setup!))
