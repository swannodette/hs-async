(ns hs-async.core
  (:refer-clojure :exclude [map filter distinct remove])
  (:require [cljs.core.async :refer [>! <! chan put! take! timeout close!]]
            [goog.dom :as dom])
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
  (go (while true
        (.log js/console (<! move)))))

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

#_(let [filtered (filter x-mod-5-y-mod-10
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

(defn fan-in [ins]
  (let [out (chan)]
    (go (while true
          (let [[x] (alts! ins)]
            (>! out x))))
    out))

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
        tick (interval 1000)]
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

(defn now [] (js/Date.))

#_(let [c0   (chan)
      c1   (chan)
      out  (fan-in [(process "p0" c0) (process "p1" c1)])
      keys (->> (events js/window "keyup")
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

;; =============================================================================
;; Escaping Callback hell

(defn fake-search [kind]
  (fn [c query]
    (go
     (<! (timeout (rand-int 100)))
     (>! c [kind query]))))

(def web1 (fake-search :web1))
(def web2 (fake-search :web2))
(def image1 (fake-search :image1))
(def image2 (fake-search :image2))
(def video1 (fake-search :video1))
(def video2 (fake-search :video2))

(defn fastest [query & replicas]
  (let [c (chan)]
    (doseq [replica replicas]
      (replica c query))
    c))

(defn google [query]
  (let [c (chan)
        t (timeout 80)]
    (go (>! c (<! (fastest query web1 web2))))
    (go (>! c (<! (fastest query image1 image2))))
    (go (>! c (<! (fastest query video1 video2))))
    (go (loop [i 0 ret []]
          (if (= i 3)
            ret
            (recur (inc i) (conj ret (alt! [c t] ([v] v)))))))))

#_(go (.log js/console (pr-str (<! (google "clojure")))))

;; =============================================================================
;; Getting fancier

;; -----------------------------------------------------------------------------
;; Declarations

(def ENTER 13)
(def UP_ARROW 38)
(def DOWN_ARROW 40)
(def SELECTOR_KEYS #{ENTER UP_ARROW DOWN_ARROW})

;; -----------------------------------------------------------------------------
;; Utils & DOM Helpers

(defn by-id [id]
  (.getElementById js/document id))

(defn by-tag-name [el tag]
  (.getElementsByTagName el tag))

(defn set-class [el name]
  (set! (.-className el) name))

(defn clear-class [el name]
  (set! (.-className el) ""))

(defn key-event->keycode [e]
  (.-keyCode e))

(defn tag-match [tag]
  (fn [el]
    (when-let [tag-name (.-tagName el)]
      (= tag (.toLowerCase tag-name)))))

(defn index-of [node-list el]
  (loop [i 0]
    (if (< i (alength node-list))
      (if (identical? (aget node-list i) el)
        i
        (recur (inc i)))
      -1)))

(extend-type default
  ICounted
  (-count [coll]
    (if (instance? js/NodeList coll)
      (alength coll)
      (accumulating-seq-count coll)))
  IIndexed
  (-nth
    ([coll n]
      (-nth coll n nil))
    ([coll n not-found]
      (if (instance? js/NodeList coll)
        (if (< n (count coll))
          (aget coll n)
          (throw (js/Error. "NodeList access out of bounds")))
        (linear-traversal-nth coll (.floor js/Math n) not-found)))))

;; -----------------------------------------------------------------------------
;; Reactive support

(defn put-all! [cs x]
  (doseq [c cs]
    (put! c x)))

(defn multiplex [in cs-or-n]
  (let [cs (if (number? cs-or-n)
             (repeatedly cs-or-n chan)
             cs-or-n)]
    (go (loop []
          (let [x (<! in)]
            (if-not (nil? x)
              (do
                (put-all! cs x)
                (recur))
              :done))))
    cs))

(defn remove
  ([f source] (remove (chan) f source))
  ([c f source]
    (go (while true
          (let [v (<! source)]
            (when-not (f v)
              (>! c v)))))
    c))

;; -----------------------------------------------------------------------------
;; Selection process

(defprotocol IUIList
  (-select! [list n])
  (-unselect! [list n]))

(defn select [list idx key]
  (if (= idx ::none)
    (condp = key
      :up (dec (count list))
      :down 0)
    (mod (({:up dec :down inc} key) idx)
      (count list))))

(defn selector [in list data]
  (let [out (chan)]
    (go
      (loop [selected ::none]
        (let [v (<! in)]
          (cond
            (nil? v) :ok
            (= v :select) (do (>! out (nth data selected))
                            (recur selected))
            :else (do (when (number? selected)
                        (-unselect! list selected))
                    (if (= v :out)
                      (recur ::none)
                      (let [n (if (number? v) v (select list selected v))]
                        (-select! list n)
                        (recur n))))))))
    out))

;; -----------------------------------------------------------------------------
;; HTML Demo

(defn hover-chan [el tag]
  (let [matcher (tag-match tag)
        matches (by-tag-name el tag)
        over (->> (events el "mouseover")
               (map
                 #(let [target (.-target %)]
                    (if (matcher target)
                      target
                      (if-let [el (dom/getAncestor target matcher)]
                        el
                        :no-match))))
               (remove #{:no-match})
               (map
                 #(index-of matches %)))
        out (->> (events el "mouseout")
              (filter
                (fn [e]
                  (and (matcher (.-target e))
                       (not (matcher (.-relatedTarget e))))))
              (map #(do :out)))]
    (distinct (fan-in [over out]))))

(defn selector-key->keyword [code]
  (condp = code
    UP_ARROW :up
    DOWN_ARROW :down
    ENTER :select))

(extend-type js/HTMLUListElement
  ICounted
  (-count [list]
    (alength (by-tag-name list "li")))
  IUIList
  (-select! [list n]
    (set-class (nth (by-tag-name list "li") n) "selected"))
  (-unselect! [list n]
    (clear-class (nth (by-tag-name list "li") n))))

#_(let [el    (by-id "list")
      hover (hover-chan el "li")
      keys  (->> (events js/window "keydown")
              (map key-event->keycode)
              (filter SELECTOR_KEYS)
              (map selector-key->keyword))
      click (->> (events el "click")
              (map #(do :select)))
      c     (selector (fan-in [hover keys click])
              el ["one" "two" "three"])]
  (go (while true
        (.log js/console (<! c)))))

;; -----------------------------------------------------------------------------
;; Array Demo

(extend-type array
  IUIList
  (-select! [list n]
    (aset list n (.replace (aget list n) "  " "* ")))
  (-unselect! [list n]
    (aset list n (.replace (aget list n) "* " "  "))))

#_(let [keys    (->> (events js/window "keydown")
                (map key-event->keycode)
                (filter SELECTOR_KEYS)
                (map selector-key->keyword))
      [k0 k1] (multiplex keys 2)
      k1      (filter #{:up :down} k1)
      list    (array "  one" "  two" "  three")
      c       (selector k0 list ["one" "two" "three"])]
  (.log js/console (.join list "\n"))
  (go (while true
        (alt!
          k1 ([v] (.log js/console (.join list "\n")))
          c  ([v] (.log js/console v))))))

(defn ->c [xs]
  (let [out (chan)]
    (go (loop [xs xs]
          (if-not (empty? xs)
            (do
              (>! out (first xs))
              (recur (rest xs)))
            (close! out))))
    out))

#_(let [c (->c [:down :down :down :enter])]
  (go (loop []
        (when-let [x (<! c)]
          (.log js/console x)
          (recur)))))

(let [test (->c [:down :down :down :select])
      list (array "  one" "  two" "  three")
      c    (selector test list ["one" "two" "three"])]
  (go
    (.log js/console (<! c))
    (.log js/console (.join list "\n"))))
