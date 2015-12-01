(ns violence-in-religious-text.core-test
  (:require [clojure.test :refer :all]
            [violence-in-religious-text.core :refer :all]
            [clojure.string :as str]))

;;;; Some black magic to allow testing of private functions.
;;;; Credit to Stuart Sierra.
;;;; https://groups.google.com/forum/#!topic/clojure/ODXlQFq5MPY
(defn refer-private [ns]
  (doseq [[symbol var] (ns-interns ns)]
    (when (:private (meta var))
      (intern *ns* symbol var))))

(refer-private 'violence-in-religious-text.core)

(deftest process-output-json-test
  (testing "Properly extracts the elements."
    (is (= (str "<center><table>"
                "<tr><td>Hi</td><td>there</td></tr>"
                "\n<tr><td>Bye</td><td>there</td></tr>"
                "</table></center>")
           (process-output-json 
              {:type "list-like"
              :open "<center><table>"
              :close "</table></center>"
              :separator "\n"
              :items [{:type "list-like"
                       :open "<tr><td>"
                       :close "</td></tr>"
                       :separator "</td><td>"
                       :items [{:type "html"
                                :content "Hi"}
                               {:type "html"
                                :content "there"}]}
                      {:type "list-like"
                       :open "<tr><td>"
                       :close "</td></tr>"
                       :separator "</td><td>"
                       :items [{:type "html"
                                :content "Bye"}
                                {:type "html"
                                 :content "there"}]}]})))))
                               
(deftest process-code-cell-test
  (testing "Properly formats the cell."
    (is (= (str "```clojure\n"
                "(def x :a)\n"
                "(def y :b)\n"
                "```\n")
           (process-code-cell
             ["(def x :a)"
              "(def y :b)"])))))
                
(deftest process-markdown-cell-test
  (testing "Properly formats the cell."
    (is (= "# This is a title\n\n---\n\nThis is text.\n"
           (process-markdown-cell
             [";;; # This is a title"
              ";;; "
              ";;; ---"
              ";;; "
              ";;; This is text."])))))
  
(deftest process-stdout-cell-test
  (testing "Properly formats the cell."
    (is (= (str "```\n"
                "This is output.\n"
                "```\n")
           (process-stdout-cell 
             [";;; This is output."])))))
                
(deftest process-output-cell-test
  (testing "Properly formats the cell when there are contents."
    (is (= "Hello!\n"
           (process-output-cell 
              [";;; {\"type\":\"html\",\"content\":\"Hello!\"}"]))))
  (testing "Properly formats the cell when there are no contents."
    (is (= "\n"
           (process-output-cell
             [";;; "])))))
            
(deftest process-cell-test
  (testing "Properly parses code cell."
    (is (= (str "```clojure\n"
                "(def x:a)\n"
                "```\n")
            (process-cell
              [[";; @@"]
               ["(def x:a)"]
               [";; @@"]]))))
  (testing "Properly parses markdown cell." 
    (is (= "**This is a markdown cell.**\n"
            (process-cell
              [[";; **"]
               [";;; **This is a markdown cell.**"]
               [";; **"]]))))
  (testing "Properly parses stdout cell." 
    (is (= (str "```\n"
                "Hello stdout.\n"
                "```\n")
          (process-cell
            [[";; ->"]
             [";;; Hello stdout."]
             [";; <-"]]))))
  (testing "Properly parses output cell." 
    (is (= "Hello!\n"
          (process-cell
            [[";; =>"]
             [";;; {\"type\":\"html\",\"content\":\"Hello!\"}"]
             [";; <="]])))))

(deftest gorilla-to-markdown-test
  (testing "Properly parses full set of notebook cells." 
    (is (= (str/join "\n"
                    ["#This is markdown."
                      ""
                      "---"
                      ""
                      "This is too."
                      ""
                      "```clojure"
                      "(println \"This is code.\")"
                      "```\n"
                      "```"
                      "This is code."
                      "```\n"
                      "nil"
                      ""])
          (gorilla-to-markdown
            (str/join "\n"
                      [";; gorilla-repl.fileformat = 1"
                       ""
                       ";; **"
                        ";;; #This is markdown."
                        ";;; "
                        ";;; ---"
                        ";;; "
                        ";;; This is too."
                        ";; **"
                        ""
                        ";; @@"
                        "(println \"This is code.\")"
                        ";; @@"
                        ";; ->"
                        ";;; This is code."
                        ";; <-"
                        ";; =>"
                        ";;; {\"type\":\"html\",\"content\":\"nil\"}"
                        ";; <="
                        ]))))))