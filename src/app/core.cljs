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
                            :sounds {}
                            :irs {}
                            }))

(defonce resp-chan (chan))
(defonce AudioContext js/window.AudioContext)
(defonce context (AudioContext.))


(def play-sound-paths [
                        ["cow" "assets/audio/cow.wav"]
                        ["bell" "assets/audio/counter-bell.wav"]
                        ["cat" "assets/audio/cat-meaow.wav"]
                        ["lodge" "assets/audio/Masonic Lodge.wav"]
                      ])

;; TODO: load in
(def ir-sound-paths [
                     ["lodge" "assets/audio/Masonic Lodge.wav"]
                     ])


(defn start-audio [e]
  (println "audio started")
   (swap! app-state update :audio-on (fn [] true)))


(defn load-sound-path [uri]
  (let [out (chan 1)]
    (ajax/GET uri {:response-format {:type :arraybuffer
                                    :read protocol/-body
                                    :description "audio"
                                    :content-type "audio/wave"
                                    }
                   :handler (fn [resp] (put! out resp))})
    out))

(defn decode-resp-data [resp-chan]
  (let [out (chan 1)]
    (go (while true
            (.decodeAudioData context (<! resp-chan) (fn [buff] (put! out buff)))))
    out))

(defn load-sound [name uri]
  (let [resp-chan (load-sound-path uri)
        dec-chan (decode-resp-data resp-chan)]
    (go
      (while true
        (let [buff (<! dec-chan)]
          (swap! app-state update :sounds #(assoc % name buff)))))))
;; (load-sound "cow" "assets/audio/cow.wav")


(defn create-audio-source [abuff]
 (let [source (.createBufferSource context)]
    (set! (.-buffer source) abuff)
    source))

(defn create-convolver [abuff]
    (let [cnv (.createConvolver context)]
        (set! (.-buffer cnv) abuff)
        (set! (.-normalize cnv) true)
        cnv))

(defn play-sound-by-name [name]
  (let [buffer (get (:sounds @app-state) name)
        source (create-audio-source buffer)]
    (.connect source (.-destination context))
    (.start source)))


(defn play-sound-thru-conv [sound-name cnv-name]
  (let [source (create-audio-source (get (:sounds @app-state) sound-name))
        cnv (create-convolver (get (:sounds @app-state) cnv-name))]
    (.connect (.connect source cnv) (.-destination context))
    (.start source)
    ))

;; (play-sound-thru-conv "cat" "lodge")

(defn ui []
  [:div
   [:h1 "Coinvolver"]
   [:button {:on-click start-audio} "Start Audio"]
   [:div {:class "sounds"}
    (for [[name buff] (:sounds @app-state)]
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
