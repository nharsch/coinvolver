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
                            :dragged-id nil
                            }))

(defonce AudioContext js/window.AudioContext)
(defonce context (AudioContext.))


(def sound-paths [
                   ["🐄" "assets/audio/cow.wav"]
                   ["🔔" "assets/audio/counter-bell.wav"]
                   ["🐈" "assets/audio/cat-meaow.wav"]
                   ["🏛️" "assets/audio/Parking Garage.wav"]
                   ["👨‍🎤" "assets/audio/opera.wav"]
                   ["🌩️" "assets/audio/thunder-clap.wav"]
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
        cnv))

(defn create-comp []
  (new js/DynamicsCompressorNode context (js-obj "attack" 1 "ratio" 20 "knee" 6 "threslhold" -35)))

(defn create-gain [gain]
  (new js/GainNode context (js-obj "gain" gain)))

;; (.-value (.-ratio (create-comp)))

(defn play-sound-by-name [name]
  (let [buffer (get-in @app-state [:abuff-map name])
        source (create-audio-source buffer)]
    (.connect source (.-destination context))
    (.start source)))


(defn play-sound-thru-conv [sound-name cnv-name]
  (let [state  @app-state
        source (create-audio-source (get-in state [:abuff-map sound-name]))
        cnv    (create-convolver (get-in state [:abuff-map cnv-name]))
        comp   (create-comp)
        gain   (create-gain 5)
        ]
    (-> source
        (.connect cnv)
        (.connect comp)
        (.connect gain)
        (.connect (.-destination  context)))
    (.start source)))


(defn drag-start-handler [e]
  (.setData e.dataTransfer "src" (.. e -target -id)))


(defn drop-handler [e]
  (.preventDefault e)
  (let [src (.getData e.dataTransfer "src")
         cnv (.. e -target -id)]
     (play-sound-thru-conv src cnv)))


(defn sound-component [name]
  ^{:key (str "tr-" name)}
  [:div {:on-click    #(play-sound-by-name name)
         :class       "coin"
         :draggable   true
         :onDragStart drag-start-handler
         :onDragOver  (fn [e] (.preventDefault e))
         :id          name
         }
   [:div {:class "text"} (subs name 0 9)]])

(defn ir-component [name]
  ^{:key (str "tr-" name)}
  [:div {:class       "slot"
         :onDragOver  (fn [e] (.preventDefault e))
         :onDrop      drop-handler
         :id          name}
   [:div {:class "text"} (subs name 0 9)]])

(defn ui []
  [:div
   [:h1 "Coinvolver"]
   [:h2 "Click coins to play sounds, drag into wells to mix sounds"]
   [:div {:class "sounds"}
    (for [name (map first sound-paths)]
      (sound-component name))]
   [:div {:class "irs"}
    (for [name (map first sound-paths)]
      (ir-component name))]
   ;; [:div
   ;;  [:h3 "app state"]
   ;;  [:p [:code (str @app-state)]]
   ;;  ]
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
