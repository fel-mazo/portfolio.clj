(ns portfolio.ui.main
  (:require [portfolio.ui.nav :as nav]
            [portfolio.ui.scroll :as scroll]
            [portfolio.ui.toc :as toc]
            [portfolio.ui.logo-morph :as logo-morph]))

(defn init! []
  (nav/setup!)
  (scroll/setup!)
  (toc/setup!)
  (logo-morph/setup!))
