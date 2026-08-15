(ns portfolio.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.resource :refer [wrap-resource]]
            [portfolio.site :as site])
  (:gen-class))

(defn handler [request]
  (site/page-for-uri (:uri request)))

(def app
  (-> handler
      (wrap-resource "public")
      wrap-content-type
      wrap-not-modified))

(defn- server-port [args]
  (or (some-> (first args) parse-long)
      (some-> (System/getenv "PORT") parse-long)
      3000))

(defn -main [& args]
  (let [port (server-port args)]
    (println (str "Server listening on http://localhost:" port))
    (jetty/run-jetty #'app {:port port :join? true})))
