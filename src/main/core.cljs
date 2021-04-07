(ns core
  (:require
   [goog.dom :as d]
   [goog.events :as events]
   [cljs-bach.synthesis :as bach]
   ))


(defn start-audio [e]
  (println "audio started")
  (defonce AudioContext (or js/window.AudioContext js/window.webkitAudioContext))
  (defonce context (AudioContext.))
  (defonce audio-element (. js/document (querySelector "audio")))
  (defonce cow-track
    (. context (createMediaElementSource audio-element)))
  ((. cow-track connect) (. context destination))
  (.play audio-element)
)


(println "test")

;; (. cow-track (.destination context))

;; (->
;;  (bach/sample "/assets/audio/cow.wav")
;;  (bach/connect-> bach/destination)
;;  (bach/run-with context (bach/current-time context) 1.0))

(defonce start-button (d/getElement "start-button"))
(events/listen
 (d/getElement "start-audio")
 (.-CLICK events/EventType)
 start-audio
 )
