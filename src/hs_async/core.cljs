(ns hs-async.core
  (:refer-clojure :exclude [map filter distinct])
  (:require [cljs.core.async :refer [>! <! chan put! take! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go alt!]]))

;; ======================================================================
;; Basics

#_(.log js/console (go 5))

#_(.log js/console (<! (go 5)))

#_(go (.log js/console (<! (go 5))))

#_(let [c (chan)]
  (go
    (.log js/console "We got here")
    (<! c)
    (.log js/console "We'll never get here")))

#_(let [c (chan)]
  (go
    (.log js/console "We got here")
    (<! c)
    (.log js/console "We made progress"))
  (go
    (>! c (js/Date.))))

#_(let [c (chan)]
  (go
    (>! c (js/Date.)))
  (go
    (.log js/console "Order")
    (<! c)
    (.log js/console "doesn't matter")))

;; =============================================================================
;; Rx

(defn events [el type]
  (let [out (chan)]
    (.addEventListener el type
      (fn [e] (put! out e)))
    out))

#_(let [move (events js/window "mousemove")]
  (go (loop []
        (.log js/console (<! move))
        (recur))))

(defn map [f in]
  (let [out (chan)]
    (go (while true
          (>! out (f (<! in)))))
    out))

(defn e->v [e]
  [(.-pageX e) (.-pageY e)])

#_(let [move (map e->v (events js/window "mousemove"))]
  (go (while true
        (.log js/console (pr-str (<! move))))))

(defn filter [pred in]
  (let [out (chan)]
    (go (while true
          (let [x (<! in)]
            (when (pred x)
              (>! out x)))))
    out))

(defn x-mod-5-y-mod-10 [[x y]]
  (and (zero? (mod x 5))
       (zero? (mod y 10))))

#_(let [filtered (filter x-mod-2-y-mod-3
                 (map e->v
                   (events js/window "mousemove")))]
  (go (while true
        (.log js/console (pr-str (<! filtered))))))

;; =============================================================================
;; Timeouts

#_(go
  (.log js/console "Hello")
  (<! (timeout 1000))
  (.log js/console "async")
  (<! (timeout 1000))
  (.log js/console "world!"))

;; =============================================================================
;; Non-deterministic choice

#_(let [c (chan)
      t (timeout 1000)]
  (go
    (let [[v sc] (alts! [c t])]
      (.log js/console "Timeout channel closed!" (= sc t)))))

#_(let [c (chan)
      t (timeout 1000)]
  (go
    (alt!
      c ([v] (.log js/console "Channel responded"))
      t ([v] (.log js/console "Timeout channel closed!")))))

;; =============================================================================
;; State

(defn distinct [in]
  (let [out (chan)]
    (go (loop [last nil]
          (let [x (<! in)]
            (when (not= x last)
              (>! out x))
            (recur x))))
    out))

#_(let [keys (distinct
             (map #(.-keyCode %)
               (events js/window "keypress")))]
  (go (while true
        (.log js/console (<! keys)))))

;; =============================================================================
;; More coordination
