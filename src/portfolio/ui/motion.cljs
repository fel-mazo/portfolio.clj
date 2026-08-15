(ns portfolio.ui.motion)

(def ^:private reduce-query "(prefers-reduced-motion: reduce)")

(defn- media-query []
  (when (.-matchMedia js/window)
    (.matchMedia js/window reduce-query)))

(defn reduced-motion?
  "True when the user asked the system to reduce motion."
  []
  (boolean (some-> (media-query) (.-matches))))

(defn scroll-behavior
  "Scroll behavior honouring the reduced-motion preference."
  []
  (if (reduced-motion?) "auto" "smooth"))

(defn on-change!
  "Calls f with the new preference (boolean) whenever it flips.
   Returns the media query list, or nil when matchMedia is unavailable."
  [f]
  (when-let [mql (media-query)]
    (.addEventListener mql "change" (fn [e] (f (.-matches e))))
    mql))
