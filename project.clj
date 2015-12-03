(defproject violence-in-religious-text "0.1.0"
  :description "A notebook analyzing violence in religious texts."
  :url "https://timothyrenner.github.io"
  :license {:name "MIT License"
            :url "https://github.com/timothyrenner/violence-in-religious-text/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.7.0"]
								 [clojure-opennlp "0.3.3"]
								 [clucy "0.4.0"]
								 [gg4clj "0.1.0"]
								 [com.kennycason/kumo "1.2"]
								 [cheshire "5.5.0"]]
	:main ^:skip-aot violence-in-religious-text.core
	:plugins [[lein-gorilla "0.3.5"]])
