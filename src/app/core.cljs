(ns app.core
  (:require
   [goog.dom :as d]
   [goog.events :as events]
   [ajax.core :as ajax]
   [ajax.protocols :as protocol]
   [cljs.core.async :as async :refer [>! <! chan put!]]
   [reagent.core :as r]
   [reagent.dom :as rdom]
   )
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defonce app-state (r/atom {
                       :audio-on false
                       :buffer-map {}
                       }))

(defonce resp-chan (chan))
(defonce AudioContext js/window.AudioContext)
(defonce context (AudioContext.))


(defonce play-sound-paths [
                           ["cow" "assets/audio/cow.wav"]
                           ["bell" "assets/audio/counter-bell.wav"]
                           ["cat" "assets/audio/cat-meaow.wav"]
                           ])

(defn start-audio [e]
  (println "audio started")
   (swap! app-state update :audio-on (fn [] true)))


;; TODO: use another chan instead of callback
(defn decode-resp-data [[name resp]]
  (.decodeAudioData context resp (fn [buff]
                                   (swap! app-state update :buffer-map #(assoc % name buff))))
  )

(defn load-sound [name uri]
  (ajax/GET uri {:response-format {:type :arraybuffer
                                   :read protocol/-body
                                   :description "audio"
                                   :content-type "audio/wave"
                                   }
                 :handler (fn [resp] (put! resp-chan [name resp]))}))


(go-loop []
  (decode-resp-data (<! resp-chan))
  (recur))

(defn load-sounds []
  (for [[name path] play-sound-paths]
    (do
        (println "loading " name)
        (load-sound name path))))


(defn play-audio-buffer [abuff]
  (let [source (.createBufferSource context)]
    (set! (.-buffer source) abuff)
    (.connect source (.-destination context))
    (.start source)))

(defn play-sound-by-name [name]
  (let [buffer (get (:buffer-map @app-state) name)]
    (play-audio-buffer buffer)))


(defn ui []
  [:div
   [:h1 "Coinvolver"]
   [:button {:on-click start-audio} "Start Audio"]
   [:div {:class "sounds"}
    (for [[name buff] (:buffer-map @app-state)]
      ^{:key (str "tr-" name)}
      [:button {:on-click #(play-sound-by-name name)} name]
      )]
   [:div
    [:h3 "app state"]
    [:p [:code (str @app-state)]]
    ]
   ]
  )


(defn ^:dev/after-load start []
    (println "starting app")
    (doall
        (for [[name path] play-sound-paths]
            (load-sound name path)))
    (rdom/render [ui] (d/getElement "ui")))

(defn ^:export main []
    (start))

(defn stop []
  (println "Stopping..."))
