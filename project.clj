(defproject hs-async "0.1.0-SNAPSHOT"
  :description "core.async walk through for Hacker School"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :jvm-opts ^:replace ["-Xmx512m" "-server"]

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1934"]
                 [org.clojure/core.async "0.1.242.0-44b1e3-alpha"]]

  :plugins [[lein-cljsbuild "0.3.3"]]

  :cljsbuild
  {:builds
   [{:id "hs-async"
     :source-paths ["src/hs_async"]
     :compiler {:optimizations :none
                :pretty-print false
                :output-dir "out"
                :output-to "main.js"}}]})
