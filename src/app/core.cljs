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
                            :abuff-map {}
                            }))

(defonce resp-chan (chan))
(defonce AudioContext js/window.AudioContext)
(defonce context (AudioContext.))


(def sound-paths [
                   ["cow" "assets/audio/cow.wav"]
                   ["bell" "assets/audio/counter-bell.wav"]
                   ["cat" "assets/audio/cat-meaow.wav"]
                   ["garage" "assets/audio/Parking Garage.wav"]
                   ["space" "assets/audio/On a Star.wav"]
                   ["opera" "assets/audio/opera.wav"]
                   ["underwater" "assets/audio/underwater.wav"]
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

(defn load-sound-asset [uri]
  (let [resp-chan (load-sound-path uri)
        dec-chan (decode-resp-data resp-chan)]
    dec-chan)
  )

(defn load-sound-to-state [name uri]
  (let [in (load-sound-asset uri)]
    (go
      (while true
        (let [buff (<! in)]
          (swap! app-state update :abuff-map #(assoc % name buff))
          )))))

(defn load-ir-to-state [name uri]
  (let [in (load-sound-asset uri)]
    (go
      (while true
        (let [buff (<! in)]
          (swap! app-state update :abuff-map #(assoc % name buff))
          )))))

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
  (let [buffer (get (:abuff-map @app-state) name)
        source (create-audio-source buffer)]
    (.connect source (.-destination context))
    (.start source)))

;; todo make play-sound-thru-conv with buffer args

(defn play-sound-thru-conv [sound-name cnv-name]
  (let [source (create-audio-source (get (:abuff-map @app-state) sound-name))
        cnv (create-convolver (get (:abuff-map @app-state) cnv-name))]
    (.connect (.connect source cnv) (.-destination context))
    (.start source)
    ))


(defn drag-handler [e]
  ; set event data
  (println "dragging " (.. e -target -id) " element")
  (.setData e.dataTransfer "src" (.. e -target -id)))

(defn drop-handler [e]
  (println "dropping " (.. e -target -id) " element")
  (.preventDefault e)
  (let [src (.getData e.dataTransfer "src")
        cnv (.. e -target -id)]
    (play-sound-thru-conv src cnv)))

(defn ui []
  [:div
   [:h1 "Coinvolver"]
   [:h2 "sounds"]
   [:div {:class "sounds"}
    (for [name ["cow" "bell" "cat" "opera"]]
      ^{:key (str "tr-" name)}
      [:button {:on-click #(play-sound-by-name name)
                :draggable true
                :onDragStart drag-handler
                :onDrop drop-handler
                :onDragOver (fn [e] (.preventDefault e))
                :id name
                }
       name])]
   [:h2 "spaces"]
   [:div {:class "irs"}
    (for [name ["space" "garage" "underwater"]]
      ^{:key (str "tr-" name)}
      [:button {:on-click #(play-sound-by-name name)
                :draggable true
                :onDragStart drag-handler
                :onDrop drop-handler
                :onDragOver (fn [e] (.preventDefault e))
                :id name
                }
       name])]
   [:div
    [:h3 "app state"]
    [:p [:code (str @app-state)]]
    ]
   ]
  )


(defn ^:dev/after-load start []
    (println "starting app")
    (doall
        (for [[name path] sound-paths]
            (load-sound-to-state name path)))
    (rdom/render [ui] (d/getElement "ui")))

(defn ^:export main []
    (start))

(defn stop []
  (println "Stopping..."))
(start)
