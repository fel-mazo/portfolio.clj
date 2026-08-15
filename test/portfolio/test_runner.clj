(ns portfolio.test-runner
  "Discovers and runs every `portfolio.*-test` namespace on the classpath.

   Nothing is listed by hand, so a new test file runs as soon as it exists.
   `-main` optionally takes selectors, each of which is either a namespace
   (`portfolio.site-test`) or a single test (`portfolio.site-test/og-image-is-absolute`,
   or just `og-image-is-absolute`):

     clojure -M:test                             ; everything
     clojure -M:test portfolio.export-test       ; one namespace
     clojure -M:test export-is-idempotent        ; one test"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as test]))

(def ^:private test-file-pattern #"^(.+_test)\.clj$")

(defn- classpath-dirs []
  (->> (str/split (System/getProperty "java.class.path")
                  (re-pattern (java.util.regex.Pattern/quote (System/getProperty "path.separator"))))
       (map io/file)
       (filter #(.isDirectory %))))

(defn- test-namespaces-in [^java.io.File classpath-dir]
  (let [dir (io/file classpath-dir "portfolio")]
    (when (.isDirectory dir)
      (->> (.listFiles dir)
           (keep #(second (re-matches test-file-pattern (.getName %))))
           (map #(symbol (str "portfolio." (str/replace % "_" "-"))))))))

(defn test-namespaces
  "Every portfolio test namespace found on the classpath, sorted."
  []
  (->> (classpath-dirs)
       (mapcat test-namespaces-in)
       distinct
       sort))

(defn- test-vars-in [ns-sym]
  (->> (ns-interns (the-ns ns-sym))
       vals
       (filter #(:test (meta %)))
       (sort-by #(str (:name (meta %))))))

(defn- matches-selector? [selector var]
  (let [{var-ns :ns var-name :name} (meta var)]
    (or (= selector (str var-ns "/" var-name))
        (= selector (str var-name)))))

(defn- select-vars [namespaces selector]
  (if (some #(= selector (str %)) namespaces)
    (test-vars-in (symbol selector))
    (filter #(matches-selector? selector %) (mapcat test-vars-in namespaces))))

(defn- run-ns-vars [[test-ns vars]]
  (binding [test/*report-counters* (ref test/*initial-report-counters*)]
    (test/do-report {:type :begin-test-ns :ns test-ns})
    (test/test-vars vars)
    (test/do-report {:type :end-test-ns :ns test-ns})
    @test/*report-counters*))

(defn run-vars
  "Runs `vars`, reporting per namespace and returning a `run-tests` summary."
  [vars]
  (let [summary (assoc (->> (group-by #(:ns (meta %)) vars)
                            (sort-by #(str (key %)))
                            (map run-ns-vars)
                            (apply merge-with + test/*initial-report-counters*))
                       :type :summary)]
    (test/do-report summary)
    summary))

(defn -main [& selectors]
  (let [namespaces (test-namespaces)
        _ (run! require namespaces)
        vars (if (seq selectors)
               (distinct (mapcat #(select-vars namespaces %) selectors))
               (mapcat test-vars-in namespaces))]
    (when (empty? vars)
      (binding [*out* *err*]
        (println "No tests matched" (pr-str (vec selectors))))
      (System/exit 1))
    (let [{:keys [fail error]} (run-vars vars)]
      (System/exit (if (zero? (+ fail error)) 0 1)))))
