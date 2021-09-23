(ns app.core
  (:require
   [goog.dom :as d]
   [clojure.browser.repl :as repl]
   [goog.events :as events]
   [ajax.core :as ajax]
   [ajax.protocols :as protocol]
   [cljs.core.async :as async :refer [>! <! chan put! take!]]
   [reagent.core :as r]
   [reagent.dom :as rdom]
   )
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defonce app-state (r/atom {
                            :audio-on false
                            :abuff-map {}
                            }))

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


(defn load-sound-path [uri]
  (let [out (chan 1)]
    (ajax/GET uri {:response-format {:type :arraybuffer
                                    :read protocol/-body
                                    :description "audio"
                                    :content-type "audio/wave"
                                    }
                   :handler (fn [resp] (put! out resp))})  ; TODO: understand why we need put! here
    out))


(defn decode-resp-data [array-buff]
  (let [out (chan 1)]
    (.decodeAudioData context array-buff #(put! out %))
    out))

(defn add-buffer-to-state [name buffer]
  (swap! app-state update-in [:abuff-map name] (fn [] buffer)))

(defn load-sound-to-state [name uri]
  (go
    (while true
      (->> uri
           (load-sound-path)
           (<!)
           (decode-resp-data)
           (<!)
           (add-buffer-to-state name)))))

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
  (let [buffer (get-in @app-state [:abuff-map name])
        source (create-audio-source buffer)]
    (.connect source (.-destination context))
    (.start source)))


(defn play-sound-thru-conv [sound-name cnv-name]
  (let [source (create-audio-source (get-in @app-state [:abuff-map sound-name]))
        cnv (create-convolver (get-in @app-state [:abuff-map cnv-name]))]
    (.connect (.connect source cnv) (.-destination context))
    (.start source)))

(defn drag-handler [e]
  ; set source id in event data
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
