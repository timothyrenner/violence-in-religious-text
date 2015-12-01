(ns violence-in-religious-text.core
    (:require [clojure.string :as str]
              [cheshire.core :as json]))

(defn- process-output-json
  "Builds the HTML from the output JSON.
   Assumes the JSON has already been parsed, and that j is a map."
  [j]
  (case (:type j)
        "html" (:content j)
        "list-like" (str (:open j)
                         (str/join (:separator j)
                                   (map #(process-output-json %) (:items j)))
                         (:close j))))
                                   
(defn- process-code-cell
  "Formats a code cell in Github flavored Markdown.
   c is a seq of strings contaiing the code in the cell."
  [c]
  (str "```clojure\n"
       (str/join "\n" c)
       "\n```\n"))
     
(defn- process-markdown-cell
  "Formats a markdown cell.
   c is a sequence of strings containing markdown."
  [c]
  (->> c
       ;; Strip out leading comment characters.
       (map #(str/replace % #"^;;; " ""))
       ;; Mash back into string.
       (str/join "\n")
       ;; Add one last newline.
       (#(str % "\n"))))
  
(defn- process-stdout-cell
  "Formats a STDOUT cell.
   c is a sequence of strings containing the STDOUT output in the notebook."
  [c]
  (->> c
       ;; Strip out leading comment characters.
       (map #(str/replace % #"^;;; " ""))
       ;; Mash back into string.
       (str/join "\n")
       ;; Add code fences.
       ((fn [s] (str "```\n"
                     s
                     "\n```\n")))))

(defn- process-output-cell
  "Formats an output cell.
   c is a sequence of strings containing the output to be formatted.
   There should be only one string in the sequence."
   [c]
   (let [output (->> c
                     ;; Strip out leading comment characters.
                     (map #(str/replace % #"^;;; " ""))
                     ;; There should only be a single string.
                     first)]
    (if (str/blank? output)
        "\n"
        (str (process-output-json (json/parse-string output true)) "\n"))))

(defn- process-cell 
  "Formats a cell.
   cell is a 3-element sequence containing an open marker, contents, and close
   marker."
  [cell]
  (case (first cell)
        [";; @@"] (process-code-cell (second cell))
        [";; **"] (process-markdown-cell (second cell))
        [";; ->"] (process-stdout-cell (second cell))
        [";; =>"] (process-output-cell (second cell))))
      
(defn gorilla-to-markdown 
  "Converts the notebook string nb into a Markdown string."
  [nb]
  (let [cell-splitter #";; (@@|\*\*|<-|->|<=|=>)"]
                   ;; Split on lines.
    (->> (str/split-lines nb)
         ;; Drop first comment about gorilla version.
         rest
         ;; Split into partitions based on cell marker.
         (partition-by (fn [s] (re-find cell-splitter s)))
         ;; Drop seqs with blank strings.
         (remove (fn [s] (every? str/blank? s)))
         ;; Group into cells (open-contents-close).
         ;; This will break if there are blank cells in the
         ;; workbook (except at the end).
         (partition 3)
         ;; Process each cell.
         (map process-cell)
         ;; Mash back into a big string.
         (str/join "\n"))))
                   

(defn -main
  "Converts the Gorilla REPL notebook to raw Markdown."
  [& args]
  (let [nb (slurp "violence-in-religious-text-nb.clj")]
    (spit "violence-in-religious-text.md" (gorilla-to-markdown nb))))
