(ns core
  (:require
   [goog.dom :as d]
   [goog.events :as events]
   [cljs-bach.synthesis :as bach]
   ))


(defn start-audio [e]
  (println "audio started")
  (defonce AudioContext js/window.AudioContext)
  (defonce context (AudioContext.))
  (defonce audio-element (. js/document (querySelector "audio")))
  (defonce cow-track
    (. context createMediaElementSource audio-element))
  (defonce gain (.createGain context))
  (set! (.-value gain) 2)
  (.connect (.connect cow-track gain) (. context -destination))
  (.play audio-element)
  )
(defonce start-button (d/getElement "start-button"))
(events/listen
 (d/getElement "start-audio")
 (.-CLICK events/EventType)
 start-audio
 )
