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

(defn fan-in
  ([ins] (fan-in (chan) ins))
  ([c ins]
    (go (while true
          (let [[x] (alts! ins)]
            (>! c x))))
    c))

(defn my-ints []
  (let [out (chan)]
    (go (loop [i 1]
          (>! out i)
          (recur (inc i))))
    out))

(defn interval [msecs]
  (let [out (chan)]
    (go (while true
          (>! out (js/Date.))
          (<! (timeout msecs))))
    out))

(defn process [name control]
  (let [out  (chan)
        ints (my-ints)
        tick (interval 100)]
    (go
      (<! control)
      (.log js/console "start" name)
      (loop [acc 0]
        (let [[v c] (alts! [tick control])]
          (condp = c
            control (do (.log js/console "pause" name)
                      (<! control)
                      (.log js/console "continue" name)
                      (recur acc))
            (do
              (>! out [name acc])
              (recur (+ acc (<! ints))))))))
    out))

(defn now []
  (js/Date.))

(let [c0    (chan)
      c1    (chan)
      out   (fan-in [(process "p0" c0) (process "p1" c1)])
      keys  (->> (events js/window "keyup")
              (map #(.-keyCode %))
              (filter #{32}))]
  (go
    (>! c0 (now))
    (loop [state 0]
      (recur
        (alt!
          out
          ([v] (do (.log js/console (pr-str v)) state))

          keys
          ([v] (case state
                 0 (do (>! c0 (now)) (>! c1 (now)) 1)
                 1 (do (>! c1 (now)) (>! c0 (now)) 0))))))))
