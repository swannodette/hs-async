(defproject hs-async "0.1.0-SNAPSHOT"
  :description "core.async walk through for Hacker School"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :repositories {"sonatype-staging"
                 "https://oss.sonatype.org/content/groups/staging/"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1845-RC1"]
                 [org.clojure/core.match "0.2.0-rc4"]
                 [core.async "0.1.0-SNAPSHOT"]]

  :plugins [[lein-cljsbuild "0.3.2"]]

  :cljsbuild
  {:builds
   [{:id "hs-async"
     :source-paths ["src/hs_async"]
     :compiler {:optimizations :whitespace
                :pretty-print false
                :static-fns true
                :output-to "main.js"}}]})
