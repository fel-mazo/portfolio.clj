(ns portfolio.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.resource :refer [wrap-resource]]
            [portfolio.site :as site]
            [portfolio.content :as content])
  (:gen-class))

(defn handler [request]
  (site/page-for-uri (:uri request)))

(def app
  (-> #'handler
      (wrap-resource "public")
      wrap-content-type
      wrap-not-modified))

;; This server only ever runs in development — production is the static export — so every request
;; reloads changed namespaces and re-reads the content from disk.
(def dev-app
  (wrap-reload (fn [request]
                 (content/reset-cache!)
                 (app request))))

(defn- server-port [args]
  (or (some-> (first args) parse-long)
      (some-> (System/getenv "PORT") parse-long)
      3001))

(defn -main [& args]
  (let [port (server-port args)]
    (println (str "Server listening on http://localhost:" port))
    (jetty/run-jetty #'dev-app {:port port :join? true})))
