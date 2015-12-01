# Violence in Religious Texts



I'll do this analysis with [Gorilla REPL](http://gorilla-repl.org/) and Clojure.
If you want to follow along with the code, click the code panels.
If you want to run it yourself, clone the [git repo](https://github.com/timothyrenner/violence-in-religious-text).


```clojure
(ns violence-in-religious-text-nb
  (:require [gorilla-plot.core :as plot]
            [gorilla-repl.image :as img]
            [gorilla-repl.table :as tbl]
            [gg4clj.core :as gg4clj]
            [opennlp.nlp :as nlp]
            [clojure.string :as str]
            [clojure.set :as st]
            [clojure.java.io :as io]
            [clucy.core :as clucy])
  (:import [wordcloud WordCloud WordFrequency CollisionMode]
           [wordcloud.bg RectangleBackground]
           [javax.imageio ImageIO]))
```

<span class='clj-nil'>nil</span>

The general strategy is simple; count the number of sentences containing violence-related words in each religious book.
The first thing we'll need (besides the data) is a way of obtaining the sentences from the text.
It seems easier than it is; the variety of punctuation patterns, quotations, etc make this pretty nontrivial.
Fortunately it's something there's been a lot of work already done, and we're going to use it.
The [OpenNLP](https://opennlp.apache.org/) (NLP = natural language processing) library has a model for sentence chunking.

```clojure
;; Sentence chunking model can be obtained at:
;; http://opennlp.sourceforge.net/models-1.5/

(def get-sentences (nlp/make-sentence-detector "resources/en-sent.bin"))
```

<span class='clj-var'>#&#x27;violence-in-religious-text/get-sentences</span>

The books were pulled from Project Gutenberg (except the Jewish scriptures).
They can be obtained at the following links:

* Buddhism, [Dhammapada](https://www.gutenberg.org/ebooks/2017)
* Christianity, [The Holy Bible (World English Bible)](https://www.gutenberg.org/ebooks/8294)
* Hinduism, [The Vedas](https://www.gutenberg.org/ebooks/16295)
* Islam, [The Koran](https://www.gutenberg.org/ebooks/3434)
* Judaism, [The Jewish Scriptures](https://archive.org/details/holyscripturesac028077mbp)
* Latter Day Saints, [The Book of Mormon](https://www.gutenberg.org/ebooks/17)

The choice of Hindu and Buddhist texts is somewhat arbitrary; there isn't any single centralized holy text for those religions, so I picked two of the more well-known (to me at least) texts.
The Jewish Scriptures I pulled is perhaps not the best translation to apply this type of analysis on.
Unfortunately Project Gutenberg's collection is just links to the Bible on an individual book-by-book basis.
This would be an immense amount of work to assemble and clean, so I opted for a collected edition.

Each book is presented as plain text, but a substantial (and I mean _substantial_) amount of cleaning is required.
I will spare you the details, but suffice it to say there could be room for improvement.
Hit the code bar at your own risk.

```clojure
;; Each of these texts requires a substantial amount of cleaning.
;; Rather than scrub this offline, I'll do it here so it can be reproduced easily just by downloading the files.
;; The scrubbing is not trivial.

;; Aside from formatting differences, each text has front and tail matter that should be removed.
;; The lines of the files to keep are as follows:
;; Dhammapada: 87 - 1717
;; The Holy Bible: 45 - 79122
;; The Vedas: 4363 - 19604
;; The Koran: 1034 - 27074
;; Jewish Scriptures: 1509 - 147516
;; Book of Mormon: 133 - 39914

(def dhammapada 
  ;; Read the file, split on lines.
  (->> (str/split (slurp "resources/the-dhammapada.txt") #"\r\n")
       ;; Take the first 1717 lines (lines prior to tail matter).
       (take 1717)
       ;; Drop the introduction.
       (drop 87)
       ;; Kill leading / trailing whitespace.
       (map str/trim)
       ;; Stitch together into a single string.
       (str/join " ")
       ;; Run the sentence chunker.
       get-sentences
       ;; Drop leading and trailing numbers.
       (map #(str/replace % #"(^\d+\.|\d+\.$)" ""))
       ;; Remove empty strings.
       (remove str/blank?)))

(def bible
  ;; Read the file, split on lines.
  (->> (str/split (slurp "resources/christian-bible.txt") #"\r\n")
       ;; Take the first 79122 lines (lines prior to tail matter).
       (take 79122)
       ;; Drop the introduction.
       (drop 45)
       ;; Kill leading / trailing whitespace.
       (map str/trim)
       ;; Replace chapter:verse markers.
       (map #(str/replace % #"^\d+:\d+" ""))
       ;; Stitch together into a single string.
       (str/join " ")
       ;; Run the sentence chunker.
       get-sentences
       ;; Remove empty strings.
       (remove str/blank?)))

(def vedas
  ;; Read the file, split on lines.
  (->> (str/split (slurp "resources/hindu-vedas.txt") #"\r\n")
       ;; Take the first 19604 lines (lines prior to tail matter).
       (take 19604)
       ;; Drop the introduction.
       (drop 4518)
       ;; Kill any leading/trailing whitespace.
       (map str/trim)
       ;; Mash back together to preserve paragraphs.
       (str/join "\r\n")
       ;; Split on double carriage returns (paragraphs).
       ((fn [s] (str/split s #"\r\n\r\n")))
       ;; Keep only the paragraphs that start with a digit.
       ;; Other paragraphs are commentary.
       (filter #(re-find #"^\d+\." %))
       ;; Drop the digit.
       (map #(str/replace % #"^\d+\.\s*" ""))
       ;; Drop intermediate carriage returns.
       (map #(str/replace % #"(\r|\n)" " "))
       ;; Mash into one string.
       (str/join " ")
       ;; Run the sentence chunker.
       get-sentences
       ;; Drop blank sentences.
       (remove str/blank?)))

(def koran
  ;; Read the file, split on lines.
  (->> (str/split (slurp "resources/the-koran.txt") #"\r\n")
       ;; Take the first 27074 lines (lines prior to tail matter).
       (take 27074)
       ;; Drop the introduction.
       (drop 1034)
       ;; Kill any leading / trailing whitespace.
       (map str/trim)
       ;; Mash the lines back together to restore paragraphs.
       (str/join "\r\n")
       ;; Split on the paragraphs.
       ((fn [s] (str/split s #"\r\n\r\n")))
       ;; Drop footnotes.
       (remove #(re-find #"^\d+" %))
       ;; Drop underscore lines.
       (remove #(re-find #"^\s*_*$" %))
       ;; Remove footnote digits (makes sentence chunker work better).
       (map #(str/replace % #"\d" ""))
       ;; Remove intermediate carriage returns.
       (map #(str/replace % #"(\r|\n)" " "))
       ;; Mash into one big string.
       (str/join " ")
       ;; Run the sentence chunker.
       get-sentences))

(def jewish-scriptures
  ;; Read the file, split on lines.
  (->> (str/split (slurp "resources/jewish-scriptures.txt") #"\n")
       ;; Take the first 147516 lines (lines prior to the tail matter).
       (take 147516)
       ;; Drop the HTML front matter.
       (drop 1508)
       ;; Mash the lines back together.
       (str/join "\n")
       ;; Split on the page breaks.
       ((fn [s] (str/split s #"\n\n\n\n")))
       ;; Remove lines containing only numbers.
       (remove #(re-find #"^\s*\d+\.?\d*\s*$" %))
       ;; Remove lines containing only capital letters,
       ;; like the book name at the bottom of each page.
       (remove #(re-find #"^\s*[A-Z]+\s*$" %))
       ;; Remove verse numbers.
       (map #(str/replace % #"\d" ""))
       ;; Remove embedded new line characters.
       (map #(str/replace % #"\n" " "))
       ;; Mash into one big string.
       (str/join " ")
       ;; Run the sentence chunker.
       get-sentences))

(def book-of-mormon
  ;; Read the file, split on lines.
  (->> (str/split (slurp "resources/the-book-of-mormon.txt") #"\r\n")
       ;; Take the first 39914 lines (lines prior to the tail matter).
       (take 39914)
       ;; Drop the front matter.
       (drop 133)
       ;; Remove book labels.
       (remove #(re-find #"^\d\s+\w+\s+\d+\s*$" %))
       ;; Remove chapter markers.
       (remove #(re-find #"^\w+\s+\d+\s*$" %))
       ;; Remove verse headers.
       (remove #(re-find #"^\d\s+\w+\s+\d+:\d+\s*$" %))
       ;; Remove blanks.
       (remove str/blank?)
       ;; Mash into one big string.
       (str/join " ")
       ;; Remove inline verse markers.
       ((fn [s] (str/replace s #"\d" "")))
       ;; Run the sentence chunker.
       get-sentences))
```

<span class='clj-var'>#&#x27;violence-in-religious-text/book-of-mormon</span>

How many sentences in each book?

```clojure
(def book-counts {"The Dhammapada" (count dhammapada)
                  "The Holy Bible" (count bible)
                  "The Vedas" (count vedas)
                  "The Koran" (count koran)
                  "The Jewish Scriptures" (count jewish-scriptures)
                  "The Book of Mormon" (count book-of-mormon)})

(tbl/table-view (map (fn [[k v]] [k (format "%,d" v)]) book-counts))
```

<center><table><tr><td><span class='clj-string'>&quot;The Dhammapada&quot;</span></td><td><span class='clj-string'>&quot;548&quot;</span></td></tr>
<tr><td><span class='clj-string'>&quot;The Holy Bible&quot;</span></td><td><span class='clj-string'>&quot;36,316&quot;</span></td></tr>
<tr><td><span class='clj-string'>&quot;The Vedas&quot;</span></td><td><span class='clj-string'>&quot;243&quot;</span></td></tr>
<tr><td><span class='clj-string'>&quot;The Koran&quot;</span></td><td><span class='clj-string'>&quot;8,183&quot;</span></td></tr>
<tr><td><span class='clj-string'>&quot;The Jewish Scriptures&quot;</span></td><td><span class='clj-string'>&quot;20,498&quot;</span></td></tr>
<tr><td><span class='clj-string'>&quot;The Book of Mormon&quot;</span></td><td><span class='clj-string'>&quot;7,605&quot;</span></td></tr></table></center>

Note that the Jewish Scriptures and the Bible both have _by far_ the highest number of sentences.
This will come into play when we start counting the violent sentences later.

Next, we need a list of words related to violence.
Here's 25 words I picked kind-of randomly with a little googling.


```clojure
(def violent-words
  ["wound"     "hurt"    "fight"  "violate" "destroy" 
   "slaughter" "murder"  "kill"   "attack"  "break" 
   "crush"     "provoke" "anger"  "hatred"  "bloodshed" 
   "rage"      "fear"    "suffer" "violent" 
   "war"       "stab"    "shoot"  "strike"  "rape"])
```

<span class='clj-var'>#&#x27;violence-in-religious-text/violent-words</span>

Finally, we need a way to detect when a word is _in_ a sentence.
This is even harder than detecting sentences; we have to detect _words_ within a sentence.

Suppose we want to search a book for instances of the word _kill_.
We can search for the exact word and we'll do okay, but we'd miss any that were at the end of a sentence (_kill?_, _kill!_, _kill._) or really any instance followed by punctuation (_kill,_, _kill;_).
We'd also miss words we may want to count: _killing_, _killed_, etc.

Luckily, this is not an uncommon need; it's called the _search problem_.
There are a number of ways to tackle this, but since we're on the JVM I opted for [Apache Lucene](http://lucene.apache.org/).
Lucene is essentially a document store that let's you pull documents based on textual queries.
It does this by analyzing the documents as they're added and indexes them based on their contents using natural language processing techniques with (probably) a sprinkle of magic.
We can use it to index each sentence of each book, then perform a search on the words we want.
At that point all we'd need to do is count the results and we have our answer.

```clojure
(def index (clucy/memory-index))

(apply clucy/add index (map (fn [s] {:book "The Dhammapada" :sentence s}) 
                            dhammapada))

(apply clucy/add index (map (fn [s] {:book "The Holy Bible" :sentence s}) 
                            bible))

(apply clucy/add index (map (fn [s] {:book "The Vedas" :sentence s}) 
                            vedas))

(apply clucy/add index (map (fn [s] {:book "The Koran" :sentence s}) 
                            koran))

(apply clucy/add index (map (fn [s] {:book "The Jewish Scriptures" :sentence s}) 
                            jewish-scriptures))

(apply clucy/add index (map (fn [s] {:book "The Book of Mormon" :sentence s}) 
                            book-of-mormon))
```

<span class='clj-nil'>nil</span>

Just to demonstrate Lucene's capabilities, I'll perform a query for the word "rape" and look at the sentences it retrieves.

```clojure
(doseq [s (clucy/search index "rape*~0.8" 10)] 
  (println (str (:book s) ": " (:sentence s) "\n")))
```

```
The Holy Bible: Their houses will be ransacked, and their wives raped.

The Jewish Scriptures: In those days saw I in Judah some  treading winepresses on the sabbath,  and bringing in heaps of corn, and  lading asses therewith; as also wine,  ;rapes, and figs, and all manner of  urdens, which they brought into  Jerusalem on the sabbath day; and I  forewarned them in the day where-  in they sold victuals.


```

<span class='clj-nil'>nil</span>

Notice that the query returned "raped" and ";rapes"; Lucene doesn't necessarily need an exact match.
It's clear from the context that the match in the Jewish Scriptures is incorrect.
Not only is it not referring to the correct word in the first place (should be "grapes"), but the word isn't even spelled properly. 
This is important, as it reveals the limitations of the analysis technique.

* Unstructured text is almost never "all the way clean".
* Obtaining context is extremely difficult.

There has been an enormous amount of progress in the NLP community on teasing out context, particularly with neural network based techniques.
We'll be using exactly none of that research and stick with the simple stuff.

Without further delay, let's take a look at what Lucene can get us.

```clojure
(def total-sentences (apply + (vals book-counts)))

(def violent-sentences
  ;; Perform and flatten the search for each violent word.
  (mapcat (fn [w]
            ;; Inject the query into the result.
            (map (fn [s] (into {:word w} s))
                 ;; *~0.8 -> wildcard at the end + edit distance fuzziness
                 (clucy/search index (str w "*~0.8") total-sentences))) 
           violent-words))

(def violent-sentence-counts
  ;; Group by the books. Strip out the query word so we don't double count.
  (->> (group-by :book (map #(select-keys % [:book :sentence]) violent-sentences))
       ;; Put in empty vector defaults in case there are no violent sentences.
       (into (apply hash-map (interleave (keys book-counts) (repeat []))))
       ;; Count the distinct number of sentences in each.
       (map (fn [[b s]] 
              (let [c (count (distinct s))
                    r (/ (float c) (book-counts b))]
                {:book [b] 
                 :count [c]
                 :clabel [(format "%,d" c)]
                 :ratio [r] 
                 :rlabel [(format "%.3f" r)]})))
       ;; Flatten into a columnar form for the R data frame.
       (reduce (fn [a x] (merge-with concat a x)) {})))
```

<span class='clj-var'>#&#x27;violence-in-religious-text/violent-sentence-counts</span>

The first thing we should look at is the raw counts of sentences containing violence.

```clojure
;; Define the data structure for the plot.
(def violent-count-plot 
  [[:<- :g (gg4clj/data-frame violent-sentence-counts)]
              (gg4clj/r+
                [:ggplot :g [:aes {:x :book :y :count :label :clabel}]]
                ;; Style the main bar.
                [:geom_bar {:stat "identity" :color "steelblue" :fill "steelblue"}]
                ;; Add the values.
                [:geom_text {:hjust -0.1}]
                ;; Extend the y axis to accomodate labels.
                [:ylim 0 3000]
                ;; Add title.
                [:ggtitle "Sentences with Violent Words"]
                ;; Remove the axis labels.
                [:xlab ""]
                [:ylab ""]
                ;; Flip to horizontal bar.
                [:coord_flip])])

;; Save to a file for convenience.
(spit "resources/violent-count-plot.svg" (gg4clj/render violent-count-plot))

;; Render it in the REPL.
(gg4clj/view violent-count-plot)
```

<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="468pt" height="289pt" viewBox="0 0 468 289" version="1.1">
<defs>
<g>
<symbol overflow="visible" id="b4c9bbdb-52ec-431e-8403-fe8cbf4c2146">
<path style="stroke:none;" d="M 0.453125 0 L 0.453125 -10.171875 L 8.53125 -10.171875 L 8.53125 0 Z M 7.25 -1.265625 L 7.25 -8.890625 L 1.734375 -8.890625 L 1.734375 -1.265625 Z M 7.25 -1.265625 "/>
</symbol>
<symbol overflow="visible" id="933e3a45-7bee-4eb6-85e2-6ca9d3aeaf2a">
<path style="stroke:none;" d="M 1.359375 -7.015625 L 1.359375 -7.96875 C 2.253906 -8.0625 2.878906 -8.207031 3.234375 -8.40625 C 3.585938 -8.613281 3.851562 -9.101562 4.03125 -9.875 L 5.015625 -9.875 L 5.015625 0 L 3.6875 0 L 3.6875 -7.015625 Z M 1.359375 -7.015625 "/>
</symbol>
<symbol overflow="visible" id="0c5398bb-aa3d-42a5-804f-c5905b57c00e">
<path style="stroke:none;" d="M 1.171875 1.453125 C 1.492188 1.390625 1.71875 1.164062 1.84375 0.78125 C 1.914062 0.570312 1.953125 0.367188 1.953125 0.171875 C 1.953125 0.140625 1.945312 0.109375 1.9375 0.078125 C 1.9375 0.0546875 1.9375 0.03125 1.9375 0 L 1.171875 0 L 1.171875 -1.515625 L 2.65625 -1.515625 L 2.65625 -0.109375 C 2.65625 0.441406 2.546875 0.921875 2.328125 1.328125 C 2.109375 1.742188 1.722656 2.003906 1.171875 2.109375 Z M 1.171875 1.453125 "/>
</symbol>
<symbol overflow="visible" id="54f13a55-eab5-41fb-bce9-77f5b6edc655">
<path style="stroke:none;" d="M 3.828125 -9.90625 C 5.109375 -9.90625 6.035156 -9.378906 6.609375 -8.328125 C 7.054688 -7.503906 7.28125 -6.382812 7.28125 -4.96875 C 7.28125 -3.625 7.078125 -2.507812 6.671875 -1.625 C 6.097656 -0.363281 5.148438 0.265625 3.828125 0.265625 C 2.640625 0.265625 1.753906 -0.25 1.171875 -1.28125 C 0.679688 -2.144531 0.4375 -3.300781 0.4375 -4.75 C 0.4375 -5.875 0.582031 -6.84375 0.875 -7.65625 C 1.425781 -9.15625 2.410156 -9.90625 3.828125 -9.90625 Z M 3.8125 -0.859375 C 4.457031 -0.859375 4.972656 -1.144531 5.359375 -1.71875 C 5.742188 -2.289062 5.9375 -3.359375 5.9375 -4.921875 C 5.9375 -6.046875 5.796875 -6.96875 5.515625 -7.6875 C 5.242188 -8.414062 4.707031 -8.78125 3.90625 -8.78125 C 3.175781 -8.78125 2.640625 -8.4375 2.296875 -7.75 C 1.960938 -7.0625 1.796875 -6.046875 1.796875 -4.703125 C 1.796875 -3.691406 1.90625 -2.878906 2.125 -2.265625 C 2.445312 -1.328125 3.007812 -0.859375 3.8125 -0.859375 Z M 3.8125 -0.859375 "/>
</symbol>
<symbol overflow="visible" id="4831850d-da5a-4e63-80fc-1e7aaba00389">
<path style="stroke:none;" d="M 3.6875 0.265625 C 2.507812 0.265625 1.65625 -0.0507812 1.125 -0.6875 C 0.601562 -1.332031 0.34375 -2.117188 0.34375 -3.046875 L 1.640625 -3.046875 C 1.691406 -2.398438 1.8125 -1.929688 2 -1.640625 C 2.320312 -1.117188 2.90625 -0.859375 3.75 -0.859375 C 4.40625 -0.859375 4.929688 -1.035156 5.328125 -1.390625 C 5.722656 -1.742188 5.921875 -2.195312 5.921875 -2.75 C 5.921875 -3.425781 5.710938 -3.898438 5.296875 -4.171875 C 4.878906 -4.453125 4.300781 -4.59375 3.5625 -4.59375 C 3.476562 -4.59375 3.390625 -4.585938 3.296875 -4.578125 C 3.210938 -4.578125 3.128906 -4.578125 3.046875 -4.578125 L 3.046875 -5.671875 C 3.171875 -5.660156 3.273438 -5.648438 3.359375 -5.640625 C 3.453125 -5.640625 3.550781 -5.640625 3.65625 -5.640625 C 4.125 -5.640625 4.503906 -5.710938 4.796875 -5.859375 C 5.328125 -6.117188 5.59375 -6.582031 5.59375 -7.25 C 5.59375 -7.738281 5.414062 -8.113281 5.0625 -8.375 C 4.71875 -8.644531 4.3125 -8.78125 3.84375 -8.78125 C 3.007812 -8.78125 2.4375 -8.503906 2.125 -7.953125 C 1.945312 -7.648438 1.84375 -7.21875 1.8125 -6.65625 L 0.59375 -6.65625 C 0.59375 -7.394531 0.738281 -8.023438 1.03125 -8.546875 C 1.539062 -9.460938 2.429688 -9.921875 3.703125 -9.921875 C 4.710938 -9.921875 5.492188 -9.695312 6.046875 -9.25 C 6.609375 -8.800781 6.890625 -8.148438 6.890625 -7.296875 C 6.890625 -6.679688 6.722656 -6.1875 6.390625 -5.8125 C 6.191406 -5.570312 5.929688 -5.390625 5.609375 -5.265625 C 6.128906 -5.117188 6.535156 -4.835938 6.828125 -4.421875 C 7.117188 -4.015625 7.265625 -3.519531 7.265625 -2.9375 C 7.265625 -1.988281 6.953125 -1.21875 6.328125 -0.625 C 5.703125 -0.03125 4.820312 0.265625 3.6875 0.265625 Z M 3.6875 0.265625 "/>
</symbol>
<symbol overflow="visible" id="876ef0d4-ad74-4833-a123-ddd48f998b8f">
<path style="stroke:none;" d="M 7.40625 -9.75 L 7.40625 -8.65625 C 7.09375 -8.34375 6.671875 -7.800781 6.140625 -7.03125 C 5.609375 -6.269531 5.140625 -5.445312 4.734375 -4.5625 C 4.335938 -3.695312 4.035156 -2.910156 3.828125 -2.203125 C 3.691406 -1.742188 3.519531 -1.007812 3.3125 0 L 1.9375 0 C 2.25 -1.894531 2.9375 -3.773438 4 -5.640625 C 4.632812 -6.742188 5.300781 -7.691406 6 -8.484375 L 0.515625 -8.484375 L 0.515625 -9.75 Z M 7.40625 -9.75 "/>
</symbol>
<symbol overflow="visible" id="de9b5837-1d48-46e6-a6f4-647d8f64554b">
<path style="stroke:none;" d="M 0.4375 0 C 0.488281 -0.851562 0.664062 -1.59375 0.96875 -2.21875 C 1.28125 -2.851562 1.878906 -3.429688 2.765625 -3.953125 L 4.09375 -4.71875 C 4.6875 -5.0625 5.101562 -5.359375 5.34375 -5.609375 C 5.726562 -5.984375 5.921875 -6.421875 5.921875 -6.921875 C 5.921875 -7.492188 5.742188 -7.945312 5.390625 -8.28125 C 5.046875 -8.625 4.585938 -8.796875 4.015625 -8.796875 C 3.160156 -8.796875 2.566406 -8.472656 2.234375 -7.828125 C 2.066406 -7.484375 1.972656 -7.003906 1.953125 -6.390625 L 0.6875 -6.390625 C 0.695312 -7.253906 0.851562 -7.957031 1.15625 -8.5 C 1.695312 -9.457031 2.648438 -9.9375 4.015625 -9.9375 C 5.148438 -9.9375 5.976562 -9.628906 6.5 -9.015625 C 7.03125 -8.410156 7.296875 -7.726562 7.296875 -6.96875 C 7.296875 -6.175781 7.015625 -5.5 6.453125 -4.9375 C 6.128906 -4.613281 5.550781 -4.21875 4.71875 -3.75 L 3.765625 -3.21875 C 3.316406 -2.96875 2.960938 -2.734375 2.703125 -2.515625 C 2.242188 -2.109375 1.953125 -1.660156 1.828125 -1.171875 L 7.25 -1.171875 L 7.25 0 Z M 0.4375 0 "/>
</symbol>
<symbol overflow="visible" id="bfb808fc-01d6-4644-bbd3-2c96ec5ff162">
<path style="stroke:none;" d="M 3.859375 -5.75 C 4.398438 -5.75 4.828125 -5.898438 5.140625 -6.203125 C 5.453125 -6.515625 5.609375 -6.882812 5.609375 -7.3125 C 5.609375 -7.6875 5.457031 -8.023438 5.15625 -8.328125 C 4.863281 -8.640625 4.414062 -8.796875 3.8125 -8.796875 C 3.207031 -8.796875 2.769531 -8.640625 2.5 -8.328125 C 2.238281 -8.023438 2.109375 -7.664062 2.109375 -7.25 C 2.109375 -6.78125 2.28125 -6.410156 2.625 -6.140625 C 2.976562 -5.878906 3.390625 -5.75 3.859375 -5.75 Z M 3.9375 -0.84375 C 4.507812 -0.84375 4.984375 -1 5.359375 -1.3125 C 5.742188 -1.625 5.9375 -2.09375 5.9375 -2.71875 C 5.9375 -3.351562 5.738281 -3.835938 5.34375 -4.171875 C 4.957031 -4.503906 4.457031 -4.671875 3.84375 -4.671875 C 3.25 -4.671875 2.757812 -4.5 2.375 -4.15625 C 2 -3.820312 1.8125 -3.351562 1.8125 -2.75 C 1.8125 -2.238281 1.984375 -1.789062 2.328125 -1.40625 C 2.671875 -1.03125 3.207031 -0.84375 3.9375 -0.84375 Z M 2.15625 -5.28125 C 1.8125 -5.425781 1.539062 -5.597656 1.34375 -5.796875 C 0.976562 -6.171875 0.796875 -6.648438 0.796875 -7.234375 C 0.796875 -7.972656 1.0625 -8.609375 1.59375 -9.140625 C 2.132812 -9.671875 2.894531 -9.9375 3.875 -9.9375 C 4.832031 -9.9375 5.578125 -9.6875 6.109375 -9.1875 C 6.648438 -8.6875 6.921875 -8.101562 6.921875 -7.4375 C 6.921875 -6.8125 6.765625 -6.3125 6.453125 -5.9375 C 6.273438 -5.71875 6.003906 -5.503906 5.640625 -5.296875 C 6.046875 -5.109375 6.367188 -4.890625 6.609375 -4.640625 C 7.046875 -4.179688 7.265625 -3.582031 7.265625 -2.84375 C 7.265625 -1.96875 6.972656 -1.226562 6.390625 -0.625 C 5.804688 -0.0195312 4.976562 0.28125 3.90625 0.28125 C 2.9375 0.28125 2.117188 0.0195312 1.453125 -0.5 C 0.785156 -1.019531 0.453125 -1.78125 0.453125 -2.78125 C 0.453125 -3.363281 0.59375 -3.867188 0.875 -4.296875 C 1.164062 -4.722656 1.59375 -5.050781 2.15625 -5.28125 Z M 2.15625 -5.28125 "/>
</symbol>
<symbol overflow="visible" id="ce154712-7e91-40f4-8cec-1a76a44aff62">
<path style="stroke:none;" d="M 4.6875 -3.515625 L 4.6875 -8 L 1.515625 -3.515625 Z M 4.703125 0 L 4.703125 -2.421875 L 0.359375 -2.421875 L 0.359375 -3.640625 L 4.90625 -9.9375 L 5.953125 -9.9375 L 5.953125 -3.515625 L 7.40625 -3.515625 L 7.40625 -2.421875 L 5.953125 -2.421875 L 5.953125 0 Z M 4.703125 0 "/>
</symbol>
<symbol overflow="visible" id="c90fcbd7-1ffa-41da-be99-cb04d4fa9285">
<path style="stroke:none;" d="M 1.75 -2.53125 C 1.832031 -1.8125 2.160156 -1.316406 2.734375 -1.046875 C 3.035156 -0.910156 3.378906 -0.84375 3.765625 -0.84375 C 4.503906 -0.84375 5.050781 -1.078125 5.40625 -1.546875 C 5.757812 -2.015625 5.9375 -2.535156 5.9375 -3.109375 C 5.9375 -3.804688 5.722656 -4.34375 5.296875 -4.71875 C 4.878906 -5.09375 4.375 -5.28125 3.78125 -5.28125 C 3.351562 -5.28125 2.984375 -5.195312 2.671875 -5.03125 C 2.367188 -4.863281 2.109375 -4.632812 1.890625 -4.34375 L 0.8125 -4.40625 L 1.578125 -9.75 L 6.71875 -9.75 L 6.71875 -8.546875 L 2.5 -8.546875 L 2.078125 -5.78125 C 2.304688 -5.957031 2.523438 -6.085938 2.734375 -6.171875 C 3.109375 -6.328125 3.535156 -6.40625 4.015625 -6.40625 C 4.929688 -6.40625 5.703125 -6.113281 6.328125 -5.53125 C 6.960938 -4.945312 7.28125 -4.203125 7.28125 -3.296875 C 7.28125 -2.359375 6.988281 -1.53125 6.40625 -0.8125 C 5.832031 -0.101562 4.910156 0.25 3.640625 0.25 C 2.828125 0.25 2.109375 0.0234375 1.484375 -0.421875 C 0.867188 -0.878906 0.523438 -1.582031 0.453125 -2.53125 Z M 1.75 -2.53125 "/>
</symbol>
<symbol overflow="visible" id="58ec2523-660e-481f-aed1-880726477b79">
<path style="stroke:none;" d="M 0.3125 0 L 0.3125 -6.875 L 5.765625 -6.875 L 5.765625 0 Z M 4.90625 -0.859375 L 4.90625 -6.015625 L 1.171875 -6.015625 L 1.171875 -0.859375 Z M 4.90625 -0.859375 "/>
</symbol>
<symbol overflow="visible" id="7f9c35d7-e87a-45e6-b915-07c79e79ba88">
<path style="stroke:none;" d="M 5.734375 -6.875 L 5.734375 -6.0625 L 3.421875 -6.0625 L 3.421875 0 L 2.46875 0 L 2.46875 -6.0625 L 0.15625 -6.0625 L 0.15625 -6.875 Z M 5.734375 -6.875 "/>
</symbol>
<symbol overflow="visible" id="518a0f4d-ebf2-4f47-a5c5-3c620d8f5aa4">
<path style="stroke:none;" d="M 0.625 -6.90625 L 1.46875 -6.90625 L 1.46875 -4.34375 C 1.664062 -4.59375 1.84375 -4.769531 2 -4.875 C 2.269531 -5.050781 2.609375 -5.140625 3.015625 -5.140625 C 3.742188 -5.140625 4.238281 -4.882812 4.5 -4.375 C 4.632812 -4.09375 4.703125 -3.707031 4.703125 -3.21875 L 4.703125 0 L 3.84375 0 L 3.84375 -3.15625 C 3.84375 -3.53125 3.796875 -3.800781 3.703125 -3.96875 C 3.546875 -4.25 3.257812 -4.390625 2.84375 -4.390625 C 2.488281 -4.390625 2.171875 -4.265625 1.890625 -4.015625 C 1.609375 -3.773438 1.46875 -3.320312 1.46875 -2.65625 L 1.46875 0 L 0.625 0 Z M 0.625 -6.90625 "/>
</symbol>
<symbol overflow="visible" id="571abcf8-31c2-4101-b2d0-25e331728abb">
<path style="stroke:none;" d="M 2.703125 -5.125 C 3.054688 -5.125 3.398438 -5.039062 3.734375 -4.875 C 4.078125 -4.707031 4.332031 -4.492188 4.5 -4.234375 C 4.675781 -3.972656 4.789062 -3.675781 4.84375 -3.34375 C 4.894531 -3.113281 4.921875 -2.742188 4.921875 -2.234375 L 1.234375 -2.234375 C 1.253906 -1.722656 1.375 -1.3125 1.59375 -1 C 1.820312 -0.695312 2.171875 -0.546875 2.640625 -0.546875 C 3.085938 -0.546875 3.441406 -0.691406 3.703125 -0.984375 C 3.847656 -1.148438 3.953125 -1.347656 4.015625 -1.578125 L 4.84375 -1.578125 C 4.820312 -1.390625 4.75 -1.179688 4.625 -0.953125 C 4.507812 -0.734375 4.375 -0.550781 4.21875 -0.40625 C 3.957031 -0.15625 3.640625 0.015625 3.265625 0.109375 C 3.054688 0.148438 2.828125 0.171875 2.578125 0.171875 C 1.953125 0.171875 1.421875 -0.0507812 0.984375 -0.5 C 0.554688 -0.957031 0.34375 -1.59375 0.34375 -2.40625 C 0.34375 -3.21875 0.5625 -3.875 1 -4.375 C 1.4375 -4.875 2.003906 -5.125 2.703125 -5.125 Z M 4.046875 -2.90625 C 4.015625 -3.269531 3.9375 -3.5625 3.8125 -3.78125 C 3.582031 -4.1875 3.195312 -4.390625 2.65625 -4.390625 C 2.269531 -4.390625 1.941406 -4.25 1.671875 -3.96875 C 1.410156 -3.695312 1.273438 -3.34375 1.265625 -2.90625 Z M 2.625 -5.140625 Z M 2.625 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="c8d0ed67-e94d-44a6-afa0-e99cb47f3045">
<path style="stroke:none;" d=""/>
</symbol>
<symbol overflow="visible" id="d54f06c4-1567-4e3b-bb84-7e7972ed21f9">
<path style="stroke:none;" d="M 3.3125 -3.96875 C 3.707031 -3.96875 4.015625 -4.023438 4.234375 -4.140625 C 4.578125 -4.304688 4.75 -4.613281 4.75 -5.0625 C 4.75 -5.507812 4.566406 -5.8125 4.203125 -5.96875 C 3.992188 -6.0625 3.6875 -6.109375 3.28125 -6.109375 L 1.625 -6.109375 L 1.625 -3.96875 Z M 3.625 -0.796875 C 4.195312 -0.796875 4.609375 -0.960938 4.859375 -1.296875 C 5.003906 -1.503906 5.078125 -1.753906 5.078125 -2.046875 C 5.078125 -2.546875 4.851562 -2.890625 4.40625 -3.078125 C 4.175781 -3.171875 3.863281 -3.21875 3.46875 -3.21875 L 1.625 -3.21875 L 1.625 -0.796875 Z M 0.703125 -6.875 L 3.65625 -6.875 C 4.46875 -6.875 5.039062 -6.632812 5.375 -6.15625 C 5.582031 -5.875 5.6875 -5.546875 5.6875 -5.171875 C 5.6875 -4.742188 5.5625 -4.390625 5.3125 -4.109375 C 5.1875 -3.960938 5.003906 -3.828125 4.765625 -3.703125 C 5.109375 -3.566406 5.367188 -3.414062 5.546875 -3.25 C 5.859375 -2.945312 6.015625 -2.535156 6.015625 -2.015625 C 6.015625 -1.566406 5.875 -1.164062 5.59375 -0.8125 C 5.175781 -0.269531 4.515625 0 3.609375 0 L 0.703125 0 Z M 0.703125 -6.875 "/>
</symbol>
<symbol overflow="visible" id="62a1d5bd-2b0b-401e-853e-2b64689af9f3">
<path style="stroke:none;" d="M 2.609375 -0.546875 C 3.171875 -0.546875 3.554688 -0.753906 3.765625 -1.171875 C 3.972656 -1.597656 4.078125 -2.070312 4.078125 -2.59375 C 4.078125 -3.0625 4 -3.441406 3.84375 -3.734375 C 3.601562 -4.191406 3.195312 -4.421875 2.625 -4.421875 C 2.101562 -4.421875 1.722656 -4.222656 1.484375 -3.828125 C 1.253906 -3.441406 1.140625 -2.96875 1.140625 -2.40625 C 1.140625 -1.875 1.253906 -1.429688 1.484375 -1.078125 C 1.722656 -0.722656 2.097656 -0.546875 2.609375 -0.546875 Z M 2.640625 -5.15625 C 3.285156 -5.15625 3.832031 -4.941406 4.28125 -4.515625 C 4.726562 -4.085938 4.953125 -3.453125 4.953125 -2.609375 C 4.953125 -1.804688 4.753906 -1.140625 4.359375 -0.609375 C 3.960938 -0.078125 3.351562 0.1875 2.53125 0.1875 C 1.84375 0.1875 1.296875 -0.046875 0.890625 -0.515625 C 0.484375 -0.984375 0.28125 -1.613281 0.28125 -2.40625 C 0.28125 -3.238281 0.492188 -3.90625 0.921875 -4.40625 C 1.347656 -4.90625 1.921875 -5.15625 2.640625 -5.15625 Z M 2.609375 -5.140625 Z M 2.609375 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="73ae7141-8d61-425b-b033-a3f46a945a6f">
<path style="stroke:none;" d="M 0.59375 -6.875 L 1.40625 -6.875 L 1.40625 -2.890625 L 3.578125 -5.015625 L 4.65625 -5.015625 L 2.734375 -3.140625 L 4.765625 0 L 3.6875 0 L 2.125 -2.53125 L 1.40625 -1.890625 L 1.40625 0 L 0.59375 0 Z M 0.59375 -6.875 "/>
</symbol>
<symbol overflow="visible" id="2ad2efdf-7e8b-4342-a799-aa5b930eec68">
<path style="stroke:none;" d="M 0.828125 -5.78125 C 0.835938 -6.132812 0.898438 -6.390625 1.015625 -6.546875 C 1.210938 -6.835938 1.59375 -6.984375 2.15625 -6.984375 C 2.207031 -6.984375 2.257812 -6.976562 2.3125 -6.96875 C 2.375 -6.96875 2.4375 -6.96875 2.5 -6.96875 L 2.5 -6.1875 C 2.414062 -6.195312 2.351562 -6.203125 2.3125 -6.203125 C 2.28125 -6.203125 2.242188 -6.203125 2.203125 -6.203125 C 1.953125 -6.203125 1.800781 -6.132812 1.75 -6 C 1.695312 -5.875 1.671875 -5.539062 1.671875 -5 L 2.5 -5 L 2.5 -4.328125 L 1.65625 -4.328125 L 1.65625 0 L 0.828125 0 L 0.828125 -4.328125 L 0.125 -4.328125 L 0.125 -5 L 0.828125 -5 Z M 0.828125 -5.78125 "/>
</symbol>
<symbol overflow="visible" id="199876ba-5c73-4808-82bc-852e4523e196">
<path style="stroke:none;" d="M 0.703125 -6.875 L 2.046875 -6.875 L 4.015625 -1.0625 L 5.984375 -6.875 L 7.296875 -6.875 L 7.296875 0 L 6.421875 0 L 6.421875 -4.0625 C 6.421875 -4.195312 6.421875 -4.425781 6.421875 -4.75 C 6.429688 -5.082031 6.4375 -5.429688 6.4375 -5.796875 L 4.46875 0 L 3.546875 0 L 1.578125 -5.796875 L 1.578125 -5.59375 C 1.578125 -5.425781 1.578125 -5.171875 1.578125 -4.828125 C 1.585938 -4.484375 1.59375 -4.226562 1.59375 -4.0625 L 1.59375 0 L 0.703125 0 Z M 0.703125 -6.875 "/>
</symbol>
<symbol overflow="visible" id="d8a87d0f-233b-4133-8ed0-3a2ffc62538b">
<path style="stroke:none;" d="M 0.640625 -5.015625 L 1.4375 -5.015625 L 1.4375 -4.15625 C 1.507812 -4.320312 1.671875 -4.523438 1.921875 -4.765625 C 2.179688 -5.003906 2.476562 -5.125 2.8125 -5.125 C 2.820312 -5.125 2.847656 -5.125 2.890625 -5.125 C 2.929688 -5.125 2.992188 -5.117188 3.078125 -5.109375 L 3.078125 -4.21875 C 3.023438 -4.226562 2.976562 -4.234375 2.9375 -4.234375 C 2.894531 -4.234375 2.851562 -4.234375 2.8125 -4.234375 C 2.382812 -4.234375 2.054688 -4.097656 1.828125 -3.828125 C 1.597656 -3.554688 1.484375 -3.242188 1.484375 -2.890625 L 1.484375 0 L 0.640625 0 Z M 0.640625 -5.015625 "/>
</symbol>
<symbol overflow="visible" id="bcb6380e-9645-4b7a-ae69-ceed6ce9942c">
<path style="stroke:none;" d="M 0.625 -5.015625 L 1.453125 -5.015625 L 1.453125 -4.3125 C 1.648438 -4.550781 1.832031 -4.726562 2 -4.84375 C 2.269531 -5.03125 2.582031 -5.125 2.9375 -5.125 C 3.34375 -5.125 3.664062 -5.023438 3.90625 -4.828125 C 4.039062 -4.722656 4.164062 -4.5625 4.28125 -4.34375 C 4.46875 -4.601562 4.6875 -4.796875 4.9375 -4.921875 C 5.195312 -5.054688 5.484375 -5.125 5.796875 -5.125 C 6.472656 -5.125 6.929688 -4.882812 7.171875 -4.40625 C 7.304688 -4.132812 7.375 -3.78125 7.375 -3.34375 L 7.375 0 L 6.5 0 L 6.5 -3.484375 C 6.5 -3.816406 6.410156 -4.046875 6.234375 -4.171875 C 6.066406 -4.296875 5.863281 -4.359375 5.625 -4.359375 C 5.300781 -4.359375 5.019531 -4.25 4.78125 -4.03125 C 4.539062 -3.8125 4.421875 -3.441406 4.421875 -2.921875 L 4.421875 0 L 3.5625 0 L 3.5625 -3.28125 C 3.5625 -3.613281 3.519531 -3.859375 3.4375 -4.015625 C 3.3125 -4.253906 3.070312 -4.375 2.71875 -4.375 C 2.40625 -4.375 2.117188 -4.25 1.859375 -4 C 1.597656 -3.75 1.46875 -3.300781 1.46875 -2.65625 L 1.46875 0 L 0.625 0 Z M 0.625 -5.015625 "/>
</symbol>
<symbol overflow="visible" id="b654e85f-6de7-48c2-a6e9-cf9a7141306a">
<path style="stroke:none;" d="M 0.625 -5.015625 L 1.421875 -5.015625 L 1.421875 -4.3125 C 1.660156 -4.601562 1.910156 -4.8125 2.171875 -4.9375 C 2.441406 -5.0625 2.738281 -5.125 3.0625 -5.125 C 3.769531 -5.125 4.25 -4.878906 4.5 -4.390625 C 4.632812 -4.117188 4.703125 -3.726562 4.703125 -3.21875 L 4.703125 0 L 3.84375 0 L 3.84375 -3.15625 C 3.84375 -3.46875 3.800781 -3.71875 3.71875 -3.90625 C 3.5625 -4.21875 3.289062 -4.375 2.90625 -4.375 C 2.695312 -4.375 2.53125 -4.351562 2.40625 -4.3125 C 2.175781 -4.238281 1.972656 -4.097656 1.796875 -3.890625 C 1.660156 -3.734375 1.570312 -3.566406 1.53125 -3.390625 C 1.488281 -3.210938 1.46875 -2.957031 1.46875 -2.625 L 1.46875 0 L 0.625 0 Z M 2.59375 -5.140625 Z M 2.59375 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="f67bf3b9-7821-48e6-b8cd-d71c524fbb4c">
<path style="stroke:none;" d="M 3.375 -0.796875 C 3.6875 -0.796875 3.945312 -0.828125 4.15625 -0.890625 C 4.507812 -1.015625 4.804688 -1.25 5.046875 -1.59375 C 5.222656 -1.875 5.351562 -2.234375 5.4375 -2.671875 C 5.488281 -2.921875 5.515625 -3.160156 5.515625 -3.390625 C 5.515625 -4.242188 5.34375 -4.90625 5 -5.375 C 4.664062 -5.84375 4.117188 -6.078125 3.359375 -6.078125 L 1.703125 -6.078125 L 1.703125 -0.796875 Z M 0.765625 -6.875 L 3.5625 -6.875 C 4.507812 -6.875 5.242188 -6.539062 5.765625 -5.875 C 6.222656 -5.269531 6.453125 -4.492188 6.453125 -3.546875 C 6.453125 -2.816406 6.316406 -2.15625 6.046875 -1.5625 C 5.566406 -0.519531 4.734375 0 3.546875 0 L 0.765625 0 Z M 0.765625 -6.875 "/>
</symbol>
<symbol overflow="visible" id="531119b7-a67c-42b4-b42a-7d9033e40188">
<path style="stroke:none;" d="M 1.265625 -1.328125 C 1.265625 -1.085938 1.351562 -0.894531 1.53125 -0.75 C 1.707031 -0.613281 1.921875 -0.546875 2.171875 -0.546875 C 2.460938 -0.546875 2.75 -0.613281 3.03125 -0.75 C 3.5 -0.976562 3.734375 -1.351562 3.734375 -1.875 L 3.734375 -2.546875 C 3.628906 -2.484375 3.492188 -2.429688 3.328125 -2.390625 C 3.171875 -2.347656 3.015625 -2.316406 2.859375 -2.296875 L 2.34375 -2.234375 C 2.039062 -2.191406 1.8125 -2.125 1.65625 -2.03125 C 1.394531 -1.882812 1.265625 -1.648438 1.265625 -1.328125 Z M 3.3125 -3.046875 C 3.5 -3.066406 3.628906 -3.144531 3.703125 -3.28125 C 3.734375 -3.351562 3.75 -3.460938 3.75 -3.609375 C 3.75 -3.890625 3.644531 -4.09375 3.4375 -4.21875 C 3.238281 -4.351562 2.945312 -4.421875 2.5625 -4.421875 C 2.125 -4.421875 1.8125 -4.304688 1.625 -4.078125 C 1.519531 -3.941406 1.453125 -3.742188 1.421875 -3.484375 L 0.640625 -3.484375 C 0.660156 -4.097656 0.863281 -4.523438 1.25 -4.765625 C 1.632812 -5.015625 2.078125 -5.140625 2.578125 -5.140625 C 3.171875 -5.140625 3.65625 -5.023438 4.03125 -4.796875 C 4.394531 -4.578125 4.578125 -4.226562 4.578125 -3.75 L 4.578125 -0.859375 C 4.578125 -0.773438 4.59375 -0.707031 4.625 -0.65625 C 4.664062 -0.601562 4.742188 -0.578125 4.859375 -0.578125 C 4.890625 -0.578125 4.925781 -0.578125 4.96875 -0.578125 C 5.019531 -0.578125 5.070312 -0.582031 5.125 -0.59375 L 5.125 0.015625 C 5 0.0546875 4.898438 0.0820312 4.828125 0.09375 C 4.765625 0.101562 4.671875 0.109375 4.546875 0.109375 C 4.253906 0.109375 4.046875 0.00390625 3.921875 -0.203125 C 3.847656 -0.304688 3.796875 -0.460938 3.765625 -0.671875 C 3.597656 -0.441406 3.351562 -0.242188 3.03125 -0.078125 C 2.707031 0.0859375 2.351562 0.171875 1.96875 0.171875 C 1.5 0.171875 1.117188 0.03125 0.828125 -0.25 C 0.535156 -0.53125 0.390625 -0.882812 0.390625 -1.3125 C 0.390625 -1.78125 0.53125 -2.140625 0.8125 -2.390625 C 1.101562 -2.648438 1.488281 -2.8125 1.96875 -2.875 Z M 2.609375 -5.140625 Z M 2.609375 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="851e063c-cf48-45e9-b5ac-9ff18a239606">
<path style="stroke:none;" d="M 2.734375 -0.5625 C 3.128906 -0.5625 3.457031 -0.726562 3.71875 -1.0625 C 3.976562 -1.394531 4.109375 -1.882812 4.109375 -2.53125 C 4.109375 -2.9375 4.050781 -3.28125 3.9375 -3.5625 C 3.71875 -4.125 3.316406 -4.40625 2.734375 -4.40625 C 2.148438 -4.40625 1.75 -4.109375 1.53125 -3.515625 C 1.414062 -3.203125 1.359375 -2.804688 1.359375 -2.328125 C 1.359375 -1.941406 1.414062 -1.613281 1.53125 -1.34375 C 1.75 -0.820312 2.148438 -0.5625 2.734375 -0.5625 Z M 0.546875 -5 L 1.375 -5 L 1.375 -4.328125 C 1.539062 -4.554688 1.722656 -4.734375 1.921875 -4.859375 C 2.210938 -5.046875 2.546875 -5.140625 2.921875 -5.140625 C 3.492188 -5.140625 3.976562 -4.921875 4.375 -4.484375 C 4.769531 -4.046875 4.96875 -3.425781 4.96875 -2.625 C 4.96875 -1.53125 4.679688 -0.75 4.109375 -0.28125 C 3.742188 0.0195312 3.320312 0.171875 2.84375 0.171875 C 2.46875 0.171875 2.148438 0.0859375 1.890625 -0.078125 C 1.742188 -0.171875 1.578125 -0.332031 1.390625 -0.5625 L 1.390625 2 L 0.546875 2 Z M 0.546875 -5 "/>
</symbol>
<symbol overflow="visible" id="ad952859-b6b9-4155-a941-7bfb27dfdb69">
<path style="stroke:none;" d="M 1.15625 -2.453125 C 1.15625 -1.910156 1.269531 -1.457031 1.5 -1.09375 C 1.726562 -0.738281 2.09375 -0.5625 2.59375 -0.5625 C 2.976562 -0.5625 3.296875 -0.726562 3.546875 -1.0625 C 3.804688 -1.394531 3.9375 -1.875 3.9375 -2.5 C 3.9375 -3.132812 3.804688 -3.601562 3.546875 -3.90625 C 3.285156 -4.21875 2.960938 -4.375 2.578125 -4.375 C 2.148438 -4.375 1.804688 -4.207031 1.546875 -3.875 C 1.285156 -3.550781 1.15625 -3.078125 1.15625 -2.453125 Z M 2.421875 -5.109375 C 2.804688 -5.109375 3.128906 -5.023438 3.390625 -4.859375 C 3.535156 -4.765625 3.703125 -4.601562 3.890625 -4.375 L 3.890625 -6.90625 L 4.703125 -6.90625 L 4.703125 0 L 3.953125 0 L 3.953125 -0.703125 C 3.753906 -0.390625 3.519531 -0.164062 3.25 -0.03125 C 2.976562 0.101562 2.671875 0.171875 2.328125 0.171875 C 1.765625 0.171875 1.28125 -0.0625 0.875 -0.53125 C 0.46875 -1 0.265625 -1.625 0.265625 -2.40625 C 0.265625 -3.132812 0.445312 -3.765625 0.8125 -4.296875 C 1.1875 -4.835938 1.722656 -5.109375 2.421875 -5.109375 Z M 2.421875 -5.109375 "/>
</symbol>
<symbol overflow="visible" id="fa132e7d-79e6-4eed-8a5a-28620a305be4">
<path style="stroke:none;" d="M 0.75 -6.875 L 1.703125 -6.875 L 1.703125 -4.03125 L 5.28125 -4.03125 L 5.28125 -6.875 L 6.21875 -6.875 L 6.21875 0 L 5.28125 0 L 5.28125 -3.21875 L 1.703125 -3.21875 L 1.703125 0 L 0.75 0 Z M 0.75 -6.875 "/>
</symbol>
<symbol overflow="visible" id="579b9a0b-5273-487f-93b1-db72e61accbe">
<path style="stroke:none;" d="M 0.640625 -6.875 L 1.484375 -6.875 L 1.484375 0 L 0.640625 0 Z M 0.640625 -6.875 "/>
</symbol>
<symbol overflow="visible" id="bc5c6727-943d-4b62-b5fc-9f115d9e6364">
<path style="stroke:none;" d="M 3.75 -5.015625 L 4.6875 -5.015625 C 4.5625 -4.691406 4.296875 -3.957031 3.890625 -2.8125 C 3.585938 -1.945312 3.332031 -1.242188 3.125 -0.703125 C 2.632812 0.578125 2.289062 1.359375 2.09375 1.640625 C 1.894531 1.921875 1.550781 2.0625 1.0625 2.0625 C 0.945312 2.0625 0.851562 2.054688 0.78125 2.046875 C 0.71875 2.035156 0.640625 2.015625 0.546875 1.984375 L 0.546875 1.21875 C 0.691406 1.257812 0.796875 1.285156 0.859375 1.296875 C 0.929688 1.304688 0.992188 1.3125 1.046875 1.3125 C 1.203125 1.3125 1.316406 1.285156 1.390625 1.234375 C 1.460938 1.179688 1.523438 1.117188 1.578125 1.046875 C 1.585938 1.015625 1.640625 0.882812 1.734375 0.65625 C 1.835938 0.425781 1.910156 0.253906 1.953125 0.140625 L 0.09375 -5.015625 L 1.046875 -5.015625 L 2.40625 -0.9375 Z M 2.390625 -5.140625 Z M 2.390625 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="10f725ed-05e0-4aa2-aa21-9b1284cd2174">
<path style="stroke:none;" d="M 0.625 -5 L 1.46875 -5 L 1.46875 0 L 0.625 0 Z M 0.625 -6.875 L 1.46875 -6.875 L 1.46875 -5.921875 L 0.625 -5.921875 Z M 0.625 -6.875 "/>
</symbol>
<symbol overflow="visible" id="415713a2-3797-4b89-8c45-4441008c9ad8">
<path style="stroke:none;" d="M 0.546875 -6.90625 L 1.375 -6.90625 L 1.375 -4.40625 C 1.5625 -4.644531 1.78125 -4.828125 2.03125 -4.953125 C 2.289062 -5.078125 2.566406 -5.140625 2.859375 -5.140625 C 3.484375 -5.140625 3.988281 -4.925781 4.375 -4.5 C 4.769531 -4.070312 4.96875 -3.441406 4.96875 -2.609375 C 4.96875 -1.816406 4.773438 -1.15625 4.390625 -0.625 C 4.003906 -0.101562 3.472656 0.15625 2.796875 0.15625 C 2.410156 0.15625 2.085938 0.0664062 1.828125 -0.109375 C 1.671875 -0.222656 1.503906 -0.398438 1.328125 -0.640625 L 1.328125 0 L 0.546875 0 Z M 2.75 -0.578125 C 3.195312 -0.578125 3.535156 -0.757812 3.765625 -1.125 C 3.992188 -1.488281 4.109375 -1.96875 4.109375 -2.5625 C 4.109375 -3.09375 3.992188 -3.53125 3.765625 -3.875 C 3.535156 -4.21875 3.203125 -4.390625 2.765625 -4.390625 C 2.378906 -4.390625 2.039062 -4.25 1.75 -3.96875 C 1.46875 -3.6875 1.328125 -3.21875 1.328125 -2.5625 C 1.328125 -2.09375 1.382812 -1.710938 1.5 -1.421875 C 1.726562 -0.859375 2.144531 -0.578125 2.75 -0.578125 Z M 2.75 -0.578125 "/>
</symbol>
<symbol overflow="visible" id="7407b793-01e4-4622-98dc-d83ccb168a21">
<path style="stroke:none;" d="M 4.109375 -2.046875 C 4.109375 -1.472656 4.019531 -1.023438 3.84375 -0.703125 C 3.53125 -0.109375 2.925781 0.1875 2.03125 0.1875 C 1.519531 0.1875 1.078125 0.046875 0.703125 -0.234375 C 0.335938 -0.515625 0.15625 -1.015625 0.15625 -1.734375 L 0.15625 -2.21875 L 1.046875 -2.21875 L 1.046875 -1.734375 C 1.046875 -1.359375 1.128906 -1.070312 1.296875 -0.875 C 1.460938 -0.6875 1.722656 -0.59375 2.078125 -0.59375 C 2.566406 -0.59375 2.890625 -0.765625 3.046875 -1.109375 C 3.140625 -1.316406 3.1875 -1.710938 3.1875 -2.296875 L 3.1875 -6.875 L 4.109375 -6.875 Z M 4.109375 -2.046875 "/>
</symbol>
<symbol overflow="visible" id="56a5e09d-e92f-4a2a-ad9b-6d516cb57308">
<path style="stroke:none;" d="M 1 -5.015625 L 1.96875 -1.0625 L 2.953125 -5.015625 L 3.890625 -5.015625 L 4.875 -1.09375 L 5.90625 -5.015625 L 6.75 -5.015625 L 5.296875 0 L 4.421875 0 L 3.390625 -3.890625 L 2.40625 0 L 1.53125 0 L 0.078125 -5.015625 Z M 1 -5.015625 "/>
</symbol>
<symbol overflow="visible" id="009522d0-1240-4848-aaed-ccccd19575b5">
<path style="stroke:none;" d="M 1.125 -1.578125 C 1.144531 -1.296875 1.210938 -1.078125 1.328125 -0.921875 C 1.546875 -0.648438 1.914062 -0.515625 2.4375 -0.515625 C 2.75 -0.515625 3.019531 -0.582031 3.25 -0.71875 C 3.488281 -0.851562 3.609375 -1.066406 3.609375 -1.359375 C 3.609375 -1.566406 3.515625 -1.726562 3.328125 -1.84375 C 3.203125 -1.914062 2.960938 -1.992188 2.609375 -2.078125 L 1.9375 -2.25 C 1.507812 -2.351562 1.195312 -2.472656 1 -2.609375 C 0.632812 -2.835938 0.453125 -3.15625 0.453125 -3.5625 C 0.453125 -4.03125 0.617188 -4.410156 0.953125 -4.703125 C 1.296875 -4.992188 1.757812 -5.140625 2.34375 -5.140625 C 3.09375 -5.140625 3.640625 -4.921875 3.984375 -4.484375 C 4.191406 -4.203125 4.289062 -3.898438 4.28125 -3.578125 L 3.484375 -3.578125 C 3.472656 -3.765625 3.40625 -3.9375 3.28125 -4.09375 C 3.09375 -4.3125 2.757812 -4.421875 2.28125 -4.421875 C 1.957031 -4.421875 1.710938 -4.359375 1.546875 -4.234375 C 1.390625 -4.117188 1.3125 -3.960938 1.3125 -3.765625 C 1.3125 -3.546875 1.414062 -3.367188 1.625 -3.234375 C 1.75 -3.160156 1.9375 -3.09375 2.1875 -3.03125 L 2.734375 -2.890625 C 3.347656 -2.742188 3.753906 -2.601562 3.953125 -2.46875 C 4.285156 -2.25 4.453125 -1.910156 4.453125 -1.453125 C 4.453125 -1.003906 4.28125 -0.617188 3.9375 -0.296875 C 3.601562 0.0234375 3.085938 0.1875 2.390625 0.1875 C 1.648438 0.1875 1.125 0.0195312 0.8125 -0.3125 C 0.5 -0.65625 0.332031 -1.078125 0.3125 -1.578125 Z M 2.359375 -5.140625 Z M 2.359375 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="80e705cf-5065-485f-ae37-bec7e29e07bc">
<path style="stroke:none;" d="M 1.34375 -2.21875 C 1.363281 -1.832031 1.453125 -1.515625 1.609375 -1.265625 C 1.921875 -0.804688 2.46875 -0.578125 3.25 -0.578125 C 3.601562 -0.578125 3.921875 -0.628906 4.203125 -0.734375 C 4.765625 -0.929688 5.046875 -1.28125 5.046875 -1.78125 C 5.046875 -2.15625 4.925781 -2.421875 4.6875 -2.578125 C 4.445312 -2.734375 4.078125 -2.867188 3.578125 -2.984375 L 2.640625 -3.1875 C 2.035156 -3.332031 1.601562 -3.488281 1.34375 -3.65625 C 0.90625 -3.9375 0.6875 -4.363281 0.6875 -4.9375 C 0.6875 -5.550781 0.898438 -6.054688 1.328125 -6.453125 C 1.765625 -6.859375 2.375 -7.0625 3.15625 -7.0625 C 3.875 -7.0625 4.484375 -6.882812 4.984375 -6.53125 C 5.492188 -6.1875 5.75 -5.628906 5.75 -4.859375 L 4.875 -4.859375 C 4.820312 -5.234375 4.722656 -5.515625 4.578125 -5.703125 C 4.285156 -6.066406 3.800781 -6.25 3.125 -6.25 C 2.570312 -6.25 2.175781 -6.132812 1.9375 -5.90625 C 1.695312 -5.675781 1.578125 -5.40625 1.578125 -5.09375 C 1.578125 -4.757812 1.71875 -4.515625 2 -4.359375 C 2.1875 -4.253906 2.601562 -4.128906 3.25 -3.984375 L 4.21875 -3.765625 C 4.675781 -3.660156 5.035156 -3.515625 5.296875 -3.328125 C 5.734375 -3.003906 5.953125 -2.535156 5.953125 -1.921875 C 5.953125 -1.160156 5.671875 -0.613281 5.109375 -0.28125 C 4.554688 0.0390625 3.914062 0.203125 3.1875 0.203125 C 2.332031 0.203125 1.660156 -0.015625 1.171875 -0.453125 C 0.691406 -0.890625 0.457031 -1.476562 0.46875 -2.21875 Z M 3.21875 -7.0625 Z M 3.21875 -7.0625 "/>
</symbol>
<symbol overflow="visible" id="3c724632-d6f8-4f30-beaa-4b06a36c92e6">
<path style="stroke:none;" d="M 2.546875 -5.15625 C 3.117188 -5.15625 3.582031 -5.019531 3.9375 -4.75 C 4.289062 -4.476562 4.503906 -4.003906 4.578125 -3.328125 L 3.75 -3.328125 C 3.695312 -3.640625 3.582031 -3.894531 3.40625 -4.09375 C 3.226562 -4.300781 2.941406 -4.40625 2.546875 -4.40625 C 2.015625 -4.40625 1.632812 -4.144531 1.40625 -3.625 C 1.25 -3.28125 1.171875 -2.859375 1.171875 -2.359375 C 1.171875 -1.859375 1.273438 -1.4375 1.484375 -1.09375 C 1.703125 -0.75 2.039062 -0.578125 2.5 -0.578125 C 2.84375 -0.578125 3.117188 -0.679688 3.328125 -0.890625 C 3.535156 -1.109375 3.675781 -1.40625 3.75 -1.78125 L 4.578125 -1.78125 C 4.484375 -1.113281 4.25 -0.625 3.875 -0.3125 C 3.5 -0.0078125 3.019531 0.140625 2.4375 0.140625 C 1.78125 0.140625 1.253906 -0.0976562 0.859375 -0.578125 C 0.472656 -1.054688 0.28125 -1.65625 0.28125 -2.375 C 0.28125 -3.25 0.492188 -3.929688 0.921875 -4.421875 C 1.347656 -4.910156 1.890625 -5.15625 2.546875 -5.15625 Z M 2.421875 -5.140625 Z M 2.421875 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="fd5ea26b-cdbc-4d5a-b1cd-e24197506908">
<path style="stroke:none;" d="M 0.78125 -6.421875 L 1.640625 -6.421875 L 1.640625 -5.015625 L 2.4375 -5.015625 L 2.4375 -4.328125 L 1.640625 -4.328125 L 1.640625 -1.046875 C 1.640625 -0.878906 1.695312 -0.765625 1.8125 -0.703125 C 1.882812 -0.671875 1.992188 -0.65625 2.140625 -0.65625 C 2.179688 -0.65625 2.222656 -0.65625 2.265625 -0.65625 C 2.316406 -0.65625 2.375 -0.660156 2.4375 -0.671875 L 2.4375 0 C 2.34375 0.03125 2.242188 0.0507812 2.140625 0.0625 C 2.035156 0.0703125 1.921875 0.078125 1.796875 0.078125 C 1.398438 0.078125 1.128906 -0.0195312 0.984375 -0.21875 C 0.847656 -0.425781 0.78125 -0.6875 0.78125 -1 L 0.78125 -4.328125 L 0.109375 -4.328125 L 0.109375 -5.015625 L 0.78125 -5.015625 Z M 0.78125 -6.421875 "/>
</symbol>
<symbol overflow="visible" id="e95eb0e0-0f65-41cd-9477-f94188a8afd3">
<path style="stroke:none;" d="M 1.46875 -5.015625 L 1.46875 -1.6875 C 1.46875 -1.425781 1.503906 -1.21875 1.578125 -1.0625 C 1.734375 -0.757812 2.015625 -0.609375 2.421875 -0.609375 C 3.003906 -0.609375 3.40625 -0.867188 3.625 -1.390625 C 3.738281 -1.671875 3.796875 -2.054688 3.796875 -2.546875 L 3.796875 -5.015625 L 4.640625 -5.015625 L 4.640625 0 L 3.84375 0 L 3.84375 -0.734375 C 3.738281 -0.546875 3.601562 -0.382812 3.4375 -0.25 C 3.113281 0.0078125 2.722656 0.140625 2.265625 0.140625 C 1.554688 0.140625 1.070312 -0.0976562 0.8125 -0.578125 C 0.664062 -0.835938 0.59375 -1.179688 0.59375 -1.609375 L 0.59375 -5.015625 Z M 2.625 -5.140625 Z M 2.625 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="9b2dfd8c-86bb-4b5e-84e3-5a26a7c6038c">
<path style="stroke:none;" d="M 0.734375 -6.875 L 1.640625 -6.875 L 1.640625 -3.53125 L 5 -6.875 L 6.28125 -6.875 L 3.421875 -4.109375 L 6.359375 0 L 5.140625 0 L 2.734375 -3.453125 L 1.640625 -2.40625 L 1.640625 0 L 0.734375 0 Z M 0.734375 -6.875 "/>
</symbol>
<symbol overflow="visible" id="6bac1475-bbbf-4864-a9d6-752c1a1c9572">
<path style="stroke:none;" d="M 1.28125 -6.875 L 3.25 -1.015625 L 5.203125 -6.875 L 6.25 -6.875 L 3.734375 0 L 2.75 0 L 0.25 -6.875 Z M 1.28125 -6.875 "/>
</symbol>
<symbol overflow="visible" id="95f46cb7-fc74-4dab-a1b2-13e7afe01b10">
<path style="stroke:none;" d="M 2.59375 -6.703125 C 3.457031 -6.703125 4.085938 -6.347656 4.484375 -5.640625 C 4.773438 -5.085938 4.921875 -4.328125 4.921875 -3.359375 C 4.921875 -2.453125 4.785156 -1.695312 4.515625 -1.09375 C 4.128906 -0.238281 3.488281 0.1875 2.59375 0.1875 C 1.78125 0.1875 1.179688 -0.160156 0.796875 -0.859375 C 0.460938 -1.453125 0.296875 -2.238281 0.296875 -3.21875 C 0.296875 -3.976562 0.394531 -4.632812 0.59375 -5.1875 C 0.957031 -6.195312 1.625 -6.703125 2.59375 -6.703125 Z M 2.578125 -0.578125 C 3.015625 -0.578125 3.363281 -0.769531 3.625 -1.15625 C 3.882812 -1.550781 4.015625 -2.273438 4.015625 -3.328125 C 4.015625 -4.085938 3.921875 -4.710938 3.734375 -5.203125 C 3.546875 -5.703125 3.179688 -5.953125 2.640625 -5.953125 C 2.148438 -5.953125 1.789062 -5.71875 1.5625 -5.25 C 1.332031 -4.78125 1.21875 -4.09375 1.21875 -3.1875 C 1.21875 -2.5 1.289062 -1.945312 1.4375 -1.53125 C 1.65625 -0.894531 2.035156 -0.578125 2.578125 -0.578125 Z M 2.578125 -0.578125 "/>
</symbol>
<symbol overflow="visible" id="1cd16fa3-8228-4a72-b55d-5ea659b6f7a8">
<path style="stroke:none;" d="M 0.921875 -4.75 L 0.921875 -5.390625 C 1.523438 -5.453125 1.945312 -5.550781 2.1875 -5.6875 C 2.425781 -5.832031 2.609375 -6.164062 2.734375 -6.6875 L 3.390625 -6.6875 L 3.390625 0 L 2.5 0 L 2.5 -4.75 Z M 0.921875 -4.75 "/>
</symbol>
<symbol overflow="visible" id="35e2073c-8bf1-40eb-83fe-da4aa2f54d5c">
<path style="stroke:none;" d="M 0.296875 0 C 0.328125 -0.570312 0.445312 -1.070312 0.65625 -1.5 C 0.863281 -1.9375 1.269531 -2.328125 1.875 -2.671875 L 2.765625 -3.1875 C 3.171875 -3.425781 3.457031 -3.628906 3.625 -3.796875 C 3.875 -4.054688 4 -4.351562 4 -4.6875 C 4 -5.070312 3.878906 -5.378906 3.640625 -5.609375 C 3.410156 -5.835938 3.101562 -5.953125 2.71875 -5.953125 C 2.132812 -5.953125 1.734375 -5.734375 1.515625 -5.296875 C 1.398438 -5.066406 1.335938 -4.742188 1.328125 -4.328125 L 0.46875 -4.328125 C 0.476562 -4.910156 0.582031 -5.382812 0.78125 -5.75 C 1.144531 -6.40625 1.789062 -6.734375 2.71875 -6.734375 C 3.488281 -6.734375 4.050781 -6.523438 4.40625 -6.109375 C 4.757812 -5.691406 4.9375 -5.226562 4.9375 -4.71875 C 4.9375 -4.1875 4.75 -3.726562 4.375 -3.34375 C 4.15625 -3.125 3.757812 -2.851562 3.1875 -2.53125 L 2.546875 -2.1875 C 2.242188 -2.019531 2.003906 -1.859375 1.828125 -1.703125 C 1.515625 -1.429688 1.316406 -1.128906 1.234375 -0.796875 L 4.90625 -0.796875 L 4.90625 0 Z M 0.296875 0 "/>
</symbol>
<symbol overflow="visible" id="23a52dda-adbd-49a5-93ce-b0af9863fb12">
<path style="stroke:none;" d="M 2.484375 0.1875 C 1.691406 0.1875 1.117188 -0.03125 0.765625 -0.46875 C 0.410156 -0.90625 0.234375 -1.4375 0.234375 -2.0625 L 1.109375 -2.0625 C 1.148438 -1.625 1.234375 -1.304688 1.359375 -1.109375 C 1.578125 -0.753906 1.96875 -0.578125 2.53125 -0.578125 C 2.976562 -0.578125 3.335938 -0.695312 3.609375 -0.9375 C 3.878906 -1.175781 4.015625 -1.484375 4.015625 -1.859375 C 4.015625 -2.316406 3.867188 -2.640625 3.578125 -2.828125 C 3.296875 -3.015625 2.90625 -3.109375 2.40625 -3.109375 C 2.351562 -3.109375 2.296875 -3.101562 2.234375 -3.09375 C 2.179688 -3.09375 2.125 -3.09375 2.0625 -3.09375 L 2.0625 -3.84375 C 2.144531 -3.832031 2.21875 -3.820312 2.28125 -3.8125 C 2.34375 -3.8125 2.40625 -3.8125 2.46875 -3.8125 C 2.789062 -3.8125 3.050781 -3.863281 3.25 -3.96875 C 3.601562 -4.144531 3.78125 -4.457031 3.78125 -4.90625 C 3.78125 -5.238281 3.660156 -5.492188 3.421875 -5.671875 C 3.191406 -5.859375 2.914062 -5.953125 2.59375 -5.953125 C 2.03125 -5.953125 1.644531 -5.765625 1.4375 -5.390625 C 1.3125 -5.179688 1.242188 -4.882812 1.234375 -4.5 L 0.390625 -4.5 C 0.390625 -5 0.492188 -5.425781 0.703125 -5.78125 C 1.046875 -6.40625 1.648438 -6.71875 2.515625 -6.71875 C 3.191406 -6.71875 3.71875 -6.5625 4.09375 -6.25 C 4.46875 -5.945312 4.65625 -5.507812 4.65625 -4.9375 C 4.65625 -4.519531 4.546875 -4.1875 4.328125 -3.9375 C 4.191406 -3.78125 4.015625 -3.65625 3.796875 -3.5625 C 4.148438 -3.46875 4.425781 -3.28125 4.625 -3 C 4.820312 -2.71875 4.921875 -2.378906 4.921875 -1.984375 C 4.921875 -1.347656 4.707031 -0.828125 4.28125 -0.421875 C 3.863281 -0.015625 3.265625 0.1875 2.484375 0.1875 Z M 2.484375 0.1875 "/>
</symbol>
<symbol overflow="visible" id="a12f4708-4d27-4672-a9b8-5299568f71d2">
<path style="stroke:none;" d="M 0.46875 0 L 0.46875 -10.328125 L 8.671875 -10.328125 L 8.671875 0 Z M 7.375 -1.296875 L 7.375 -9.046875 L 1.765625 -9.046875 L 1.765625 -1.296875 Z M 7.375 -1.296875 "/>
</symbol>
<symbol overflow="visible" id="6ebe081a-2f9c-4b57-a466-89294c534270">
<path style="stroke:none;" d="M 2.015625 -3.328125 C 2.046875 -2.742188 2.179688 -2.269531 2.421875 -1.90625 C 2.890625 -1.21875 3.707031 -0.875 4.875 -0.875 C 5.40625 -0.875 5.882812 -0.953125 6.3125 -1.109375 C 7.144531 -1.398438 7.5625 -1.921875 7.5625 -2.671875 C 7.5625 -3.234375 7.390625 -3.632812 7.046875 -3.875 C 6.679688 -4.101562 6.117188 -4.304688 5.359375 -4.484375 L 3.96875 -4.796875 C 3.050781 -5.003906 2.40625 -5.234375 2.03125 -5.484375 C 1.375 -5.910156 1.046875 -6.554688 1.046875 -7.421875 C 1.046875 -8.347656 1.363281 -9.109375 2 -9.703125 C 2.644531 -10.296875 3.554688 -10.59375 4.734375 -10.59375 C 5.816406 -10.59375 6.734375 -10.332031 7.484375 -9.8125 C 8.242188 -9.289062 8.625 -8.453125 8.625 -7.296875 L 7.3125 -7.296875 C 7.238281 -7.847656 7.085938 -8.273438 6.859375 -8.578125 C 6.429688 -9.117188 5.707031 -9.390625 4.6875 -9.390625 C 3.863281 -9.390625 3.269531 -9.210938 2.90625 -8.859375 C 2.550781 -8.515625 2.375 -8.113281 2.375 -7.65625 C 2.375 -7.144531 2.582031 -6.773438 3 -6.546875 C 3.28125 -6.390625 3.90625 -6.203125 4.875 -5.984375 L 6.328125 -5.65625 C 7.023438 -5.488281 7.566406 -5.269531 7.953125 -5 C 8.609375 -4.507812 8.9375 -3.804688 8.9375 -2.890625 C 8.9375 -1.742188 8.519531 -0.925781 7.6875 -0.4375 C 6.851562 0.0507812 5.882812 0.296875 4.78125 0.296875 C 3.5 0.296875 2.492188 -0.03125 1.765625 -0.6875 C 1.035156 -1.332031 0.679688 -2.210938 0.703125 -3.328125 Z M 4.84375 -10.609375 Z M 4.84375 -10.609375 "/>
</symbol>
<symbol overflow="visible" id="c2d3e914-7845-4f38-b529-bfe2b590ee4e">
<path style="stroke:none;" d="M 4.0625 -7.703125 C 4.601562 -7.703125 5.125 -7.578125 5.625 -7.328125 C 6.125 -7.078125 6.503906 -6.753906 6.765625 -6.359375 C 7.015625 -5.972656 7.1875 -5.523438 7.28125 -5.015625 C 7.351562 -4.671875 7.390625 -4.117188 7.390625 -3.359375 L 1.859375 -3.359375 C 1.890625 -2.597656 2.070312 -1.984375 2.40625 -1.515625 C 2.738281 -1.054688 3.257812 -0.828125 3.96875 -0.828125 C 4.632812 -0.828125 5.164062 -1.046875 5.5625 -1.484375 C 5.78125 -1.734375 5.9375 -2.023438 6.03125 -2.359375 L 7.28125 -2.359375 C 7.25 -2.085938 7.140625 -1.78125 6.953125 -1.4375 C 6.765625 -1.09375 6.554688 -0.816406 6.328125 -0.609375 C 5.941406 -0.234375 5.46875 0.0195312 4.90625 0.15625 C 4.601562 0.226562 4.257812 0.265625 3.875 0.265625 C 2.9375 0.265625 2.140625 -0.0703125 1.484375 -0.75 C 0.828125 -1.4375 0.5 -2.394531 0.5 -3.625 C 0.5 -4.832031 0.828125 -5.8125 1.484375 -6.5625 C 2.140625 -7.320312 3 -7.703125 4.0625 -7.703125 Z M 6.078125 -4.375 C 6.023438 -4.914062 5.90625 -5.351562 5.71875 -5.6875 C 5.375 -6.289062 4.796875 -6.59375 3.984375 -6.59375 C 3.398438 -6.59375 2.910156 -6.382812 2.515625 -5.96875 C 2.128906 -5.550781 1.925781 -5.019531 1.90625 -4.375 Z M 3.953125 -7.71875 Z M 3.953125 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="84889efc-9850-4a0b-ab79-3ae9aa0e0e4d">
<path style="stroke:none;" d="M 0.921875 -7.53125 L 2.125 -7.53125 L 2.125 -6.46875 C 2.488281 -6.90625 2.867188 -7.21875 3.265625 -7.40625 C 3.660156 -7.601562 4.101562 -7.703125 4.59375 -7.703125 C 5.664062 -7.703125 6.390625 -7.328125 6.765625 -6.578125 C 6.960938 -6.171875 7.0625 -5.585938 7.0625 -4.828125 L 7.0625 0 L 5.78125 0 L 5.78125 -4.75 C 5.78125 -5.207031 5.710938 -5.578125 5.578125 -5.859375 C 5.347656 -6.328125 4.941406 -6.5625 4.359375 -6.5625 C 4.054688 -6.5625 3.804688 -6.53125 3.609375 -6.46875 C 3.265625 -6.363281 2.960938 -6.160156 2.703125 -5.859375 C 2.492188 -5.609375 2.351562 -5.347656 2.28125 -5.078125 C 2.21875 -4.816406 2.1875 -4.441406 2.1875 -3.953125 L 2.1875 0 L 0.921875 0 Z M 3.90625 -7.71875 Z M 3.90625 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="5ca03971-6326-4d6b-afce-60cf7e805e2e">
<path style="stroke:none;" d="M 1.1875 -9.640625 L 2.46875 -9.640625 L 2.46875 -7.53125 L 3.671875 -7.53125 L 3.671875 -6.5 L 2.46875 -6.5 L 2.46875 -1.578125 C 2.46875 -1.316406 2.554688 -1.144531 2.734375 -1.0625 C 2.828125 -1.007812 2.988281 -0.984375 3.21875 -0.984375 C 3.28125 -0.984375 3.347656 -0.984375 3.421875 -0.984375 C 3.492188 -0.984375 3.578125 -0.988281 3.671875 -1 L 3.671875 0 C 3.523438 0.0390625 3.367188 0.0703125 3.203125 0.09375 C 3.046875 0.113281 2.878906 0.125 2.703125 0.125 C 2.109375 0.125 1.707031 -0.0234375 1.5 -0.328125 C 1.289062 -0.628906 1.1875 -1.023438 1.1875 -1.515625 L 1.1875 -6.5 L 0.15625 -6.5 L 0.15625 -7.53125 L 1.1875 -7.53125 Z M 1.1875 -9.640625 "/>
</symbol>
<symbol overflow="visible" id="842e014b-de28-48d3-bd61-bb1560c95b8b">
<path style="stroke:none;" d="M 3.828125 -7.75 C 4.679688 -7.75 5.375 -7.539062 5.90625 -7.125 C 6.4375 -6.71875 6.753906 -6.007812 6.859375 -5 L 5.640625 -5 C 5.554688 -5.46875 5.378906 -5.851562 5.109375 -6.15625 C 4.847656 -6.46875 4.421875 -6.625 3.828125 -6.625 C 3.023438 -6.625 2.453125 -6.226562 2.109375 -5.4375 C 1.878906 -4.925781 1.765625 -4.296875 1.765625 -3.546875 C 1.765625 -2.785156 1.921875 -2.144531 2.234375 -1.625 C 2.554688 -1.113281 3.0625 -0.859375 3.75 -0.859375 C 4.269531 -0.859375 4.679688 -1.019531 4.984375 -1.34375 C 5.296875 -1.664062 5.515625 -2.109375 5.640625 -2.671875 L 6.859375 -2.671875 C 6.722656 -1.671875 6.375 -0.9375 5.8125 -0.46875 C 5.25 -0.0078125 4.53125 0.21875 3.65625 0.21875 C 2.664062 0.21875 1.878906 -0.140625 1.296875 -0.859375 C 0.710938 -1.578125 0.421875 -2.476562 0.421875 -3.5625 C 0.421875 -4.882812 0.738281 -5.910156 1.375 -6.640625 C 2.019531 -7.378906 2.835938 -7.75 3.828125 -7.75 Z M 3.640625 -7.71875 Z M 3.640625 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="3704ae25-ae68-41ef-9bcc-85057db8231c">
<path style="stroke:none;" d="M 1.6875 -2.359375 C 1.71875 -1.941406 1.820312 -1.617188 2 -1.390625 C 2.3125 -0.984375 2.863281 -0.78125 3.65625 -0.78125 C 4.125 -0.78125 4.535156 -0.878906 4.890625 -1.078125 C 5.253906 -1.285156 5.4375 -1.601562 5.4375 -2.03125 C 5.4375 -2.351562 5.289062 -2.597656 5 -2.765625 C 4.820312 -2.867188 4.460938 -2.988281 3.921875 -3.125 L 2.90625 -3.390625 C 2.269531 -3.546875 1.796875 -3.722656 1.484375 -3.921875 C 0.941406 -4.265625 0.671875 -4.738281 0.671875 -5.34375 C 0.671875 -6.050781 0.925781 -6.625 1.4375 -7.0625 C 1.957031 -7.507812 2.648438 -7.734375 3.515625 -7.734375 C 4.648438 -7.734375 5.46875 -7.398438 5.96875 -6.734375 C 6.28125 -6.304688 6.429688 -5.847656 6.421875 -5.359375 L 5.234375 -5.359375 C 5.210938 -5.648438 5.113281 -5.910156 4.9375 -6.140625 C 4.644531 -6.472656 4.140625 -6.640625 3.421875 -6.640625 C 2.941406 -6.640625 2.578125 -6.546875 2.328125 -6.359375 C 2.085938 -6.179688 1.96875 -5.945312 1.96875 -5.65625 C 1.96875 -5.320312 2.128906 -5.054688 2.453125 -4.859375 C 2.640625 -4.742188 2.914062 -4.640625 3.28125 -4.546875 L 4.109375 -4.34375 C 5.023438 -4.125 5.632812 -3.910156 5.9375 -3.703125 C 6.4375 -3.378906 6.6875 -2.875 6.6875 -2.1875 C 6.6875 -1.507812 6.429688 -0.925781 5.921875 -0.4375 C 5.410156 0.0390625 4.632812 0.28125 3.59375 0.28125 C 2.46875 0.28125 1.671875 0.03125 1.203125 -0.46875 C 0.742188 -0.976562 0.5 -1.609375 0.46875 -2.359375 Z M 3.546875 -7.71875 Z M 3.546875 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="1e4aed7b-c431-41ce-96b2-3d2241aee11a">
<path style="stroke:none;" d=""/>
</symbol>
<symbol overflow="visible" id="851587bf-51c2-4915-ad1d-e5403635efbb">
<path style="stroke:none;" d="M 1.515625 -7.53125 L 2.96875 -1.59375 L 4.4375 -7.53125 L 5.859375 -7.53125 L 7.328125 -1.625 L 8.875 -7.53125 L 10.140625 -7.53125 L 7.953125 0 L 6.640625 0 L 5.09375 -5.828125 L 3.609375 0 L 2.296875 0 L 0.125 -7.53125 Z M 1.515625 -7.53125 "/>
</symbol>
<symbol overflow="visible" id="a8cd521c-cb51-426c-a396-cce6cdd2d45a">
<path style="stroke:none;" d="M 0.921875 -7.5 L 2.21875 -7.5 L 2.21875 0 L 0.921875 0 Z M 0.921875 -10.328125 L 2.21875 -10.328125 L 2.21875 -8.890625 L 0.921875 -8.890625 Z M 0.921875 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="7dd138ca-f2fb-492f-b411-6faef97e4491">
<path style="stroke:none;" d="M 0.921875 -10.375 L 2.1875 -10.375 L 2.1875 -6.515625 C 2.488281 -6.890625 2.757812 -7.15625 3 -7.3125 C 3.40625 -7.582031 3.914062 -7.71875 4.53125 -7.71875 C 5.625 -7.71875 6.363281 -7.332031 6.75 -6.5625 C 6.957031 -6.144531 7.0625 -5.566406 7.0625 -4.828125 L 7.0625 0 L 5.765625 0 L 5.765625 -4.75 C 5.765625 -5.300781 5.695312 -5.707031 5.5625 -5.96875 C 5.332031 -6.375 4.898438 -6.578125 4.265625 -6.578125 C 3.734375 -6.578125 3.253906 -6.394531 2.828125 -6.03125 C 2.398438 -5.675781 2.1875 -5 2.1875 -4 L 2.1875 0 L 0.921875 0 Z M 0.921875 -10.375 "/>
</symbol>
<symbol overflow="visible" id="65d16751-0dfc-49ed-9310-e06618844ff9">
<path style="stroke:none;" d="M 1.90625 -10.328125 L 4.875 -1.53125 L 7.8125 -10.328125 L 9.390625 -10.328125 L 5.609375 0 L 4.125 0 L 0.359375 -10.328125 Z M 1.90625 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="e10f3324-cb22-4eea-ad25-a694b85ca9c6">
<path style="stroke:none;" d="M 3.921875 -0.8125 C 4.753906 -0.8125 5.328125 -1.128906 5.640625 -1.765625 C 5.953125 -2.398438 6.109375 -3.109375 6.109375 -3.890625 C 6.109375 -4.585938 6 -5.160156 5.78125 -5.609375 C 5.414062 -6.296875 4.800781 -6.640625 3.9375 -6.640625 C 3.15625 -6.640625 2.585938 -6.34375 2.234375 -5.75 C 1.890625 -5.164062 1.71875 -4.457031 1.71875 -3.625 C 1.71875 -2.820312 1.890625 -2.148438 2.234375 -1.609375 C 2.585938 -1.078125 3.148438 -0.8125 3.921875 -0.8125 Z M 3.96875 -7.75 C 4.9375 -7.75 5.753906 -7.425781 6.421875 -6.78125 C 7.097656 -6.132812 7.4375 -5.179688 7.4375 -3.921875 C 7.4375 -2.710938 7.140625 -1.707031 6.546875 -0.90625 C 5.953125 -0.113281 5.035156 0.28125 3.796875 0.28125 C 2.765625 0.28125 1.941406 -0.0664062 1.328125 -0.765625 C 0.722656 -1.472656 0.421875 -2.421875 0.421875 -3.609375 C 0.421875 -4.867188 0.738281 -5.875 1.375 -6.625 C 2.019531 -7.375 2.882812 -7.75 3.96875 -7.75 Z M 3.921875 -7.71875 Z M 3.921875 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="42821b73-2108-4853-b0d9-4d0d665e0640">
<path style="stroke:none;" d="M 0.96875 -10.328125 L 2.234375 -10.328125 L 2.234375 0 L 0.96875 0 Z M 0.96875 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="356ac9f2-a21a-4393-a548-3cf387b7232d">
<path style="stroke:none;" d="M 1.78125 -10.328125 L 3.734375 -1.921875 L 6.0625 -10.328125 L 7.578125 -10.328125 L 9.921875 -1.921875 L 11.859375 -10.328125 L 13.40625 -10.328125 L 10.6875 0 L 9.21875 0 L 6.828125 -8.5625 L 4.4375 0 L 2.96875 0 L 0.265625 -10.328125 Z M 1.78125 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="67328d24-65e4-492d-8f38-ae82af1afba3">
<path style="stroke:none;" d="M 0.96875 -7.53125 L 2.171875 -7.53125 L 2.171875 -6.234375 C 2.265625 -6.484375 2.503906 -6.789062 2.890625 -7.15625 C 3.273438 -7.519531 3.71875 -7.703125 4.21875 -7.703125 C 4.238281 -7.703125 4.273438 -7.695312 4.328125 -7.6875 C 4.390625 -7.6875 4.488281 -7.679688 4.625 -7.671875 L 4.625 -6.328125 C 4.550781 -6.347656 4.484375 -6.359375 4.421875 -6.359375 C 4.359375 -6.359375 4.289062 -6.359375 4.21875 -6.359375 C 3.570312 -6.359375 3.078125 -6.15625 2.734375 -5.75 C 2.398438 -5.34375 2.234375 -4.867188 2.234375 -4.328125 L 2.234375 0 L 0.96875 0 Z M 0.96875 -7.53125 "/>
</symbol>
<symbol overflow="visible" id="dd930bd5-b068-47ca-9966-1b2258c3728f">
<path style="stroke:none;" d="M 1.734375 -3.671875 C 1.734375 -2.867188 1.898438 -2.195312 2.234375 -1.65625 C 2.578125 -1.113281 3.128906 -0.84375 3.890625 -0.84375 C 4.472656 -0.84375 4.953125 -1.09375 5.328125 -1.59375 C 5.710938 -2.09375 5.90625 -2.816406 5.90625 -3.765625 C 5.90625 -4.710938 5.707031 -5.414062 5.3125 -5.875 C 4.925781 -6.332031 4.445312 -6.5625 3.875 -6.5625 C 3.238281 -6.5625 2.722656 -6.316406 2.328125 -5.828125 C 1.929688 -5.335938 1.734375 -4.617188 1.734375 -3.671875 Z M 3.640625 -7.671875 C 4.210938 -7.671875 4.691406 -7.546875 5.078125 -7.296875 C 5.304688 -7.160156 5.566406 -6.914062 5.859375 -6.5625 L 5.859375 -10.375 L 7.0625 -10.375 L 7.0625 0 L 5.9375 0 L 5.9375 -1.046875 C 5.632812 -0.585938 5.28125 -0.253906 4.875 -0.046875 C 4.476562 0.160156 4.019531 0.265625 3.5 0.265625 C 2.65625 0.265625 1.925781 -0.0820312 1.3125 -0.78125 C 0.695312 -1.488281 0.390625 -2.429688 0.390625 -3.609375 C 0.390625 -4.703125 0.671875 -5.648438 1.234375 -6.453125 C 1.796875 -7.265625 2.597656 -7.671875 3.640625 -7.671875 Z M 3.640625 -7.671875 "/>
</symbol>
</g>
<clipPath id="ab3d6429-e308-4096-9095-83b16d6e797f">
  <path d="M 124.46875 31.929688 L 454 31.929688 L 454 253 L 124.46875 253 Z M 124.46875 31.929688 "/>
</clipPath>
<clipPath id="25f63e63-5eb3-4806-89ea-4536ff58e4f6">
  <path d="M 189 31.929688 L 190 31.929688 L 190 253 L 189 253 Z M 189 31.929688 "/>
</clipPath>
<clipPath id="4806370f-c22e-401b-800f-bdbfab9b3e0c">
  <path d="M 288 31.929688 L 290 31.929688 L 290 253 L 288 253 Z M 288 31.929688 "/>
</clipPath>
<clipPath id="29ca058f-ceb3-4232-a635-4bc63b7c4b70">
  <path d="M 388 31.929688 L 390 31.929688 L 390 253 L 388 253 Z M 388 31.929688 "/>
</clipPath>
<clipPath id="d5435e5c-79b9-4e1d-a7b1-441abe641b01">
  <path d="M 124.46875 230 L 454.601562 230 L 454.601562 232 L 124.46875 232 Z M 124.46875 230 "/>
</clipPath>
<clipPath id="980a847a-cd15-4b8f-a511-a415407f9cbb">
  <path d="M 124.46875 194 L 454.601562 194 L 454.601562 196 L 124.46875 196 Z M 124.46875 194 "/>
</clipPath>
<clipPath id="54b5d448-3ae3-4647-81ce-d778ebbe344c">
  <path d="M 124.46875 159 L 454.601562 159 L 454.601562 161 L 124.46875 161 Z M 124.46875 159 "/>
</clipPath>
<clipPath id="cad3eb5e-31ad-4a3f-9c10-0aba70a2c6a3">
  <path d="M 124.46875 123 L 454.601562 123 L 454.601562 125 L 124.46875 125 Z M 124.46875 123 "/>
</clipPath>
<clipPath id="bddb0346-5acb-4ccd-a929-95245a43401e">
  <path d="M 124.46875 88 L 454.601562 88 L 454.601562 90 L 124.46875 90 Z M 124.46875 88 "/>
</clipPath>
<clipPath id="3124efac-5750-463c-97d0-ae52929691aa">
  <path d="M 124.46875 52 L 454.601562 52 L 454.601562 54 L 124.46875 54 Z M 124.46875 52 "/>
</clipPath>
<clipPath id="d5006fcc-e429-48d5-81ab-590624b7749f">
  <path d="M 138 31.929688 L 140 31.929688 L 140 253 L 138 253 Z M 138 31.929688 "/>
</clipPath>
<clipPath id="3b69d214-13c2-4605-bd59-ee9025826769">
  <path d="M 238 31.929688 L 240 31.929688 L 240 253 L 238 253 Z M 238 31.929688 "/>
</clipPath>
<clipPath id="03f41baa-d27c-45b7-9807-fc9baa9324b7">
  <path d="M 338 31.929688 L 340 31.929688 L 340 253 L 338 253 Z M 338 31.929688 "/>
</clipPath>
<clipPath id="f889a937-4b2b-48e9-a8a7-874019fb4469">
  <path d="M 438 31.929688 L 440 31.929688 L 440 253 L 438 253 Z M 438 31.929688 "/>
</clipPath>
</defs>
<g id="37a16018-c0c9-4a8b-958d-0f55f3df7076">
<rect x="0" y="0" width="468" height="289" style="fill:rgb(100%,100%,100%);fill-opacity:1;stroke:none;"/>
<rect x="0" y="0" width="468" height="289" style="fill:rgb(100%,100%,100%);fill-opacity:1;stroke:none;"/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:round;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 0 289 L 468 289 L 468 0 L 0 0 Z M 0 289 "/>
<g clip-path="url(#ab3d6429-e308-4096-9095-83b16d6e797f)" clip-rule="nonzero">
<path style=" stroke:none;fill-rule:nonzero;fill:rgb(89.803922%,89.803922%,89.803922%);fill-opacity:1;" d="M 124.46875 252.027344 L 453.601562 252.027344 L 453.601562 31.925781 L 124.46875 31.925781 Z M 124.46875 252.027344 "/>
</g>
<g clip-path="url(#25f63e63-5eb3-4806-89ea-4536ff58e4f6)" clip-rule="nonzero">
<path style="fill:none;stroke-width:0.531496;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(94.901961%,94.901961%,94.901961%);stroke-opacity:1;stroke-miterlimit:10;" d="M 189.296875 252.027344 L 189.296875 31.929688 "/>
</g>
<g clip-path="url(#4806370f-c22e-401b-800f-bdbfab9b3e0c)" clip-rule="nonzero">
<path style="fill:none;stroke-width:0.531496;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(94.901961%,94.901961%,94.901961%);stroke-opacity:1;stroke-miterlimit:10;" d="M 289.035156 252.027344 L 289.035156 31.929688 "/>
</g>
<g clip-path="url(#29ca058f-ceb3-4232-a635-4bc63b7c4b70)" clip-rule="nonzero">
<path style="fill:none;stroke-width:0.531496;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(94.901961%,94.901961%,94.901961%);stroke-opacity:1;stroke-miterlimit:10;" d="M 388.769531 252.027344 L 388.769531 31.929688 "/>
</g>
<g clip-path="url(#d5435e5c-79b9-4e1d-a7b1-441abe641b01)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 230.730469 L 453.601562 230.730469 "/>
</g>
<g clip-path="url(#980a847a-cd15-4b8f-a511-a415407f9cbb)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 195.230469 L 453.601562 195.230469 "/>
</g>
<g clip-path="url(#54b5d448-3ae3-4647-81ce-d778ebbe344c)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 159.730469 L 453.601562 159.730469 "/>
</g>
<g clip-path="url(#cad3eb5e-31ad-4a3f-9c10-0aba70a2c6a3)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 124.226562 L 453.601562 124.226562 "/>
</g>
<g clip-path="url(#bddb0346-5acb-4ccd-a929-95245a43401e)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 88.726562 L 453.601562 88.726562 "/>
</g>
<g clip-path="url(#3124efac-5750-463c-97d0-ae52929691aa)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 53.226562 L 453.601562 53.226562 "/>
</g>
<g clip-path="url(#d5006fcc-e429-48d5-81ab-590624b7749f)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 252.027344 L 139.429688 31.929688 "/>
</g>
<g clip-path="url(#3b69d214-13c2-4605-bd59-ee9025826769)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 239.167969 252.027344 L 239.167969 31.929688 "/>
</g>
<g clip-path="url(#03f41baa-d27c-45b7-9807-fc9baa9324b7)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 338.902344 252.027344 L 338.902344 31.929688 "/>
</g>
<g clip-path="url(#f889a937-4b2b-48e9-a8a7-874019fb4469)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 438.640625 252.027344 L 438.640625 31.929688 "/>
</g>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 246.703125 L 242.257812 246.703125 L 242.257812 214.753906 L 139.429688 214.753906 Z M 139.429688 246.703125 "/>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 211.203125 L 148.105469 211.203125 L 148.105469 179.253906 L 139.429688 179.253906 Z M 139.429688 211.203125 "/>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 175.703125 L 396.550781 175.703125 L 396.550781 143.753906 L 139.429688 143.753906 Z M 139.429688 175.703125 "/>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 140.203125 L 316.164062 140.203125 L 316.164062 108.253906 L 139.429688 108.253906 Z M 139.429688 140.203125 "/>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 104.703125 L 219.617188 104.703125 L 219.617188 72.753906 L 139.429688 72.753906 Z M 139.429688 104.703125 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 69.203125 L 139.429688 37.253906 Z M 139.429688 69.203125 "/>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#933e3a45-7bee-4eb6-85e2-6ca9d3aeaf2a" x="245.804688" y="235.816406"/>
  <use xlink:href="#0c5398bb-aa3d-42a5-804f-c5905b57c00e" x="253.686417" y="235.816406"/>
  <use xlink:href="#54f13a55-eab5-41fb-bce9-77f5b6edc655" x="257.623825" y="235.816406"/>
  <use xlink:href="#4831850d-da5a-4e63-80fc-1e7aaba00389" x="265.505554" y="235.816406"/>
  <use xlink:href="#933e3a45-7bee-4eb6-85e2-6ca9d3aeaf2a" x="273.387283" y="235.816406"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#933e3a45-7bee-4eb6-85e2-6ca9d3aeaf2a" x="319.710938" y="129.3125"/>
  <use xlink:href="#0c5398bb-aa3d-42a5-804f-c5905b57c00e" x="327.592667" y="129.3125"/>
  <use xlink:href="#876ef0d4-ad74-4833-a123-ddd48f998b8f" x="331.530075" y="129.3125"/>
  <use xlink:href="#876ef0d4-ad74-4833-a123-ddd48f998b8f" x="339.411804" y="129.3125"/>
  <use xlink:href="#de9b5837-1d48-46e6-a6f4-647d8f64554b" x="347.293533" y="129.3125"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#bfb808fc-01d6-4644-bbd3-2c96ec5ff162" x="221.980469" y="93.8125"/>
  <use xlink:href="#54f13a55-eab5-41fb-bce9-77f5b6edc655" x="229.862198" y="93.8125"/>
  <use xlink:href="#ce154712-7e91-40f4-8cec-1a76a44aff62" x="237.743927" y="93.8125"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#bfb808fc-01d6-4644-bbd3-2c96ec5ff162" x="149.683594" y="200.316406"/>
  <use xlink:href="#876ef0d4-ad74-4833-a123-ddd48f998b8f" x="157.565323" y="200.316406"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#54f13a55-eab5-41fb-bce9-77f5b6edc655" x="140.21875" y="58.3125"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#de9b5837-1d48-46e6-a6f4-647d8f64554b" x="400.097656" y="164.816406"/>
  <use xlink:href="#0c5398bb-aa3d-42a5-804f-c5905b57c00e" x="407.979385" y="164.816406"/>
  <use xlink:href="#c90fcbd7-1ffa-41da-be99-cb04d4fa9285" x="411.916794" y="164.816406"/>
  <use xlink:href="#876ef0d4-ad74-4833-a123-ddd48f998b8f" x="419.798523" y="164.816406"/>
  <use xlink:href="#bfb808fc-01d6-4644-bbd3-2c96ec5ff162" x="427.680252" y="164.816406"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#7f9c35d7-e87a-45e6-b915-07c79e79ba88" x="27.800781" y="234.167969"/>
  <use xlink:href="#518a0f4d-ebf2-4f47-a5c5-3c620d8f5aa4" x="33.661026" y="234.167969"/>
  <use xlink:href="#571abcf8-31c2-4101-b2d0-25e331728abb" x="38.996613" y="234.167969"/>
  <use xlink:href="#c8d0ed67-e94d-44a6-afa0-e99cb47f3045" x="44.332199" y="234.167969"/>
  <use xlink:href="#d54f06c4-1567-4e3b-bb84-7e7972ed21f9" x="46.99765" y="234.167969"/>
  <use xlink:href="#62a1d5bd-2b0b-401e-853e-2b64689af9f3" x="53.396606" y="234.167969"/>
  <use xlink:href="#62a1d5bd-2b0b-401e-853e-2b64689af9f3" x="58.732193" y="234.167969"/>
  <use xlink:href="#73ae7141-8d61-425b-b033-a3f46a945a6f" x="64.06778" y="234.167969"/>
  <use xlink:href="#c8d0ed67-e94d-44a6-afa0-e99cb47f3045" x="68.864655" y="234.167969"/>
  <use xlink:href="#62a1d5bd-2b0b-401e-853e-2b64689af9f3" x="71.530106" y="234.167969"/>
  <use xlink:href="#2ad2efdf-7e8b-4342-a799-aa5b930eec68" x="76.865692" y="234.167969"/>
  <use xlink:href="#c8d0ed67-e94d-44a6-afa0-e99cb47f3045" x="79.531143" y="234.167969"/>
  <use xlink:href="#199876ba-5c73-4808-82bc-852e4523e196" x="82.196594" y="234.167969"/>
  <use xlink:href="#62a1d5bd-2b0b-401e-853e-2b64689af9f3" x="90.188263" y="234.167969"/>
  <use xlink:href="#d8a87d0f-233b-4133-8ed0-3a2ffc62538b" x="95.523849" y="234.167969"/>
  <use xlink:href="#bcb6380e-9645-4b7a-ae69-ceed6ce9942c" x="98.718643" y="234.167969"/>
  <use xlink:href="#62a1d5bd-2b0b-401e-853e-2b64689af9f3" x="106.710312" y="234.167969"/>
  <use xlink:href="#b654e85f-6de7-48c2-a6e9-cf9a7141306a" x="112.045898" y="234.167969"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#7f9c35d7-e87a-45e6-b915-07c79e79ba88" x="37.925781" y="198.667969"/>
  <use xlink:href="#518a0f4d-ebf2-4f47-a5c5-3c620d8f5aa4" x="43.786026" y="198.667969"/>
  <use xlink:href="#571abcf8-31c2-4101-b2d0-25e331728abb" x="49.121613" y="198.667969"/>
  <use xlink:href="#c8d0ed67-e94d-44a6-afa0-e99cb47f3045" x="54.457199" y="198.667969"/>
  <use xlink:href="#f67bf3b9-7821-48e6-b8cd-d71c524fbb4c" x="57.12265" y="198.667969"/>
  <use xlink:href="#518a0f4d-ebf2-4f47-a5c5-3c620d8f5aa4" x="64.050949" y="198.667969"/>
  <use xlink:href="#531119b7-a67c-42b4-b42a-7d9033e40188" x="69.386536" y="198.667969"/>
  <use xlink:href="#bcb6380e-9645-4b7a-ae69-ceed6ce9942c" x="74.722122" y="198.667969"/>
  <use xlink:href="#bcb6380e-9645-4b7a-ae69-ceed6ce9942c" x="82.713791" y="198.667969"/>
  <use xlink:href="#531119b7-a67c-42b4-b42a-7d9033e40188" x="90.70546" y="198.667969"/>
  <use xlink:href="#851e063c-cf48-45e9-b5ac-9ff18a239606" x="96.041046" y="198.667969"/>
  <use xlink:href="#531119b7-a67c-42b4-b42a-7d9033e40188" x="101.376633" y="198.667969"/>
  <use xlink:href="#ad952859-b6b9-4155-a941-7bfb27dfdb69" x="106.712219" y="198.667969"/>
  <use xlink:href="#531119b7-a67c-42b4-b42a-7d9033e40188" x="112.047806" y="198.667969"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#7f9c35d7-e87a-45e6-b915-07c79e79ba88" x="54.996094" y="163.167969"/>
  <use xlink:href="#518a0f4d-ebf2-4f47-a5c5-3c620d8f5aa4" x="60.856339" y="163.167969"/>
  <use xlink:href="#571abcf8-31c2-4101-b2d0-25e331728abb" x="66.191925" y="163.167969"/>
  <use xlink:href="#c8d0ed67-e94d-44a6-afa0-e99cb47f3045" x="71.527512" y="163.167969"/>
  <use xlink:href="#fa132e7d-79e6-4eed-8a5a-28620a305be4" x="74.192963" y="163.167969"/>
  <use xlink:href="#62a1d5bd-2b0b-401e-853e-2b64689af9f3" x="81.121262" y="163.167969"/>
  <use xlink:href="#579b9a0b-5273-487f-93b1-db72e61accbe" x="86.456848" y="163.167969"/>
  <use xlink:href="#bc5c6727-943d-4b62-b5fc-9f115d9e6364" x="88.588272" y="163.167969"/>
  <use xlink:href="#c8d0ed67-e94d-44a6-afa0-e99cb47f3045" x="93.385147" y="163.167969"/>
  <use xlink:href="#d54f06c4-1567-4e3b-bb84-7e7972ed21f9" x="96.050598" y="163.167969"/>
  <use xlink:href="#10f725ed-05e0-4aa2-aa21-9b1284cd2174" x="102.449554" y="163.167969"/>
  <use xlink:href="#415713a2-3797-4b89-8c45-4441008c9ad8" x="104.580978" y="163.167969"/>
  <use xlink:href="#579b9a0b-5273-487f-93b1-db72e61accbe" x="109.916565" y="163.167969"/>
  <use xlink:href="#571abcf8-31c2-4101-b2d0-25e331728abb" x="112.047989" y="163.167969"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#7f9c35d7-e87a-45e6-b915-07c79e79ba88" x="23.011719" y="127.664062"/>
  <use xlink:href="#518a0f4d-ebf2-4f47-a5c5-3c620d8f5aa4" x="28.871964" y="127.664062"/>
  <use xlink:href="#571abcf8-31c2-4101-b2d0-25e331728abb" x="34.20755" y="127.664062"/>
  <use xlink:href="#c8d0ed67-e94d-44a6-afa0-e99cb47f3045" x="39.543137" y="127.664062"/>
  <use xlink:href="#7407b793-01e4-4622-98dc-d83ccb168a21" x="42.208588" y="127.664062"/>
  <use xlink:href="#571abcf8-31c2-4101-b2d0-25e331728abb" x="47.005463" y="127.664062"/>
  <use xlink:href="#56a5e09d-e92f-4a2a-ad9b-6d516cb57308" x="52.341049" y="127.664062"/>
  <use xlink:href="#10f725ed-05e0-4aa2-aa21-9b1284cd2174" x="59.269348" y="127.664062"/>
  <use xlink:href="#009522d0-1240-4848-aaed-ccccd19575b5" x="61.400772" y="127.664062"/>
  <use xlink:href="#518a0f4d-ebf2-4f47-a5c5-3c620d8f5aa4" x="66.197647" y="127.664062"/>
  <use xlink:href="#c8d0ed67-e94d-44a6-afa0-e99cb47f3045" x="71.533234" y="127.664062"/>
  <use xlink:href="#80e705cf-5065-485f-ae37-bec7e29e07bc" x="74.198685" y="127.664062"/>
  <use xlink:href="#3c724632-d6f8-4f30-beaa-4b06a36c92e6" x="80.597641" y="127.664062"/>
  <use xlink:href="#d8a87d0f-233b-4133-8ed0-3a2ffc62538b" x="85.394516" y="127.664062"/>
  <use xlink:href="#10f725ed-05e0-4aa2-aa21-9b1284cd2174" x="88.58931" y="127.664062"/>
  <use xlink:href="#851e063c-cf48-45e9-b5ac-9ff18a239606" x="90.720734" y="127.664062"/>
  <use xlink:href="#fd5ea26b-cdbc-4d5a-b1cd-e24197506908" x="96.05632" y="127.664062"/>
  <use xlink:href="#e95eb0e0-0f65-41cd-9477-f94188a8afd3" x="98.721771" y="127.664062"/>
  <use xlink:href="#d8a87d0f-233b-4133-8ed0-3a2ffc62538b" x="104.057358" y="127.664062"/>
  <use xlink:href="#571abcf8-31c2-4101-b2d0-25e331728abb" x="107.252151" y="127.664062"/>
  <use xlink:href="#009522d0-1240-4848-aaed-ccccd19575b5" x="112.587738" y="127.664062"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#7f9c35d7-e87a-45e6-b915-07c79e79ba88" x="72.585938" y="92.164062"/>
  <use xlink:href="#518a0f4d-ebf2-4f47-a5c5-3c620d8f5aa4" x="78.446182" y="92.164062"/>
  <use xlink:href="#571abcf8-31c2-4101-b2d0-25e331728abb" x="83.781769" y="92.164062"/>
  <use xlink:href="#c8d0ed67-e94d-44a6-afa0-e99cb47f3045" x="89.117355" y="92.164062"/>
  <use xlink:href="#9b2dfd8c-86bb-4b5e-84e3-5a26a7c6038c" x="91.782806" y="92.164062"/>
  <use xlink:href="#62a1d5bd-2b0b-401e-853e-2b64689af9f3" x="98.181763" y="92.164062"/>
  <use xlink:href="#d8a87d0f-233b-4133-8ed0-3a2ffc62538b" x="103.517349" y="92.164062"/>
  <use xlink:href="#531119b7-a67c-42b4-b42a-7d9033e40188" x="106.712143" y="92.164062"/>
  <use xlink:href="#b654e85f-6de7-48c2-a6e9-cf9a7141306a" x="112.047729" y="92.164062"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#7f9c35d7-e87a-45e6-b915-07c79e79ba88" x="70.984375" y="56.664062"/>
  <use xlink:href="#518a0f4d-ebf2-4f47-a5c5-3c620d8f5aa4" x="76.84462" y="56.664062"/>
  <use xlink:href="#571abcf8-31c2-4101-b2d0-25e331728abb" x="82.180206" y="56.664062"/>
  <use xlink:href="#c8d0ed67-e94d-44a6-afa0-e99cb47f3045" x="87.515793" y="56.664062"/>
  <use xlink:href="#6bac1475-bbbf-4864-a9d6-752c1a1c9572" x="90.181244" y="56.664062"/>
  <use xlink:href="#571abcf8-31c2-4101-b2d0-25e331728abb" x="96.5802" y="56.664062"/>
  <use xlink:href="#ad952859-b6b9-4155-a941-7bfb27dfdb69" x="101.915787" y="56.664062"/>
  <use xlink:href="#531119b7-a67c-42b4-b42a-7d9033e40188" x="107.251373" y="56.664062"/>
  <use xlink:href="#009522d0-1240-4848-aaed-ccccd19575b5" x="112.58696" y="56.664062"/>
</g>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 120.21875 230.730469 L 124.46875 230.730469 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 120.21875 195.230469 L 124.46875 195.230469 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 120.21875 159.730469 L 124.46875 159.730469 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 120.21875 124.226562 L 124.46875 124.226562 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 120.21875 88.726562 L 124.46875 88.726562 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 120.21875 53.226562 L 124.46875 53.226562 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 256.28125 L 139.429688 252.027344 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 239.167969 256.28125 L 239.167969 252.027344 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 338.902344 256.28125 L 338.902344 252.027344 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 438.640625 256.28125 L 438.640625 252.027344 "/>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#95f46cb7-fc74-4dab-a1b2-13e7afe01b10" x="136.761719" y="265.992188"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#1cd16fa3-8228-4a72-b55d-5ea659b6f7a8" x="228.496094" y="265.992188"/>
  <use xlink:href="#95f46cb7-fc74-4dab-a1b2-13e7afe01b10" x="233.83168" y="265.992188"/>
  <use xlink:href="#95f46cb7-fc74-4dab-a1b2-13e7afe01b10" x="239.167267" y="265.992188"/>
  <use xlink:href="#95f46cb7-fc74-4dab-a1b2-13e7afe01b10" x="244.502853" y="265.992188"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#35e2073c-8bf1-40eb-83fe-da4aa2f54d5c" x="328.230469" y="265.992188"/>
  <use xlink:href="#95f46cb7-fc74-4dab-a1b2-13e7afe01b10" x="333.566055" y="265.992188"/>
  <use xlink:href="#95f46cb7-fc74-4dab-a1b2-13e7afe01b10" x="338.901642" y="265.992188"/>
  <use xlink:href="#95f46cb7-fc74-4dab-a1b2-13e7afe01b10" x="344.237228" y="265.992188"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#23a52dda-adbd-49a5-93ce-b0af9863fb12" x="427.96875" y="265.992188"/>
  <use xlink:href="#95f46cb7-fc74-4dab-a1b2-13e7afe01b10" x="433.304337" y="265.992188"/>
  <use xlink:href="#95f46cb7-fc74-4dab-a1b2-13e7afe01b10" x="438.639923" y="265.992188"/>
  <use xlink:href="#95f46cb7-fc74-4dab-a1b2-13e7afe01b10" x="443.97551" y="265.992188"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#6ebe081a-2f9c-4b57-a466-89294c534270" x="193.347656" y="28.328125"/>
  <use xlink:href="#c2d3e914-7845-4f38-b529-bfe2b590ee4e" x="202.956512" y="28.328125"/>
  <use xlink:href="#84889efc-9850-4a0b-ab79-3ae9aa0e0e4d" x="210.968582" y="28.328125"/>
  <use xlink:href="#5ca03971-6326-4d6b-afce-60cf7e805e2e" x="218.980652" y="28.328125"/>
  <use xlink:href="#c2d3e914-7845-4f38-b529-bfe2b590ee4e" x="222.98317" y="28.328125"/>
  <use xlink:href="#84889efc-9850-4a0b-ab79-3ae9aa0e0e4d" x="230.995239" y="28.328125"/>
  <use xlink:href="#842e014b-de28-48d3-bd61-bb1560c95b8b" x="239.007309" y="28.328125"/>
  <use xlink:href="#c2d3e914-7845-4f38-b529-bfe2b590ee4e" x="246.210434" y="28.328125"/>
  <use xlink:href="#3704ae25-ae68-41ef-9bcc-85057db8231c" x="254.222504" y="28.328125"/>
  <use xlink:href="#1e4aed7b-c431-41ce-96b2-3d2241aee11a" x="261.425629" y="28.328125"/>
  <use xlink:href="#851587bf-51c2-4915-ad1d-e5403635efbb" x="265.428146" y="28.328125"/>
  <use xlink:href="#a8cd521c-cb51-426c-a396-cce6cdd2d45a" x="275.831879" y="28.328125"/>
  <use xlink:href="#5ca03971-6326-4d6b-afce-60cf7e805e2e" x="279.032486" y="28.328125"/>
  <use xlink:href="#7dd138ca-f2fb-492f-b411-6faef97e4491" x="283.035004" y="28.328125"/>
  <use xlink:href="#1e4aed7b-c431-41ce-96b2-3d2241aee11a" x="291.047073" y="28.328125"/>
  <use xlink:href="#65d16751-0dfc-49ed-9310-e06618844ff9" x="295.049591" y="28.328125"/>
  <use xlink:href="#a8cd521c-cb51-426c-a396-cce6cdd2d45a" x="304.658447" y="28.328125"/>
  <use xlink:href="#e10f3324-cb22-4eea-ad25-a694b85ca9c6" x="307.859055" y="28.328125"/>
  <use xlink:href="#42821b73-2108-4853-b0d9-4d0d665e0640" x="315.871124" y="28.328125"/>
  <use xlink:href="#c2d3e914-7845-4f38-b529-bfe2b590ee4e" x="319.071732" y="28.328125"/>
  <use xlink:href="#84889efc-9850-4a0b-ab79-3ae9aa0e0e4d" x="327.083801" y="28.328125"/>
  <use xlink:href="#5ca03971-6326-4d6b-afce-60cf7e805e2e" x="335.095871" y="28.328125"/>
  <use xlink:href="#1e4aed7b-c431-41ce-96b2-3d2241aee11a" x="339.098389" y="28.328125"/>
  <use xlink:href="#356ac9f2-a21a-4393-a548-3cf387b7232d" x="343.100906" y="28.328125"/>
  <use xlink:href="#e10f3324-cb22-4eea-ad25-a694b85ca9c6" x="356.698212" y="28.328125"/>
  <use xlink:href="#67328d24-65e4-492d-8f38-ae82af1afba3" x="364.710281" y="28.328125"/>
  <use xlink:href="#dd930bd5-b068-47ca-9966-1b2258c3728f" x="369.507675" y="28.328125"/>
  <use xlink:href="#3704ae25-ae68-41ef-9bcc-85057db8231c" x="377.519745" y="28.328125"/>
</g>
</g>
</svg>


By a landslide, the Bible is on top, followed closely by the Jewish Scriptures.
This isn't unexpected for two reasons:

1. The Bible and Jewish Scriptures have the most sentences.
2. The Jewish Scriptures share a huge amount of material with the Bible.

A better way to look at the data is to compute the ratio of violent sentences against total sentences.

```clojure
;; Define the data structure for the plot.
(def violent-normalized-plot
  [[:<- :g (gg4clj/data-frame violent-sentence-counts)]
              (gg4clj/r+
                [:ggplot :g [:aes {:x :book :y :ratio :label :rlabel}]]
                ;; Style the main bar.
                [:geom_bar {:stat "identity" :color "steelblue" :fill "steelblue"}]
                ;; Add the values.
                [:geom_text {:hjust -0.1}]
                ;; Extend the y axis to accomodate labels.
                [:ylim 0 0.2]
                ;; Add title.
                [:ggtitle "Sentences with Violent Words (Normalized)"]
                ;; Remove the axis labels.
                [:xlab ""]
                [:ylab ""]
                ;; Flip to horizontal bar.
                [:coord_flip])])

;; Save it to a file.
(spit "violent-normalized-plot.zvg" (gg4clj/render violent-normalized-plot))

;; ... and render it in the REPL.
(gg4clj/view violent-normalized-plot)
```

<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="468pt" height="289pt" viewBox="0 0 468 289" version="1.1">
<defs>
<g>
<symbol overflow="visible" id="a9dd422d-57ba-4a37-9db6-63360fab9106">
<path style="stroke:none;" d="M 0.453125 0 L 0.453125 -10.171875 L 8.53125 -10.171875 L 8.53125 0 Z M 7.25 -1.265625 L 7.25 -8.890625 L 1.734375 -8.890625 L 1.734375 -1.265625 Z M 7.25 -1.265625 "/>
</symbol>
<symbol overflow="visible" id="5c5d006e-d463-4237-90f3-4cfeea901fc0">
<path style="stroke:none;" d="M 3.828125 -9.90625 C 5.109375 -9.90625 6.035156 -9.378906 6.609375 -8.328125 C 7.054688 -7.503906 7.28125 -6.382812 7.28125 -4.96875 C 7.28125 -3.625 7.078125 -2.507812 6.671875 -1.625 C 6.097656 -0.363281 5.148438 0.265625 3.828125 0.265625 C 2.640625 0.265625 1.753906 -0.25 1.171875 -1.28125 C 0.679688 -2.144531 0.4375 -3.300781 0.4375 -4.75 C 0.4375 -5.875 0.582031 -6.84375 0.875 -7.65625 C 1.425781 -9.15625 2.410156 -9.90625 3.828125 -9.90625 Z M 3.8125 -0.859375 C 4.457031 -0.859375 4.972656 -1.144531 5.359375 -1.71875 C 5.742188 -2.289062 5.9375 -3.359375 5.9375 -4.921875 C 5.9375 -6.046875 5.796875 -6.96875 5.515625 -7.6875 C 5.242188 -8.414062 4.707031 -8.78125 3.90625 -8.78125 C 3.175781 -8.78125 2.640625 -8.4375 2.296875 -7.75 C 1.960938 -7.0625 1.796875 -6.046875 1.796875 -4.703125 C 1.796875 -3.691406 1.90625 -2.878906 2.125 -2.265625 C 2.445312 -1.328125 3.007812 -0.859375 3.8125 -0.859375 Z M 3.8125 -0.859375 "/>
</symbol>
<symbol overflow="visible" id="dcbc5f3d-49cd-4465-993f-f45a255e141e">
<path style="stroke:none;" d="M 1.21875 -1.515625 L 2.65625 -1.515625 L 2.65625 0 L 1.21875 0 Z M 1.21875 -1.515625 "/>
</symbol>
<symbol overflow="visible" id="d61ba169-9977-4c07-a00f-b652e2c3add3">
<path style="stroke:none;" d="M 1.359375 -7.015625 L 1.359375 -7.96875 C 2.253906 -8.0625 2.878906 -8.207031 3.234375 -8.40625 C 3.585938 -8.613281 3.851562 -9.101562 4.03125 -9.875 L 5.015625 -9.875 L 5.015625 0 L 3.6875 0 L 3.6875 -7.015625 Z M 1.359375 -7.015625 "/>
</symbol>
<symbol overflow="visible" id="27fef74f-3c4a-4715-b53c-2f1f1afaf2df">
<path style="stroke:none;" d="M 3.6875 0.265625 C 2.507812 0.265625 1.65625 -0.0507812 1.125 -0.6875 C 0.601562 -1.332031 0.34375 -2.117188 0.34375 -3.046875 L 1.640625 -3.046875 C 1.691406 -2.398438 1.8125 -1.929688 2 -1.640625 C 2.320312 -1.117188 2.90625 -0.859375 3.75 -0.859375 C 4.40625 -0.859375 4.929688 -1.035156 5.328125 -1.390625 C 5.722656 -1.742188 5.921875 -2.195312 5.921875 -2.75 C 5.921875 -3.425781 5.710938 -3.898438 5.296875 -4.171875 C 4.878906 -4.453125 4.300781 -4.59375 3.5625 -4.59375 C 3.476562 -4.59375 3.390625 -4.585938 3.296875 -4.578125 C 3.210938 -4.578125 3.128906 -4.578125 3.046875 -4.578125 L 3.046875 -5.671875 C 3.171875 -5.660156 3.273438 -5.648438 3.359375 -5.640625 C 3.453125 -5.640625 3.550781 -5.640625 3.65625 -5.640625 C 4.125 -5.640625 4.503906 -5.710938 4.796875 -5.859375 C 5.328125 -6.117188 5.59375 -6.582031 5.59375 -7.25 C 5.59375 -7.738281 5.414062 -8.113281 5.0625 -8.375 C 4.71875 -8.644531 4.3125 -8.78125 3.84375 -8.78125 C 3.007812 -8.78125 2.4375 -8.503906 2.125 -7.953125 C 1.945312 -7.648438 1.84375 -7.21875 1.8125 -6.65625 L 0.59375 -6.65625 C 0.59375 -7.394531 0.738281 -8.023438 1.03125 -8.546875 C 1.539062 -9.460938 2.429688 -9.921875 3.703125 -9.921875 C 4.710938 -9.921875 5.492188 -9.695312 6.046875 -9.25 C 6.609375 -8.800781 6.890625 -8.148438 6.890625 -7.296875 C 6.890625 -6.679688 6.722656 -6.1875 6.390625 -5.8125 C 6.191406 -5.570312 5.929688 -5.390625 5.609375 -5.265625 C 6.128906 -5.117188 6.535156 -4.835938 6.828125 -4.421875 C 7.117188 -4.015625 7.265625 -3.519531 7.265625 -2.9375 C 7.265625 -1.988281 6.953125 -1.21875 6.328125 -0.625 C 5.703125 -0.03125 4.820312 0.265625 3.6875 0.265625 Z M 3.6875 0.265625 "/>
</symbol>
<symbol overflow="visible" id="d3ae3f82-2764-475e-8d13-26c33b6be63b">
<path style="stroke:none;" d="M 4.140625 -9.953125 C 5.253906 -9.953125 6.023438 -9.664062 6.453125 -9.09375 C 6.890625 -8.519531 7.109375 -7.925781 7.109375 -7.3125 L 5.875 -7.3125 C 5.800781 -7.707031 5.6875 -8.015625 5.53125 -8.234375 C 5.226562 -8.648438 4.773438 -8.859375 4.171875 -8.859375 C 3.472656 -8.859375 2.914062 -8.535156 2.5 -7.890625 C 2.09375 -7.242188 1.863281 -6.320312 1.8125 -5.125 C 2.101562 -5.539062 2.46875 -5.851562 2.90625 -6.0625 C 3.300781 -6.25 3.742188 -6.34375 4.234375 -6.34375 C 5.054688 -6.34375 5.773438 -6.078125 6.390625 -5.546875 C 7.015625 -5.015625 7.328125 -4.222656 7.328125 -3.171875 C 7.328125 -2.273438 7.035156 -1.476562 6.453125 -0.78125 C 5.867188 -0.09375 5.03125 0.25 3.9375 0.25 C 3.007812 0.25 2.207031 -0.0976562 1.53125 -0.796875 C 0.863281 -1.503906 0.53125 -2.691406 0.53125 -4.359375 C 0.53125 -5.585938 0.679688 -6.628906 0.984375 -7.484375 C 1.554688 -9.128906 2.609375 -9.953125 4.140625 -9.953125 Z M 4.0625 -0.84375 C 4.707031 -0.84375 5.191406 -1.0625 5.515625 -1.5 C 5.847656 -1.945312 6.015625 -2.472656 6.015625 -3.078125 C 6.015625 -3.578125 5.867188 -4.054688 5.578125 -4.515625 C 5.285156 -4.972656 4.757812 -5.203125 4 -5.203125 C 3.457031 -5.203125 2.984375 -5.023438 2.578125 -4.671875 C 2.179688 -4.316406 1.984375 -3.785156 1.984375 -3.078125 C 1.984375 -2.441406 2.164062 -1.910156 2.53125 -1.484375 C 2.894531 -1.054688 3.40625 -0.84375 4.0625 -0.84375 Z M 4.0625 -0.84375 "/>
</symbol>
<symbol overflow="visible" id="85e9991f-abff-4766-b513-9f561e0651be">
<path style="stroke:none;" d="M 3.859375 -5.75 C 4.398438 -5.75 4.828125 -5.898438 5.140625 -6.203125 C 5.453125 -6.515625 5.609375 -6.882812 5.609375 -7.3125 C 5.609375 -7.6875 5.457031 -8.023438 5.15625 -8.328125 C 4.863281 -8.640625 4.414062 -8.796875 3.8125 -8.796875 C 3.207031 -8.796875 2.769531 -8.640625 2.5 -8.328125 C 2.238281 -8.023438 2.109375 -7.664062 2.109375 -7.25 C 2.109375 -6.78125 2.28125 -6.410156 2.625 -6.140625 C 2.976562 -5.878906 3.390625 -5.75 3.859375 -5.75 Z M 3.9375 -0.84375 C 4.507812 -0.84375 4.984375 -1 5.359375 -1.3125 C 5.742188 -1.625 5.9375 -2.09375 5.9375 -2.71875 C 5.9375 -3.351562 5.738281 -3.835938 5.34375 -4.171875 C 4.957031 -4.503906 4.457031 -4.671875 3.84375 -4.671875 C 3.25 -4.671875 2.757812 -4.5 2.375 -4.15625 C 2 -3.820312 1.8125 -3.351562 1.8125 -2.75 C 1.8125 -2.238281 1.984375 -1.789062 2.328125 -1.40625 C 2.671875 -1.03125 3.207031 -0.84375 3.9375 -0.84375 Z M 2.15625 -5.28125 C 1.8125 -5.425781 1.539062 -5.597656 1.34375 -5.796875 C 0.976562 -6.171875 0.796875 -6.648438 0.796875 -7.234375 C 0.796875 -7.972656 1.0625 -8.609375 1.59375 -9.140625 C 2.132812 -9.671875 2.894531 -9.9375 3.875 -9.9375 C 4.832031 -9.9375 5.578125 -9.6875 6.109375 -9.1875 C 6.648438 -8.6875 6.921875 -8.101562 6.921875 -7.4375 C 6.921875 -6.8125 6.765625 -6.3125 6.453125 -5.9375 C 6.273438 -5.71875 6.003906 -5.503906 5.640625 -5.296875 C 6.046875 -5.109375 6.367188 -4.890625 6.609375 -4.640625 C 7.046875 -4.179688 7.265625 -3.582031 7.265625 -2.84375 C 7.265625 -1.96875 6.972656 -1.226562 6.390625 -0.625 C 5.804688 -0.0195312 4.976562 0.28125 3.90625 0.28125 C 2.9375 0.28125 2.117188 0.0195312 1.453125 -0.5 C 0.785156 -1.019531 0.453125 -1.78125 0.453125 -2.78125 C 0.453125 -3.363281 0.59375 -3.867188 0.875 -4.296875 C 1.164062 -4.722656 1.59375 -5.050781 2.15625 -5.28125 Z M 2.15625 -5.28125 "/>
</symbol>
<symbol overflow="visible" id="29071ec5-ff69-4f8d-b427-30ecd709e34b">
<path style="stroke:none;" d="M 1.875 -2.390625 C 1.914062 -1.703125 2.179688 -1.226562 2.671875 -0.96875 C 2.929688 -0.832031 3.21875 -0.765625 3.53125 -0.765625 C 4.125 -0.765625 4.628906 -1.007812 5.046875 -1.5 C 5.472656 -2 5.773438 -3.007812 5.953125 -4.53125 C 5.671875 -4.09375 5.328125 -3.78125 4.921875 -3.59375 C 4.515625 -3.414062 4.078125 -3.328125 3.609375 -3.328125 C 2.648438 -3.328125 1.890625 -3.625 1.328125 -4.21875 C 0.773438 -4.820312 0.5 -5.59375 0.5 -6.53125 C 0.5 -7.425781 0.773438 -8.210938 1.328125 -8.890625 C 1.878906 -9.578125 2.6875 -9.921875 3.75 -9.921875 C 5.195312 -9.921875 6.195312 -9.269531 6.75 -7.96875 C 7.050781 -7.257812 7.203125 -6.363281 7.203125 -5.28125 C 7.203125 -4.070312 7.019531 -3 6.65625 -2.0625 C 6.050781 -0.5 5.023438 0.28125 3.578125 0.28125 C 2.609375 0.28125 1.875 0.0234375 1.375 -0.484375 C 0.875 -0.992188 0.625 -1.628906 0.625 -2.390625 Z M 3.765625 -4.421875 C 4.265625 -4.421875 4.71875 -4.582031 5.125 -4.90625 C 5.53125 -5.238281 5.734375 -5.8125 5.734375 -6.625 C 5.734375 -7.351562 5.550781 -7.894531 5.1875 -8.25 C 4.820312 -8.601562 4.351562 -8.78125 3.78125 -8.78125 C 3.175781 -8.78125 2.691406 -8.578125 2.328125 -8.171875 C 1.972656 -7.765625 1.796875 -7.222656 1.796875 -6.546875 C 1.796875 -5.898438 1.953125 -5.382812 2.265625 -5 C 2.578125 -4.613281 3.078125 -4.421875 3.765625 -4.421875 Z M 3.765625 -4.421875 "/>
</symbol>
<symbol overflow="visible" id="6abb19dd-b08d-430d-8d6c-fec5d1934dbc">
<path style="stroke:none;" d="M 1.75 -2.53125 C 1.832031 -1.8125 2.160156 -1.316406 2.734375 -1.046875 C 3.035156 -0.910156 3.378906 -0.84375 3.765625 -0.84375 C 4.503906 -0.84375 5.050781 -1.078125 5.40625 -1.546875 C 5.757812 -2.015625 5.9375 -2.535156 5.9375 -3.109375 C 5.9375 -3.804688 5.722656 -4.34375 5.296875 -4.71875 C 4.878906 -5.09375 4.375 -5.28125 3.78125 -5.28125 C 3.351562 -5.28125 2.984375 -5.195312 2.671875 -5.03125 C 2.367188 -4.863281 2.109375 -4.632812 1.890625 -4.34375 L 0.8125 -4.40625 L 1.578125 -9.75 L 6.71875 -9.75 L 6.71875 -8.546875 L 2.5 -8.546875 L 2.078125 -5.78125 C 2.304688 -5.957031 2.523438 -6.085938 2.734375 -6.171875 C 3.109375 -6.328125 3.535156 -6.40625 4.015625 -6.40625 C 4.929688 -6.40625 5.703125 -6.113281 6.328125 -5.53125 C 6.960938 -4.945312 7.28125 -4.203125 7.28125 -3.296875 C 7.28125 -2.359375 6.988281 -1.53125 6.40625 -0.8125 C 5.832031 -0.101562 4.910156 0.25 3.640625 0.25 C 2.828125 0.25 2.109375 0.0234375 1.484375 -0.421875 C 0.867188 -0.878906 0.523438 -1.582031 0.453125 -2.53125 Z M 1.75 -2.53125 "/>
</symbol>
<symbol overflow="visible" id="ee373b28-b66e-4cc7-ac39-3a87321aebf9">
<path style="stroke:none;" d="M 7.40625 -9.75 L 7.40625 -8.65625 C 7.09375 -8.34375 6.671875 -7.800781 6.140625 -7.03125 C 5.609375 -6.269531 5.140625 -5.445312 4.734375 -4.5625 C 4.335938 -3.695312 4.035156 -2.910156 3.828125 -2.203125 C 3.691406 -1.742188 3.519531 -1.007812 3.3125 0 L 1.9375 0 C 2.25 -1.894531 2.9375 -3.773438 4 -5.640625 C 4.632812 -6.742188 5.300781 -7.691406 6 -8.484375 L 0.515625 -8.484375 L 0.515625 -9.75 Z M 7.40625 -9.75 "/>
</symbol>
<symbol overflow="visible" id="effe92fa-0145-4fd0-81af-2814e5b0089a">
<path style="stroke:none;" d="M 0.3125 0 L 0.3125 -6.875 L 5.765625 -6.875 L 5.765625 0 Z M 4.90625 -0.859375 L 4.90625 -6.015625 L 1.171875 -6.015625 L 1.171875 -0.859375 Z M 4.90625 -0.859375 "/>
</symbol>
<symbol overflow="visible" id="5971a0bb-62a5-417f-8d1d-c81c33ae291d">
<path style="stroke:none;" d="M 5.734375 -6.875 L 5.734375 -6.0625 L 3.421875 -6.0625 L 3.421875 0 L 2.46875 0 L 2.46875 -6.0625 L 0.15625 -6.0625 L 0.15625 -6.875 Z M 5.734375 -6.875 "/>
</symbol>
<symbol overflow="visible" id="cf1266df-5613-4cfd-8e84-be19123280f6">
<path style="stroke:none;" d="M 0.625 -6.90625 L 1.46875 -6.90625 L 1.46875 -4.34375 C 1.664062 -4.59375 1.84375 -4.769531 2 -4.875 C 2.269531 -5.050781 2.609375 -5.140625 3.015625 -5.140625 C 3.742188 -5.140625 4.238281 -4.882812 4.5 -4.375 C 4.632812 -4.09375 4.703125 -3.707031 4.703125 -3.21875 L 4.703125 0 L 3.84375 0 L 3.84375 -3.15625 C 3.84375 -3.53125 3.796875 -3.800781 3.703125 -3.96875 C 3.546875 -4.25 3.257812 -4.390625 2.84375 -4.390625 C 2.488281 -4.390625 2.171875 -4.265625 1.890625 -4.015625 C 1.609375 -3.773438 1.46875 -3.320312 1.46875 -2.65625 L 1.46875 0 L 0.625 0 Z M 0.625 -6.90625 "/>
</symbol>
<symbol overflow="visible" id="ab183365-5912-4dab-9540-4d3a4618f91d">
<path style="stroke:none;" d="M 2.703125 -5.125 C 3.054688 -5.125 3.398438 -5.039062 3.734375 -4.875 C 4.078125 -4.707031 4.332031 -4.492188 4.5 -4.234375 C 4.675781 -3.972656 4.789062 -3.675781 4.84375 -3.34375 C 4.894531 -3.113281 4.921875 -2.742188 4.921875 -2.234375 L 1.234375 -2.234375 C 1.253906 -1.722656 1.375 -1.3125 1.59375 -1 C 1.820312 -0.695312 2.171875 -0.546875 2.640625 -0.546875 C 3.085938 -0.546875 3.441406 -0.691406 3.703125 -0.984375 C 3.847656 -1.148438 3.953125 -1.347656 4.015625 -1.578125 L 4.84375 -1.578125 C 4.820312 -1.390625 4.75 -1.179688 4.625 -0.953125 C 4.507812 -0.734375 4.375 -0.550781 4.21875 -0.40625 C 3.957031 -0.15625 3.640625 0.015625 3.265625 0.109375 C 3.054688 0.148438 2.828125 0.171875 2.578125 0.171875 C 1.953125 0.171875 1.421875 -0.0507812 0.984375 -0.5 C 0.554688 -0.957031 0.34375 -1.59375 0.34375 -2.40625 C 0.34375 -3.21875 0.5625 -3.875 1 -4.375 C 1.4375 -4.875 2.003906 -5.125 2.703125 -5.125 Z M 4.046875 -2.90625 C 4.015625 -3.269531 3.9375 -3.5625 3.8125 -3.78125 C 3.582031 -4.1875 3.195312 -4.390625 2.65625 -4.390625 C 2.269531 -4.390625 1.941406 -4.25 1.671875 -3.96875 C 1.410156 -3.695312 1.273438 -3.34375 1.265625 -2.90625 Z M 2.625 -5.140625 Z M 2.625 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="57b18eff-5b5d-4232-adf3-c7189f058183">
<path style="stroke:none;" d=""/>
</symbol>
<symbol overflow="visible" id="c21db32d-b24a-445b-a1fe-14a9cadd0e36">
<path style="stroke:none;" d="M 3.3125 -3.96875 C 3.707031 -3.96875 4.015625 -4.023438 4.234375 -4.140625 C 4.578125 -4.304688 4.75 -4.613281 4.75 -5.0625 C 4.75 -5.507812 4.566406 -5.8125 4.203125 -5.96875 C 3.992188 -6.0625 3.6875 -6.109375 3.28125 -6.109375 L 1.625 -6.109375 L 1.625 -3.96875 Z M 3.625 -0.796875 C 4.195312 -0.796875 4.609375 -0.960938 4.859375 -1.296875 C 5.003906 -1.503906 5.078125 -1.753906 5.078125 -2.046875 C 5.078125 -2.546875 4.851562 -2.890625 4.40625 -3.078125 C 4.175781 -3.171875 3.863281 -3.21875 3.46875 -3.21875 L 1.625 -3.21875 L 1.625 -0.796875 Z M 0.703125 -6.875 L 3.65625 -6.875 C 4.46875 -6.875 5.039062 -6.632812 5.375 -6.15625 C 5.582031 -5.875 5.6875 -5.546875 5.6875 -5.171875 C 5.6875 -4.742188 5.5625 -4.390625 5.3125 -4.109375 C 5.1875 -3.960938 5.003906 -3.828125 4.765625 -3.703125 C 5.109375 -3.566406 5.367188 -3.414062 5.546875 -3.25 C 5.859375 -2.945312 6.015625 -2.535156 6.015625 -2.015625 C 6.015625 -1.566406 5.875 -1.164062 5.59375 -0.8125 C 5.175781 -0.269531 4.515625 0 3.609375 0 L 0.703125 0 Z M 0.703125 -6.875 "/>
</symbol>
<symbol overflow="visible" id="c6d1eef7-4221-45eb-a0c4-949e09fb587f">
<path style="stroke:none;" d="M 2.609375 -0.546875 C 3.171875 -0.546875 3.554688 -0.753906 3.765625 -1.171875 C 3.972656 -1.597656 4.078125 -2.070312 4.078125 -2.59375 C 4.078125 -3.0625 4 -3.441406 3.84375 -3.734375 C 3.601562 -4.191406 3.195312 -4.421875 2.625 -4.421875 C 2.101562 -4.421875 1.722656 -4.222656 1.484375 -3.828125 C 1.253906 -3.441406 1.140625 -2.96875 1.140625 -2.40625 C 1.140625 -1.875 1.253906 -1.429688 1.484375 -1.078125 C 1.722656 -0.722656 2.097656 -0.546875 2.609375 -0.546875 Z M 2.640625 -5.15625 C 3.285156 -5.15625 3.832031 -4.941406 4.28125 -4.515625 C 4.726562 -4.085938 4.953125 -3.453125 4.953125 -2.609375 C 4.953125 -1.804688 4.753906 -1.140625 4.359375 -0.609375 C 3.960938 -0.078125 3.351562 0.1875 2.53125 0.1875 C 1.84375 0.1875 1.296875 -0.046875 0.890625 -0.515625 C 0.484375 -0.984375 0.28125 -1.613281 0.28125 -2.40625 C 0.28125 -3.238281 0.492188 -3.90625 0.921875 -4.40625 C 1.347656 -4.90625 1.921875 -5.15625 2.640625 -5.15625 Z M 2.609375 -5.140625 Z M 2.609375 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="3b53d5af-88b1-49de-898a-8ab1c5f91df8">
<path style="stroke:none;" d="M 0.59375 -6.875 L 1.40625 -6.875 L 1.40625 -2.890625 L 3.578125 -5.015625 L 4.65625 -5.015625 L 2.734375 -3.140625 L 4.765625 0 L 3.6875 0 L 2.125 -2.53125 L 1.40625 -1.890625 L 1.40625 0 L 0.59375 0 Z M 0.59375 -6.875 "/>
</symbol>
<symbol overflow="visible" id="1615171e-04fc-4eda-94b2-ba5c1c136930">
<path style="stroke:none;" d="M 0.828125 -5.78125 C 0.835938 -6.132812 0.898438 -6.390625 1.015625 -6.546875 C 1.210938 -6.835938 1.59375 -6.984375 2.15625 -6.984375 C 2.207031 -6.984375 2.257812 -6.976562 2.3125 -6.96875 C 2.375 -6.96875 2.4375 -6.96875 2.5 -6.96875 L 2.5 -6.1875 C 2.414062 -6.195312 2.351562 -6.203125 2.3125 -6.203125 C 2.28125 -6.203125 2.242188 -6.203125 2.203125 -6.203125 C 1.953125 -6.203125 1.800781 -6.132812 1.75 -6 C 1.695312 -5.875 1.671875 -5.539062 1.671875 -5 L 2.5 -5 L 2.5 -4.328125 L 1.65625 -4.328125 L 1.65625 0 L 0.828125 0 L 0.828125 -4.328125 L 0.125 -4.328125 L 0.125 -5 L 0.828125 -5 Z M 0.828125 -5.78125 "/>
</symbol>
<symbol overflow="visible" id="0691f01a-4f9b-4509-a0f7-1b3ca7e5be1f">
<path style="stroke:none;" d="M 0.703125 -6.875 L 2.046875 -6.875 L 4.015625 -1.0625 L 5.984375 -6.875 L 7.296875 -6.875 L 7.296875 0 L 6.421875 0 L 6.421875 -4.0625 C 6.421875 -4.195312 6.421875 -4.425781 6.421875 -4.75 C 6.429688 -5.082031 6.4375 -5.429688 6.4375 -5.796875 L 4.46875 0 L 3.546875 0 L 1.578125 -5.796875 L 1.578125 -5.59375 C 1.578125 -5.425781 1.578125 -5.171875 1.578125 -4.828125 C 1.585938 -4.484375 1.59375 -4.226562 1.59375 -4.0625 L 1.59375 0 L 0.703125 0 Z M 0.703125 -6.875 "/>
</symbol>
<symbol overflow="visible" id="22145fa8-a45c-442e-b087-71214dc5c528">
<path style="stroke:none;" d="M 0.640625 -5.015625 L 1.4375 -5.015625 L 1.4375 -4.15625 C 1.507812 -4.320312 1.671875 -4.523438 1.921875 -4.765625 C 2.179688 -5.003906 2.476562 -5.125 2.8125 -5.125 C 2.820312 -5.125 2.847656 -5.125 2.890625 -5.125 C 2.929688 -5.125 2.992188 -5.117188 3.078125 -5.109375 L 3.078125 -4.21875 C 3.023438 -4.226562 2.976562 -4.234375 2.9375 -4.234375 C 2.894531 -4.234375 2.851562 -4.234375 2.8125 -4.234375 C 2.382812 -4.234375 2.054688 -4.097656 1.828125 -3.828125 C 1.597656 -3.554688 1.484375 -3.242188 1.484375 -2.890625 L 1.484375 0 L 0.640625 0 Z M 0.640625 -5.015625 "/>
</symbol>
<symbol overflow="visible" id="857ce6f1-780e-4daa-8abc-abd87153a886">
<path style="stroke:none;" d="M 0.625 -5.015625 L 1.453125 -5.015625 L 1.453125 -4.3125 C 1.648438 -4.550781 1.832031 -4.726562 2 -4.84375 C 2.269531 -5.03125 2.582031 -5.125 2.9375 -5.125 C 3.34375 -5.125 3.664062 -5.023438 3.90625 -4.828125 C 4.039062 -4.722656 4.164062 -4.5625 4.28125 -4.34375 C 4.46875 -4.601562 4.6875 -4.796875 4.9375 -4.921875 C 5.195312 -5.054688 5.484375 -5.125 5.796875 -5.125 C 6.472656 -5.125 6.929688 -4.882812 7.171875 -4.40625 C 7.304688 -4.132812 7.375 -3.78125 7.375 -3.34375 L 7.375 0 L 6.5 0 L 6.5 -3.484375 C 6.5 -3.816406 6.410156 -4.046875 6.234375 -4.171875 C 6.066406 -4.296875 5.863281 -4.359375 5.625 -4.359375 C 5.300781 -4.359375 5.019531 -4.25 4.78125 -4.03125 C 4.539062 -3.8125 4.421875 -3.441406 4.421875 -2.921875 L 4.421875 0 L 3.5625 0 L 3.5625 -3.28125 C 3.5625 -3.613281 3.519531 -3.859375 3.4375 -4.015625 C 3.3125 -4.253906 3.070312 -4.375 2.71875 -4.375 C 2.40625 -4.375 2.117188 -4.25 1.859375 -4 C 1.597656 -3.75 1.46875 -3.300781 1.46875 -2.65625 L 1.46875 0 L 0.625 0 Z M 0.625 -5.015625 "/>
</symbol>
<symbol overflow="visible" id="df47b663-6ef6-46cd-a0e8-6990cadc8cf6">
<path style="stroke:none;" d="M 0.625 -5.015625 L 1.421875 -5.015625 L 1.421875 -4.3125 C 1.660156 -4.601562 1.910156 -4.8125 2.171875 -4.9375 C 2.441406 -5.0625 2.738281 -5.125 3.0625 -5.125 C 3.769531 -5.125 4.25 -4.878906 4.5 -4.390625 C 4.632812 -4.117188 4.703125 -3.726562 4.703125 -3.21875 L 4.703125 0 L 3.84375 0 L 3.84375 -3.15625 C 3.84375 -3.46875 3.800781 -3.71875 3.71875 -3.90625 C 3.5625 -4.21875 3.289062 -4.375 2.90625 -4.375 C 2.695312 -4.375 2.53125 -4.351562 2.40625 -4.3125 C 2.175781 -4.238281 1.972656 -4.097656 1.796875 -3.890625 C 1.660156 -3.734375 1.570312 -3.566406 1.53125 -3.390625 C 1.488281 -3.210938 1.46875 -2.957031 1.46875 -2.625 L 1.46875 0 L 0.625 0 Z M 2.59375 -5.140625 Z M 2.59375 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="e3ad6941-ec3e-46a4-abde-7e65550a378c">
<path style="stroke:none;" d="M 3.375 -0.796875 C 3.6875 -0.796875 3.945312 -0.828125 4.15625 -0.890625 C 4.507812 -1.015625 4.804688 -1.25 5.046875 -1.59375 C 5.222656 -1.875 5.351562 -2.234375 5.4375 -2.671875 C 5.488281 -2.921875 5.515625 -3.160156 5.515625 -3.390625 C 5.515625 -4.242188 5.34375 -4.90625 5 -5.375 C 4.664062 -5.84375 4.117188 -6.078125 3.359375 -6.078125 L 1.703125 -6.078125 L 1.703125 -0.796875 Z M 0.765625 -6.875 L 3.5625 -6.875 C 4.507812 -6.875 5.242188 -6.539062 5.765625 -5.875 C 6.222656 -5.269531 6.453125 -4.492188 6.453125 -3.546875 C 6.453125 -2.816406 6.316406 -2.15625 6.046875 -1.5625 C 5.566406 -0.519531 4.734375 0 3.546875 0 L 0.765625 0 Z M 0.765625 -6.875 "/>
</symbol>
<symbol overflow="visible" id="07d61c43-94b5-4efe-941c-12e6dfafc433">
<path style="stroke:none;" d="M 1.265625 -1.328125 C 1.265625 -1.085938 1.351562 -0.894531 1.53125 -0.75 C 1.707031 -0.613281 1.921875 -0.546875 2.171875 -0.546875 C 2.460938 -0.546875 2.75 -0.613281 3.03125 -0.75 C 3.5 -0.976562 3.734375 -1.351562 3.734375 -1.875 L 3.734375 -2.546875 C 3.628906 -2.484375 3.492188 -2.429688 3.328125 -2.390625 C 3.171875 -2.347656 3.015625 -2.316406 2.859375 -2.296875 L 2.34375 -2.234375 C 2.039062 -2.191406 1.8125 -2.125 1.65625 -2.03125 C 1.394531 -1.882812 1.265625 -1.648438 1.265625 -1.328125 Z M 3.3125 -3.046875 C 3.5 -3.066406 3.628906 -3.144531 3.703125 -3.28125 C 3.734375 -3.351562 3.75 -3.460938 3.75 -3.609375 C 3.75 -3.890625 3.644531 -4.09375 3.4375 -4.21875 C 3.238281 -4.351562 2.945312 -4.421875 2.5625 -4.421875 C 2.125 -4.421875 1.8125 -4.304688 1.625 -4.078125 C 1.519531 -3.941406 1.453125 -3.742188 1.421875 -3.484375 L 0.640625 -3.484375 C 0.660156 -4.097656 0.863281 -4.523438 1.25 -4.765625 C 1.632812 -5.015625 2.078125 -5.140625 2.578125 -5.140625 C 3.171875 -5.140625 3.65625 -5.023438 4.03125 -4.796875 C 4.394531 -4.578125 4.578125 -4.226562 4.578125 -3.75 L 4.578125 -0.859375 C 4.578125 -0.773438 4.59375 -0.707031 4.625 -0.65625 C 4.664062 -0.601562 4.742188 -0.578125 4.859375 -0.578125 C 4.890625 -0.578125 4.925781 -0.578125 4.96875 -0.578125 C 5.019531 -0.578125 5.070312 -0.582031 5.125 -0.59375 L 5.125 0.015625 C 5 0.0546875 4.898438 0.0820312 4.828125 0.09375 C 4.765625 0.101562 4.671875 0.109375 4.546875 0.109375 C 4.253906 0.109375 4.046875 0.00390625 3.921875 -0.203125 C 3.847656 -0.304688 3.796875 -0.460938 3.765625 -0.671875 C 3.597656 -0.441406 3.351562 -0.242188 3.03125 -0.078125 C 2.707031 0.0859375 2.351562 0.171875 1.96875 0.171875 C 1.5 0.171875 1.117188 0.03125 0.828125 -0.25 C 0.535156 -0.53125 0.390625 -0.882812 0.390625 -1.3125 C 0.390625 -1.78125 0.53125 -2.140625 0.8125 -2.390625 C 1.101562 -2.648438 1.488281 -2.8125 1.96875 -2.875 Z M 2.609375 -5.140625 Z M 2.609375 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="b8d2e071-2217-4d8d-a77b-85b7b56affdc">
<path style="stroke:none;" d="M 2.734375 -0.5625 C 3.128906 -0.5625 3.457031 -0.726562 3.71875 -1.0625 C 3.976562 -1.394531 4.109375 -1.882812 4.109375 -2.53125 C 4.109375 -2.9375 4.050781 -3.28125 3.9375 -3.5625 C 3.71875 -4.125 3.316406 -4.40625 2.734375 -4.40625 C 2.148438 -4.40625 1.75 -4.109375 1.53125 -3.515625 C 1.414062 -3.203125 1.359375 -2.804688 1.359375 -2.328125 C 1.359375 -1.941406 1.414062 -1.613281 1.53125 -1.34375 C 1.75 -0.820312 2.148438 -0.5625 2.734375 -0.5625 Z M 0.546875 -5 L 1.375 -5 L 1.375 -4.328125 C 1.539062 -4.554688 1.722656 -4.734375 1.921875 -4.859375 C 2.210938 -5.046875 2.546875 -5.140625 2.921875 -5.140625 C 3.492188 -5.140625 3.976562 -4.921875 4.375 -4.484375 C 4.769531 -4.046875 4.96875 -3.425781 4.96875 -2.625 C 4.96875 -1.53125 4.679688 -0.75 4.109375 -0.28125 C 3.742188 0.0195312 3.320312 0.171875 2.84375 0.171875 C 2.46875 0.171875 2.148438 0.0859375 1.890625 -0.078125 C 1.742188 -0.171875 1.578125 -0.332031 1.390625 -0.5625 L 1.390625 2 L 0.546875 2 Z M 0.546875 -5 "/>
</symbol>
<symbol overflow="visible" id="ce83838c-a289-4795-9394-f874b0069e6c">
<path style="stroke:none;" d="M 1.15625 -2.453125 C 1.15625 -1.910156 1.269531 -1.457031 1.5 -1.09375 C 1.726562 -0.738281 2.09375 -0.5625 2.59375 -0.5625 C 2.976562 -0.5625 3.296875 -0.726562 3.546875 -1.0625 C 3.804688 -1.394531 3.9375 -1.875 3.9375 -2.5 C 3.9375 -3.132812 3.804688 -3.601562 3.546875 -3.90625 C 3.285156 -4.21875 2.960938 -4.375 2.578125 -4.375 C 2.148438 -4.375 1.804688 -4.207031 1.546875 -3.875 C 1.285156 -3.550781 1.15625 -3.078125 1.15625 -2.453125 Z M 2.421875 -5.109375 C 2.804688 -5.109375 3.128906 -5.023438 3.390625 -4.859375 C 3.535156 -4.765625 3.703125 -4.601562 3.890625 -4.375 L 3.890625 -6.90625 L 4.703125 -6.90625 L 4.703125 0 L 3.953125 0 L 3.953125 -0.703125 C 3.753906 -0.390625 3.519531 -0.164062 3.25 -0.03125 C 2.976562 0.101562 2.671875 0.171875 2.328125 0.171875 C 1.765625 0.171875 1.28125 -0.0625 0.875 -0.53125 C 0.46875 -1 0.265625 -1.625 0.265625 -2.40625 C 0.265625 -3.132812 0.445312 -3.765625 0.8125 -4.296875 C 1.1875 -4.835938 1.722656 -5.109375 2.421875 -5.109375 Z M 2.421875 -5.109375 "/>
</symbol>
<symbol overflow="visible" id="e93771c4-04ea-4a52-adb6-a6d63904d72a">
<path style="stroke:none;" d="M 0.75 -6.875 L 1.703125 -6.875 L 1.703125 -4.03125 L 5.28125 -4.03125 L 5.28125 -6.875 L 6.21875 -6.875 L 6.21875 0 L 5.28125 0 L 5.28125 -3.21875 L 1.703125 -3.21875 L 1.703125 0 L 0.75 0 Z M 0.75 -6.875 "/>
</symbol>
<symbol overflow="visible" id="f40ca2db-a00f-472a-8894-e4ed273b2c73">
<path style="stroke:none;" d="M 0.640625 -6.875 L 1.484375 -6.875 L 1.484375 0 L 0.640625 0 Z M 0.640625 -6.875 "/>
</symbol>
<symbol overflow="visible" id="c2df39ed-2692-4a38-9f0c-d07ce50a552c">
<path style="stroke:none;" d="M 3.75 -5.015625 L 4.6875 -5.015625 C 4.5625 -4.691406 4.296875 -3.957031 3.890625 -2.8125 C 3.585938 -1.945312 3.332031 -1.242188 3.125 -0.703125 C 2.632812 0.578125 2.289062 1.359375 2.09375 1.640625 C 1.894531 1.921875 1.550781 2.0625 1.0625 2.0625 C 0.945312 2.0625 0.851562 2.054688 0.78125 2.046875 C 0.71875 2.035156 0.640625 2.015625 0.546875 1.984375 L 0.546875 1.21875 C 0.691406 1.257812 0.796875 1.285156 0.859375 1.296875 C 0.929688 1.304688 0.992188 1.3125 1.046875 1.3125 C 1.203125 1.3125 1.316406 1.285156 1.390625 1.234375 C 1.460938 1.179688 1.523438 1.117188 1.578125 1.046875 C 1.585938 1.015625 1.640625 0.882812 1.734375 0.65625 C 1.835938 0.425781 1.910156 0.253906 1.953125 0.140625 L 0.09375 -5.015625 L 1.046875 -5.015625 L 2.40625 -0.9375 Z M 2.390625 -5.140625 Z M 2.390625 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="393595dc-7276-4c55-92ea-2876b048911a">
<path style="stroke:none;" d="M 0.625 -5 L 1.46875 -5 L 1.46875 0 L 0.625 0 Z M 0.625 -6.875 L 1.46875 -6.875 L 1.46875 -5.921875 L 0.625 -5.921875 Z M 0.625 -6.875 "/>
</symbol>
<symbol overflow="visible" id="0b3efa9d-0428-40c6-b740-44e39cf37874">
<path style="stroke:none;" d="M 0.546875 -6.90625 L 1.375 -6.90625 L 1.375 -4.40625 C 1.5625 -4.644531 1.78125 -4.828125 2.03125 -4.953125 C 2.289062 -5.078125 2.566406 -5.140625 2.859375 -5.140625 C 3.484375 -5.140625 3.988281 -4.925781 4.375 -4.5 C 4.769531 -4.070312 4.96875 -3.441406 4.96875 -2.609375 C 4.96875 -1.816406 4.773438 -1.15625 4.390625 -0.625 C 4.003906 -0.101562 3.472656 0.15625 2.796875 0.15625 C 2.410156 0.15625 2.085938 0.0664062 1.828125 -0.109375 C 1.671875 -0.222656 1.503906 -0.398438 1.328125 -0.640625 L 1.328125 0 L 0.546875 0 Z M 2.75 -0.578125 C 3.195312 -0.578125 3.535156 -0.757812 3.765625 -1.125 C 3.992188 -1.488281 4.109375 -1.96875 4.109375 -2.5625 C 4.109375 -3.09375 3.992188 -3.53125 3.765625 -3.875 C 3.535156 -4.21875 3.203125 -4.390625 2.765625 -4.390625 C 2.378906 -4.390625 2.039062 -4.25 1.75 -3.96875 C 1.46875 -3.6875 1.328125 -3.21875 1.328125 -2.5625 C 1.328125 -2.09375 1.382812 -1.710938 1.5 -1.421875 C 1.726562 -0.859375 2.144531 -0.578125 2.75 -0.578125 Z M 2.75 -0.578125 "/>
</symbol>
<symbol overflow="visible" id="cfb32a16-a1ce-41d8-a0f4-973421ca8435">
<path style="stroke:none;" d="M 4.109375 -2.046875 C 4.109375 -1.472656 4.019531 -1.023438 3.84375 -0.703125 C 3.53125 -0.109375 2.925781 0.1875 2.03125 0.1875 C 1.519531 0.1875 1.078125 0.046875 0.703125 -0.234375 C 0.335938 -0.515625 0.15625 -1.015625 0.15625 -1.734375 L 0.15625 -2.21875 L 1.046875 -2.21875 L 1.046875 -1.734375 C 1.046875 -1.359375 1.128906 -1.070312 1.296875 -0.875 C 1.460938 -0.6875 1.722656 -0.59375 2.078125 -0.59375 C 2.566406 -0.59375 2.890625 -0.765625 3.046875 -1.109375 C 3.140625 -1.316406 3.1875 -1.710938 3.1875 -2.296875 L 3.1875 -6.875 L 4.109375 -6.875 Z M 4.109375 -2.046875 "/>
</symbol>
<symbol overflow="visible" id="ede5b5f9-4726-405a-a246-476addbf1412">
<path style="stroke:none;" d="M 1 -5.015625 L 1.96875 -1.0625 L 2.953125 -5.015625 L 3.890625 -5.015625 L 4.875 -1.09375 L 5.90625 -5.015625 L 6.75 -5.015625 L 5.296875 0 L 4.421875 0 L 3.390625 -3.890625 L 2.40625 0 L 1.53125 0 L 0.078125 -5.015625 Z M 1 -5.015625 "/>
</symbol>
<symbol overflow="visible" id="6f7ae1f5-9c92-4ea2-b134-868cd7d53483">
<path style="stroke:none;" d="M 1.125 -1.578125 C 1.144531 -1.296875 1.210938 -1.078125 1.328125 -0.921875 C 1.546875 -0.648438 1.914062 -0.515625 2.4375 -0.515625 C 2.75 -0.515625 3.019531 -0.582031 3.25 -0.71875 C 3.488281 -0.851562 3.609375 -1.066406 3.609375 -1.359375 C 3.609375 -1.566406 3.515625 -1.726562 3.328125 -1.84375 C 3.203125 -1.914062 2.960938 -1.992188 2.609375 -2.078125 L 1.9375 -2.25 C 1.507812 -2.351562 1.195312 -2.472656 1 -2.609375 C 0.632812 -2.835938 0.453125 -3.15625 0.453125 -3.5625 C 0.453125 -4.03125 0.617188 -4.410156 0.953125 -4.703125 C 1.296875 -4.992188 1.757812 -5.140625 2.34375 -5.140625 C 3.09375 -5.140625 3.640625 -4.921875 3.984375 -4.484375 C 4.191406 -4.203125 4.289062 -3.898438 4.28125 -3.578125 L 3.484375 -3.578125 C 3.472656 -3.765625 3.40625 -3.9375 3.28125 -4.09375 C 3.09375 -4.3125 2.757812 -4.421875 2.28125 -4.421875 C 1.957031 -4.421875 1.710938 -4.359375 1.546875 -4.234375 C 1.390625 -4.117188 1.3125 -3.960938 1.3125 -3.765625 C 1.3125 -3.546875 1.414062 -3.367188 1.625 -3.234375 C 1.75 -3.160156 1.9375 -3.09375 2.1875 -3.03125 L 2.734375 -2.890625 C 3.347656 -2.742188 3.753906 -2.601562 3.953125 -2.46875 C 4.285156 -2.25 4.453125 -1.910156 4.453125 -1.453125 C 4.453125 -1.003906 4.28125 -0.617188 3.9375 -0.296875 C 3.601562 0.0234375 3.085938 0.1875 2.390625 0.1875 C 1.648438 0.1875 1.125 0.0195312 0.8125 -0.3125 C 0.5 -0.65625 0.332031 -1.078125 0.3125 -1.578125 Z M 2.359375 -5.140625 Z M 2.359375 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="c98524f6-9574-4eef-ae06-783b867aa52f">
<path style="stroke:none;" d="M 1.34375 -2.21875 C 1.363281 -1.832031 1.453125 -1.515625 1.609375 -1.265625 C 1.921875 -0.804688 2.46875 -0.578125 3.25 -0.578125 C 3.601562 -0.578125 3.921875 -0.628906 4.203125 -0.734375 C 4.765625 -0.929688 5.046875 -1.28125 5.046875 -1.78125 C 5.046875 -2.15625 4.925781 -2.421875 4.6875 -2.578125 C 4.445312 -2.734375 4.078125 -2.867188 3.578125 -2.984375 L 2.640625 -3.1875 C 2.035156 -3.332031 1.601562 -3.488281 1.34375 -3.65625 C 0.90625 -3.9375 0.6875 -4.363281 0.6875 -4.9375 C 0.6875 -5.550781 0.898438 -6.054688 1.328125 -6.453125 C 1.765625 -6.859375 2.375 -7.0625 3.15625 -7.0625 C 3.875 -7.0625 4.484375 -6.882812 4.984375 -6.53125 C 5.492188 -6.1875 5.75 -5.628906 5.75 -4.859375 L 4.875 -4.859375 C 4.820312 -5.234375 4.722656 -5.515625 4.578125 -5.703125 C 4.285156 -6.066406 3.800781 -6.25 3.125 -6.25 C 2.570312 -6.25 2.175781 -6.132812 1.9375 -5.90625 C 1.695312 -5.675781 1.578125 -5.40625 1.578125 -5.09375 C 1.578125 -4.757812 1.71875 -4.515625 2 -4.359375 C 2.1875 -4.253906 2.601562 -4.128906 3.25 -3.984375 L 4.21875 -3.765625 C 4.675781 -3.660156 5.035156 -3.515625 5.296875 -3.328125 C 5.734375 -3.003906 5.953125 -2.535156 5.953125 -1.921875 C 5.953125 -1.160156 5.671875 -0.613281 5.109375 -0.28125 C 4.554688 0.0390625 3.914062 0.203125 3.1875 0.203125 C 2.332031 0.203125 1.660156 -0.015625 1.171875 -0.453125 C 0.691406 -0.890625 0.457031 -1.476562 0.46875 -2.21875 Z M 3.21875 -7.0625 Z M 3.21875 -7.0625 "/>
</symbol>
<symbol overflow="visible" id="a82b918f-4808-4be8-97d9-430f9c3fd8e4">
<path style="stroke:none;" d="M 2.546875 -5.15625 C 3.117188 -5.15625 3.582031 -5.019531 3.9375 -4.75 C 4.289062 -4.476562 4.503906 -4.003906 4.578125 -3.328125 L 3.75 -3.328125 C 3.695312 -3.640625 3.582031 -3.894531 3.40625 -4.09375 C 3.226562 -4.300781 2.941406 -4.40625 2.546875 -4.40625 C 2.015625 -4.40625 1.632812 -4.144531 1.40625 -3.625 C 1.25 -3.28125 1.171875 -2.859375 1.171875 -2.359375 C 1.171875 -1.859375 1.273438 -1.4375 1.484375 -1.09375 C 1.703125 -0.75 2.039062 -0.578125 2.5 -0.578125 C 2.84375 -0.578125 3.117188 -0.679688 3.328125 -0.890625 C 3.535156 -1.109375 3.675781 -1.40625 3.75 -1.78125 L 4.578125 -1.78125 C 4.484375 -1.113281 4.25 -0.625 3.875 -0.3125 C 3.5 -0.0078125 3.019531 0.140625 2.4375 0.140625 C 1.78125 0.140625 1.253906 -0.0976562 0.859375 -0.578125 C 0.472656 -1.054688 0.28125 -1.65625 0.28125 -2.375 C 0.28125 -3.25 0.492188 -3.929688 0.921875 -4.421875 C 1.347656 -4.910156 1.890625 -5.15625 2.546875 -5.15625 Z M 2.421875 -5.140625 Z M 2.421875 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="8fb04012-17dc-4b05-8af4-bbdcf10cf7f4">
<path style="stroke:none;" d="M 0.78125 -6.421875 L 1.640625 -6.421875 L 1.640625 -5.015625 L 2.4375 -5.015625 L 2.4375 -4.328125 L 1.640625 -4.328125 L 1.640625 -1.046875 C 1.640625 -0.878906 1.695312 -0.765625 1.8125 -0.703125 C 1.882812 -0.671875 1.992188 -0.65625 2.140625 -0.65625 C 2.179688 -0.65625 2.222656 -0.65625 2.265625 -0.65625 C 2.316406 -0.65625 2.375 -0.660156 2.4375 -0.671875 L 2.4375 0 C 2.34375 0.03125 2.242188 0.0507812 2.140625 0.0625 C 2.035156 0.0703125 1.921875 0.078125 1.796875 0.078125 C 1.398438 0.078125 1.128906 -0.0195312 0.984375 -0.21875 C 0.847656 -0.425781 0.78125 -0.6875 0.78125 -1 L 0.78125 -4.328125 L 0.109375 -4.328125 L 0.109375 -5.015625 L 0.78125 -5.015625 Z M 0.78125 -6.421875 "/>
</symbol>
<symbol overflow="visible" id="026e711f-a1e9-4afa-9894-65f3796f7440">
<path style="stroke:none;" d="M 1.46875 -5.015625 L 1.46875 -1.6875 C 1.46875 -1.425781 1.503906 -1.21875 1.578125 -1.0625 C 1.734375 -0.757812 2.015625 -0.609375 2.421875 -0.609375 C 3.003906 -0.609375 3.40625 -0.867188 3.625 -1.390625 C 3.738281 -1.671875 3.796875 -2.054688 3.796875 -2.546875 L 3.796875 -5.015625 L 4.640625 -5.015625 L 4.640625 0 L 3.84375 0 L 3.84375 -0.734375 C 3.738281 -0.546875 3.601562 -0.382812 3.4375 -0.25 C 3.113281 0.0078125 2.722656 0.140625 2.265625 0.140625 C 1.554688 0.140625 1.070312 -0.0976562 0.8125 -0.578125 C 0.664062 -0.835938 0.59375 -1.179688 0.59375 -1.609375 L 0.59375 -5.015625 Z M 2.625 -5.140625 Z M 2.625 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="42eda7bd-9a65-44ce-872a-38f8dbeda5ea">
<path style="stroke:none;" d="M 0.734375 -6.875 L 1.640625 -6.875 L 1.640625 -3.53125 L 5 -6.875 L 6.28125 -6.875 L 3.421875 -4.109375 L 6.359375 0 L 5.140625 0 L 2.734375 -3.453125 L 1.640625 -2.40625 L 1.640625 0 L 0.734375 0 Z M 0.734375 -6.875 "/>
</symbol>
<symbol overflow="visible" id="1221b63e-5ebd-4ef4-8c9c-430a97c34395">
<path style="stroke:none;" d="M 1.28125 -6.875 L 3.25 -1.015625 L 5.203125 -6.875 L 6.25 -6.875 L 3.734375 0 L 2.75 0 L 0.25 -6.875 Z M 1.28125 -6.875 "/>
</symbol>
<symbol overflow="visible" id="bd729ac8-8583-4cb8-ba89-700eabbd6b65">
<path style="stroke:none;" d="M 2.59375 -6.703125 C 3.457031 -6.703125 4.085938 -6.347656 4.484375 -5.640625 C 4.773438 -5.085938 4.921875 -4.328125 4.921875 -3.359375 C 4.921875 -2.453125 4.785156 -1.695312 4.515625 -1.09375 C 4.128906 -0.238281 3.488281 0.1875 2.59375 0.1875 C 1.78125 0.1875 1.179688 -0.160156 0.796875 -0.859375 C 0.460938 -1.453125 0.296875 -2.238281 0.296875 -3.21875 C 0.296875 -3.976562 0.394531 -4.632812 0.59375 -5.1875 C 0.957031 -6.195312 1.625 -6.703125 2.59375 -6.703125 Z M 2.578125 -0.578125 C 3.015625 -0.578125 3.363281 -0.769531 3.625 -1.15625 C 3.882812 -1.550781 4.015625 -2.273438 4.015625 -3.328125 C 4.015625 -4.085938 3.921875 -4.710938 3.734375 -5.203125 C 3.546875 -5.703125 3.179688 -5.953125 2.640625 -5.953125 C 2.148438 -5.953125 1.789062 -5.71875 1.5625 -5.25 C 1.332031 -4.78125 1.21875 -4.09375 1.21875 -3.1875 C 1.21875 -2.5 1.289062 -1.945312 1.4375 -1.53125 C 1.65625 -0.894531 2.035156 -0.578125 2.578125 -0.578125 Z M 2.578125 -0.578125 "/>
</symbol>
<symbol overflow="visible" id="0ea4294d-30a7-49b0-8b1b-62e9568bafa2">
<path style="stroke:none;" d="M 0.8125 -1.015625 L 1.796875 -1.015625 L 1.796875 0 L 0.8125 0 Z M 0.8125 -1.015625 "/>
</symbol>
<symbol overflow="visible" id="058b580f-461d-4127-af7d-0f7c6304ddd0">
<path style="stroke:none;" d="M 1.1875 -1.703125 C 1.238281 -1.222656 1.460938 -0.894531 1.859375 -0.71875 C 2.054688 -0.625 2.285156 -0.578125 2.546875 -0.578125 C 3.046875 -0.578125 3.414062 -0.734375 3.65625 -1.046875 C 3.894531 -1.367188 4.015625 -1.722656 4.015625 -2.109375 C 4.015625 -2.578125 3.867188 -2.9375 3.578125 -3.1875 C 3.296875 -3.445312 2.957031 -3.578125 2.5625 -3.578125 C 2.269531 -3.578125 2.019531 -3.519531 1.8125 -3.40625 C 1.601562 -3.289062 1.425781 -3.132812 1.28125 -2.9375 L 0.546875 -2.984375 L 1.0625 -6.59375 L 4.546875 -6.59375 L 4.546875 -5.78125 L 1.703125 -5.78125 L 1.40625 -3.921875 C 1.5625 -4.035156 1.710938 -4.125 1.859375 -4.1875 C 2.109375 -4.289062 2.394531 -4.34375 2.71875 -4.34375 C 3.332031 -4.34375 3.851562 -4.140625 4.28125 -3.734375 C 4.707031 -3.335938 4.921875 -2.835938 4.921875 -2.234375 C 4.921875 -1.597656 4.722656 -1.035156 4.328125 -0.546875 C 3.941406 -0.0664062 3.320312 0.171875 2.46875 0.171875 C 1.914062 0.171875 1.429688 0.0195312 1.015625 -0.28125 C 0.597656 -0.59375 0.363281 -1.066406 0.3125 -1.703125 Z M 1.1875 -1.703125 "/>
</symbol>
<symbol overflow="visible" id="cc3cf413-77e0-4d52-ba1f-b6e7a6b29b0c">
<path style="stroke:none;" d="M 0.921875 -4.75 L 0.921875 -5.390625 C 1.523438 -5.453125 1.945312 -5.550781 2.1875 -5.6875 C 2.425781 -5.832031 2.609375 -6.164062 2.734375 -6.6875 L 3.390625 -6.6875 L 3.390625 0 L 2.5 0 L 2.5 -4.75 Z M 0.921875 -4.75 "/>
</symbol>
<symbol overflow="visible" id="dbd8936a-1341-4072-b754-d0739b9eba04">
<path style="stroke:none;" d="M 0.296875 0 C 0.328125 -0.570312 0.445312 -1.070312 0.65625 -1.5 C 0.863281 -1.9375 1.269531 -2.328125 1.875 -2.671875 L 2.765625 -3.1875 C 3.171875 -3.425781 3.457031 -3.628906 3.625 -3.796875 C 3.875 -4.054688 4 -4.351562 4 -4.6875 C 4 -5.070312 3.878906 -5.378906 3.640625 -5.609375 C 3.410156 -5.835938 3.101562 -5.953125 2.71875 -5.953125 C 2.132812 -5.953125 1.734375 -5.734375 1.515625 -5.296875 C 1.398438 -5.066406 1.335938 -4.742188 1.328125 -4.328125 L 0.46875 -4.328125 C 0.476562 -4.910156 0.582031 -5.382812 0.78125 -5.75 C 1.144531 -6.40625 1.789062 -6.734375 2.71875 -6.734375 C 3.488281 -6.734375 4.050781 -6.523438 4.40625 -6.109375 C 4.757812 -5.691406 4.9375 -5.226562 4.9375 -4.71875 C 4.9375 -4.1875 4.75 -3.726562 4.375 -3.34375 C 4.15625 -3.125 3.757812 -2.851562 3.1875 -2.53125 L 2.546875 -2.1875 C 2.242188 -2.019531 2.003906 -1.859375 1.828125 -1.703125 C 1.515625 -1.429688 1.316406 -1.128906 1.234375 -0.796875 L 4.90625 -0.796875 L 4.90625 0 Z M 0.296875 0 "/>
</symbol>
<symbol overflow="visible" id="f4f147fe-fd50-4d63-bb63-1aa7350b6291">
<path style="stroke:none;" d="M 0.46875 0 L 0.46875 -10.328125 L 8.671875 -10.328125 L 8.671875 0 Z M 7.375 -1.296875 L 7.375 -9.046875 L 1.765625 -9.046875 L 1.765625 -1.296875 Z M 7.375 -1.296875 "/>
</symbol>
<symbol overflow="visible" id="2591db38-be3f-428a-bbfd-adf67cc57832">
<path style="stroke:none;" d="M 2.015625 -3.328125 C 2.046875 -2.742188 2.179688 -2.269531 2.421875 -1.90625 C 2.890625 -1.21875 3.707031 -0.875 4.875 -0.875 C 5.40625 -0.875 5.882812 -0.953125 6.3125 -1.109375 C 7.144531 -1.398438 7.5625 -1.921875 7.5625 -2.671875 C 7.5625 -3.234375 7.390625 -3.632812 7.046875 -3.875 C 6.679688 -4.101562 6.117188 -4.304688 5.359375 -4.484375 L 3.96875 -4.796875 C 3.050781 -5.003906 2.40625 -5.234375 2.03125 -5.484375 C 1.375 -5.910156 1.046875 -6.554688 1.046875 -7.421875 C 1.046875 -8.347656 1.363281 -9.109375 2 -9.703125 C 2.644531 -10.296875 3.554688 -10.59375 4.734375 -10.59375 C 5.816406 -10.59375 6.734375 -10.332031 7.484375 -9.8125 C 8.242188 -9.289062 8.625 -8.453125 8.625 -7.296875 L 7.3125 -7.296875 C 7.238281 -7.847656 7.085938 -8.273438 6.859375 -8.578125 C 6.429688 -9.117188 5.707031 -9.390625 4.6875 -9.390625 C 3.863281 -9.390625 3.269531 -9.210938 2.90625 -8.859375 C 2.550781 -8.515625 2.375 -8.113281 2.375 -7.65625 C 2.375 -7.144531 2.582031 -6.773438 3 -6.546875 C 3.28125 -6.390625 3.90625 -6.203125 4.875 -5.984375 L 6.328125 -5.65625 C 7.023438 -5.488281 7.566406 -5.269531 7.953125 -5 C 8.609375 -4.507812 8.9375 -3.804688 8.9375 -2.890625 C 8.9375 -1.742188 8.519531 -0.925781 7.6875 -0.4375 C 6.851562 0.0507812 5.882812 0.296875 4.78125 0.296875 C 3.5 0.296875 2.492188 -0.03125 1.765625 -0.6875 C 1.035156 -1.332031 0.679688 -2.210938 0.703125 -3.328125 Z M 4.84375 -10.609375 Z M 4.84375 -10.609375 "/>
</symbol>
<symbol overflow="visible" id="8b04a0c9-b7c5-4bc3-a5ab-4dadd8f1f196">
<path style="stroke:none;" d="M 4.0625 -7.703125 C 4.601562 -7.703125 5.125 -7.578125 5.625 -7.328125 C 6.125 -7.078125 6.503906 -6.753906 6.765625 -6.359375 C 7.015625 -5.972656 7.1875 -5.523438 7.28125 -5.015625 C 7.351562 -4.671875 7.390625 -4.117188 7.390625 -3.359375 L 1.859375 -3.359375 C 1.890625 -2.597656 2.070312 -1.984375 2.40625 -1.515625 C 2.738281 -1.054688 3.257812 -0.828125 3.96875 -0.828125 C 4.632812 -0.828125 5.164062 -1.046875 5.5625 -1.484375 C 5.78125 -1.734375 5.9375 -2.023438 6.03125 -2.359375 L 7.28125 -2.359375 C 7.25 -2.085938 7.140625 -1.78125 6.953125 -1.4375 C 6.765625 -1.09375 6.554688 -0.816406 6.328125 -0.609375 C 5.941406 -0.234375 5.46875 0.0195312 4.90625 0.15625 C 4.601562 0.226562 4.257812 0.265625 3.875 0.265625 C 2.9375 0.265625 2.140625 -0.0703125 1.484375 -0.75 C 0.828125 -1.4375 0.5 -2.394531 0.5 -3.625 C 0.5 -4.832031 0.828125 -5.8125 1.484375 -6.5625 C 2.140625 -7.320312 3 -7.703125 4.0625 -7.703125 Z M 6.078125 -4.375 C 6.023438 -4.914062 5.90625 -5.351562 5.71875 -5.6875 C 5.375 -6.289062 4.796875 -6.59375 3.984375 -6.59375 C 3.398438 -6.59375 2.910156 -6.382812 2.515625 -5.96875 C 2.128906 -5.550781 1.925781 -5.019531 1.90625 -4.375 Z M 3.953125 -7.71875 Z M 3.953125 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="e408f01e-2dd0-4277-8a66-f34c44f3fc29">
<path style="stroke:none;" d="M 0.921875 -7.53125 L 2.125 -7.53125 L 2.125 -6.46875 C 2.488281 -6.90625 2.867188 -7.21875 3.265625 -7.40625 C 3.660156 -7.601562 4.101562 -7.703125 4.59375 -7.703125 C 5.664062 -7.703125 6.390625 -7.328125 6.765625 -6.578125 C 6.960938 -6.171875 7.0625 -5.585938 7.0625 -4.828125 L 7.0625 0 L 5.78125 0 L 5.78125 -4.75 C 5.78125 -5.207031 5.710938 -5.578125 5.578125 -5.859375 C 5.347656 -6.328125 4.941406 -6.5625 4.359375 -6.5625 C 4.054688 -6.5625 3.804688 -6.53125 3.609375 -6.46875 C 3.265625 -6.363281 2.960938 -6.160156 2.703125 -5.859375 C 2.492188 -5.609375 2.351562 -5.347656 2.28125 -5.078125 C 2.21875 -4.816406 2.1875 -4.441406 2.1875 -3.953125 L 2.1875 0 L 0.921875 0 Z M 3.90625 -7.71875 Z M 3.90625 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="a48c4574-ef1e-404d-9acf-53d9027dc574">
<path style="stroke:none;" d="M 1.1875 -9.640625 L 2.46875 -9.640625 L 2.46875 -7.53125 L 3.671875 -7.53125 L 3.671875 -6.5 L 2.46875 -6.5 L 2.46875 -1.578125 C 2.46875 -1.316406 2.554688 -1.144531 2.734375 -1.0625 C 2.828125 -1.007812 2.988281 -0.984375 3.21875 -0.984375 C 3.28125 -0.984375 3.347656 -0.984375 3.421875 -0.984375 C 3.492188 -0.984375 3.578125 -0.988281 3.671875 -1 L 3.671875 0 C 3.523438 0.0390625 3.367188 0.0703125 3.203125 0.09375 C 3.046875 0.113281 2.878906 0.125 2.703125 0.125 C 2.109375 0.125 1.707031 -0.0234375 1.5 -0.328125 C 1.289062 -0.628906 1.1875 -1.023438 1.1875 -1.515625 L 1.1875 -6.5 L 0.15625 -6.5 L 0.15625 -7.53125 L 1.1875 -7.53125 Z M 1.1875 -9.640625 "/>
</symbol>
<symbol overflow="visible" id="cdf227e9-8d94-4779-b30b-a6118845c8c0">
<path style="stroke:none;" d="M 3.828125 -7.75 C 4.679688 -7.75 5.375 -7.539062 5.90625 -7.125 C 6.4375 -6.71875 6.753906 -6.007812 6.859375 -5 L 5.640625 -5 C 5.554688 -5.46875 5.378906 -5.851562 5.109375 -6.15625 C 4.847656 -6.46875 4.421875 -6.625 3.828125 -6.625 C 3.023438 -6.625 2.453125 -6.226562 2.109375 -5.4375 C 1.878906 -4.925781 1.765625 -4.296875 1.765625 -3.546875 C 1.765625 -2.785156 1.921875 -2.144531 2.234375 -1.625 C 2.554688 -1.113281 3.0625 -0.859375 3.75 -0.859375 C 4.269531 -0.859375 4.679688 -1.019531 4.984375 -1.34375 C 5.296875 -1.664062 5.515625 -2.109375 5.640625 -2.671875 L 6.859375 -2.671875 C 6.722656 -1.671875 6.375 -0.9375 5.8125 -0.46875 C 5.25 -0.0078125 4.53125 0.21875 3.65625 0.21875 C 2.664062 0.21875 1.878906 -0.140625 1.296875 -0.859375 C 0.710938 -1.578125 0.421875 -2.476562 0.421875 -3.5625 C 0.421875 -4.882812 0.738281 -5.910156 1.375 -6.640625 C 2.019531 -7.378906 2.835938 -7.75 3.828125 -7.75 Z M 3.640625 -7.71875 Z M 3.640625 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="4c059457-afe6-439a-9d19-a6e2a7f13973">
<path style="stroke:none;" d="M 1.6875 -2.359375 C 1.71875 -1.941406 1.820312 -1.617188 2 -1.390625 C 2.3125 -0.984375 2.863281 -0.78125 3.65625 -0.78125 C 4.125 -0.78125 4.535156 -0.878906 4.890625 -1.078125 C 5.253906 -1.285156 5.4375 -1.601562 5.4375 -2.03125 C 5.4375 -2.351562 5.289062 -2.597656 5 -2.765625 C 4.820312 -2.867188 4.460938 -2.988281 3.921875 -3.125 L 2.90625 -3.390625 C 2.269531 -3.546875 1.796875 -3.722656 1.484375 -3.921875 C 0.941406 -4.265625 0.671875 -4.738281 0.671875 -5.34375 C 0.671875 -6.050781 0.925781 -6.625 1.4375 -7.0625 C 1.957031 -7.507812 2.648438 -7.734375 3.515625 -7.734375 C 4.648438 -7.734375 5.46875 -7.398438 5.96875 -6.734375 C 6.28125 -6.304688 6.429688 -5.847656 6.421875 -5.359375 L 5.234375 -5.359375 C 5.210938 -5.648438 5.113281 -5.910156 4.9375 -6.140625 C 4.644531 -6.472656 4.140625 -6.640625 3.421875 -6.640625 C 2.941406 -6.640625 2.578125 -6.546875 2.328125 -6.359375 C 2.085938 -6.179688 1.96875 -5.945312 1.96875 -5.65625 C 1.96875 -5.320312 2.128906 -5.054688 2.453125 -4.859375 C 2.640625 -4.742188 2.914062 -4.640625 3.28125 -4.546875 L 4.109375 -4.34375 C 5.023438 -4.125 5.632812 -3.910156 5.9375 -3.703125 C 6.4375 -3.378906 6.6875 -2.875 6.6875 -2.1875 C 6.6875 -1.507812 6.429688 -0.925781 5.921875 -0.4375 C 5.410156 0.0390625 4.632812 0.28125 3.59375 0.28125 C 2.46875 0.28125 1.671875 0.03125 1.203125 -0.46875 C 0.742188 -0.976562 0.5 -1.609375 0.46875 -2.359375 Z M 3.546875 -7.71875 Z M 3.546875 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="d57ec852-aedc-4979-a6f4-8d7b13ce9875">
<path style="stroke:none;" d=""/>
</symbol>
<symbol overflow="visible" id="11ebbd4d-41b6-452a-a3b9-cb9f78f58802">
<path style="stroke:none;" d="M 1.515625 -7.53125 L 2.96875 -1.59375 L 4.4375 -7.53125 L 5.859375 -7.53125 L 7.328125 -1.625 L 8.875 -7.53125 L 10.140625 -7.53125 L 7.953125 0 L 6.640625 0 L 5.09375 -5.828125 L 3.609375 0 L 2.296875 0 L 0.125 -7.53125 Z M 1.515625 -7.53125 "/>
</symbol>
<symbol overflow="visible" id="353caa0d-36e5-43c7-82be-ad3ebdf3c679">
<path style="stroke:none;" d="M 0.921875 -7.5 L 2.21875 -7.5 L 2.21875 0 L 0.921875 0 Z M 0.921875 -10.328125 L 2.21875 -10.328125 L 2.21875 -8.890625 L 0.921875 -8.890625 Z M 0.921875 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="fffbcce7-5e4c-4ffb-9d3c-94ac1d37225c">
<path style="stroke:none;" d="M 0.921875 -10.375 L 2.1875 -10.375 L 2.1875 -6.515625 C 2.488281 -6.890625 2.757812 -7.15625 3 -7.3125 C 3.40625 -7.582031 3.914062 -7.71875 4.53125 -7.71875 C 5.625 -7.71875 6.363281 -7.332031 6.75 -6.5625 C 6.957031 -6.144531 7.0625 -5.566406 7.0625 -4.828125 L 7.0625 0 L 5.765625 0 L 5.765625 -4.75 C 5.765625 -5.300781 5.695312 -5.707031 5.5625 -5.96875 C 5.332031 -6.375 4.898438 -6.578125 4.265625 -6.578125 C 3.734375 -6.578125 3.253906 -6.394531 2.828125 -6.03125 C 2.398438 -5.675781 2.1875 -5 2.1875 -4 L 2.1875 0 L 0.921875 0 Z M 0.921875 -10.375 "/>
</symbol>
<symbol overflow="visible" id="5af4611e-989f-4983-b4e6-dca4e48cf9e9">
<path style="stroke:none;" d="M 1.90625 -10.328125 L 4.875 -1.53125 L 7.8125 -10.328125 L 9.390625 -10.328125 L 5.609375 0 L 4.125 0 L 0.359375 -10.328125 Z M 1.90625 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="b1d887fd-26a6-49b7-8426-2647855b1af5">
<path style="stroke:none;" d="M 3.921875 -0.8125 C 4.753906 -0.8125 5.328125 -1.128906 5.640625 -1.765625 C 5.953125 -2.398438 6.109375 -3.109375 6.109375 -3.890625 C 6.109375 -4.585938 6 -5.160156 5.78125 -5.609375 C 5.414062 -6.296875 4.800781 -6.640625 3.9375 -6.640625 C 3.15625 -6.640625 2.585938 -6.34375 2.234375 -5.75 C 1.890625 -5.164062 1.71875 -4.457031 1.71875 -3.625 C 1.71875 -2.820312 1.890625 -2.148438 2.234375 -1.609375 C 2.585938 -1.078125 3.148438 -0.8125 3.921875 -0.8125 Z M 3.96875 -7.75 C 4.9375 -7.75 5.753906 -7.425781 6.421875 -6.78125 C 7.097656 -6.132812 7.4375 -5.179688 7.4375 -3.921875 C 7.4375 -2.710938 7.140625 -1.707031 6.546875 -0.90625 C 5.953125 -0.113281 5.035156 0.28125 3.796875 0.28125 C 2.765625 0.28125 1.941406 -0.0664062 1.328125 -0.765625 C 0.722656 -1.472656 0.421875 -2.421875 0.421875 -3.609375 C 0.421875 -4.867188 0.738281 -5.875 1.375 -6.625 C 2.019531 -7.375 2.882812 -7.75 3.96875 -7.75 Z M 3.921875 -7.71875 Z M 3.921875 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="8ad7a25b-2e63-49e8-ab98-f681bf6aa87c">
<path style="stroke:none;" d="M 0.96875 -10.328125 L 2.234375 -10.328125 L 2.234375 0 L 0.96875 0 Z M 0.96875 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="810a2665-bbb6-4d66-bcdc-0dd9568e8203">
<path style="stroke:none;" d="M 1.78125 -10.328125 L 3.734375 -1.921875 L 6.0625 -10.328125 L 7.578125 -10.328125 L 9.921875 -1.921875 L 11.859375 -10.328125 L 13.40625 -10.328125 L 10.6875 0 L 9.21875 0 L 6.828125 -8.5625 L 4.4375 0 L 2.96875 0 L 0.265625 -10.328125 Z M 1.78125 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="95e5869a-4b2e-4e0c-9368-6ca39692abb9">
<path style="stroke:none;" d="M 0.96875 -7.53125 L 2.171875 -7.53125 L 2.171875 -6.234375 C 2.265625 -6.484375 2.503906 -6.789062 2.890625 -7.15625 C 3.273438 -7.519531 3.71875 -7.703125 4.21875 -7.703125 C 4.238281 -7.703125 4.273438 -7.695312 4.328125 -7.6875 C 4.390625 -7.6875 4.488281 -7.679688 4.625 -7.671875 L 4.625 -6.328125 C 4.550781 -6.347656 4.484375 -6.359375 4.421875 -6.359375 C 4.359375 -6.359375 4.289062 -6.359375 4.21875 -6.359375 C 3.570312 -6.359375 3.078125 -6.15625 2.734375 -5.75 C 2.398438 -5.34375 2.234375 -4.867188 2.234375 -4.328125 L 2.234375 0 L 0.96875 0 Z M 0.96875 -7.53125 "/>
</symbol>
<symbol overflow="visible" id="ff196a4b-4737-45af-a6f3-cd8c5c90c25f">
<path style="stroke:none;" d="M 1.734375 -3.671875 C 1.734375 -2.867188 1.898438 -2.195312 2.234375 -1.65625 C 2.578125 -1.113281 3.128906 -0.84375 3.890625 -0.84375 C 4.472656 -0.84375 4.953125 -1.09375 5.328125 -1.59375 C 5.710938 -2.09375 5.90625 -2.816406 5.90625 -3.765625 C 5.90625 -4.710938 5.707031 -5.414062 5.3125 -5.875 C 4.925781 -6.332031 4.445312 -6.5625 3.875 -6.5625 C 3.238281 -6.5625 2.722656 -6.316406 2.328125 -5.828125 C 1.929688 -5.335938 1.734375 -4.617188 1.734375 -3.671875 Z M 3.640625 -7.671875 C 4.210938 -7.671875 4.691406 -7.546875 5.078125 -7.296875 C 5.304688 -7.160156 5.566406 -6.914062 5.859375 -6.5625 L 5.859375 -10.375 L 7.0625 -10.375 L 7.0625 0 L 5.9375 0 L 5.9375 -1.046875 C 5.632812 -0.585938 5.28125 -0.253906 4.875 -0.046875 C 4.476562 0.160156 4.019531 0.265625 3.5 0.265625 C 2.65625 0.265625 1.925781 -0.0820312 1.3125 -0.78125 C 0.695312 -1.488281 0.390625 -2.429688 0.390625 -3.609375 C 0.390625 -4.703125 0.671875 -5.648438 1.234375 -6.453125 C 1.796875 -7.265625 2.597656 -7.671875 3.640625 -7.671875 Z M 3.640625 -7.671875 "/>
</symbol>
<symbol overflow="visible" id="2c72bf9c-7582-4e2c-997f-8cd506f26c1a">
<path style="stroke:none;" d="M 4.265625 -10.5 C 3.523438 -9.070312 3.046875 -8.019531 2.828125 -7.34375 C 2.492188 -6.3125 2.328125 -5.125 2.328125 -3.78125 C 2.328125 -2.425781 2.515625 -1.1875 2.890625 -0.0625 C 3.128906 0.632812 3.59375 1.632812 4.28125 2.9375 L 3.4375 2.9375 C 2.75 1.875 2.320312 1.191406 2.15625 0.890625 C 1.988281 0.597656 1.8125 0.195312 1.625 -0.3125 C 1.363281 -1 1.179688 -1.738281 1.078125 -2.53125 C 1.023438 -2.9375 1 -3.328125 1 -3.703125 C 1 -5.085938 1.21875 -6.320312 1.65625 -7.40625 C 1.925781 -8.09375 2.503906 -9.125 3.390625 -10.5 Z M 4.265625 -10.5 "/>
</symbol>
<symbol overflow="visible" id="fbf0f2b4-68c4-4e6e-af95-49421d72c4da">
<path style="stroke:none;" d="M 1.09375 -10.328125 L 2.75 -10.328125 L 7.96875 -1.96875 L 7.96875 -10.328125 L 9.296875 -10.328125 L 9.296875 0 L 7.734375 0 L 2.4375 -8.359375 L 2.4375 0 L 1.09375 0 Z M 5.109375 -10.328125 Z M 5.109375 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="ed519697-0544-4696-b68f-4c50db167766">
<path style="stroke:none;" d="M 0.921875 -7.53125 L 2.1875 -7.53125 L 2.1875 -6.46875 C 2.476562 -6.832031 2.75 -7.101562 3 -7.28125 C 3.414062 -7.5625 3.890625 -7.703125 4.421875 -7.703125 C 5.015625 -7.703125 5.492188 -7.554688 5.859375 -7.265625 C 6.066406 -7.085938 6.253906 -6.835938 6.421875 -6.515625 C 6.703125 -6.921875 7.03125 -7.21875 7.40625 -7.40625 C 7.789062 -7.601562 8.222656 -7.703125 8.703125 -7.703125 C 9.710938 -7.703125 10.398438 -7.335938 10.765625 -6.609375 C 10.960938 -6.210938 11.0625 -5.679688 11.0625 -5.015625 L 11.0625 0 L 9.75 0 L 9.75 -5.234375 C 9.75 -5.734375 9.625 -6.078125 9.375 -6.265625 C 9.125 -6.453125 8.816406 -6.546875 8.453125 -6.546875 C 7.953125 -6.546875 7.523438 -6.378906 7.171875 -6.046875 C 6.816406 -5.710938 6.640625 -5.15625 6.640625 -4.375 L 6.640625 0 L 5.34375 0 L 5.34375 -4.921875 C 5.34375 -5.429688 5.28125 -5.800781 5.15625 -6.03125 C 4.96875 -6.382812 4.613281 -6.5625 4.09375 -6.5625 C 3.613281 -6.5625 3.175781 -6.375 2.78125 -6 C 2.382812 -5.632812 2.1875 -4.96875 2.1875 -4 L 2.1875 0 L 0.921875 0 Z M 0.921875 -7.53125 "/>
</symbol>
<symbol overflow="visible" id="becc9896-188c-49f1-8006-34cca4e06782">
<path style="stroke:none;" d="M 1.90625 -2 C 1.90625 -1.632812 2.035156 -1.347656 2.296875 -1.140625 C 2.566406 -0.929688 2.882812 -0.828125 3.25 -0.828125 C 3.695312 -0.828125 4.128906 -0.925781 4.546875 -1.125 C 5.242188 -1.46875 5.59375 -2.03125 5.59375 -2.8125 L 5.59375 -3.828125 C 5.4375 -3.734375 5.238281 -3.648438 5 -3.578125 C 4.757812 -3.515625 4.519531 -3.472656 4.28125 -3.453125 L 3.515625 -3.34375 C 3.054688 -3.28125 2.710938 -3.1875 2.484375 -3.0625 C 2.097656 -2.84375 1.90625 -2.488281 1.90625 -2 Z M 4.96875 -4.5625 C 5.257812 -4.601562 5.453125 -4.726562 5.546875 -4.9375 C 5.609375 -5.039062 5.640625 -5.203125 5.640625 -5.421875 C 5.640625 -5.847656 5.484375 -6.15625 5.171875 -6.34375 C 4.867188 -6.539062 4.429688 -6.640625 3.859375 -6.640625 C 3.191406 -6.640625 2.722656 -6.460938 2.453125 -6.109375 C 2.296875 -5.910156 2.191406 -5.617188 2.140625 -5.234375 L 0.96875 -5.234375 C 0.988281 -6.160156 1.285156 -6.804688 1.859375 -7.171875 C 2.441406 -7.535156 3.117188 -7.71875 3.890625 -7.71875 C 4.773438 -7.71875 5.492188 -7.546875 6.046875 -7.203125 C 6.585938 -6.867188 6.859375 -6.347656 6.859375 -5.640625 L 6.859375 -1.296875 C 6.859375 -1.160156 6.882812 -1.050781 6.9375 -0.96875 C 7 -0.894531 7.113281 -0.859375 7.28125 -0.859375 C 7.34375 -0.859375 7.40625 -0.859375 7.46875 -0.859375 C 7.539062 -0.867188 7.617188 -0.882812 7.703125 -0.90625 L 7.703125 0.03125 C 7.503906 0.09375 7.351562 0.128906 7.25 0.140625 C 7.144531 0.148438 7.003906 0.15625 6.828125 0.15625 C 6.390625 0.15625 6.070312 0.00390625 5.875 -0.296875 C 5.769531 -0.460938 5.695312 -0.695312 5.65625 -1 C 5.40625 -0.664062 5.035156 -0.375 4.546875 -0.125 C 4.066406 0.125 3.535156 0.25 2.953125 0.25 C 2.253906 0.25 1.679688 0.0390625 1.234375 -0.375 C 0.796875 -0.800781 0.578125 -1.335938 0.578125 -1.984375 C 0.578125 -2.679688 0.796875 -3.21875 1.234375 -3.59375 C 1.671875 -3.976562 2.242188 -4.21875 2.953125 -4.3125 Z M 3.921875 -7.71875 Z M 3.921875 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="e2d7f321-37d9-4771-8391-076ba067850b">
<path style="stroke:none;" d="M 0.359375 -1 L 4.828125 -6.40625 L 0.703125 -6.40625 L 0.703125 -7.53125 L 6.53125 -7.53125 L 6.53125 -6.5 L 2.09375 -1.125 L 6.671875 -1.125 L 6.671875 0 L 0.359375 0 Z M 3.625 -7.71875 Z M 3.625 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="194b2940-296a-4a77-8b7a-40287a322f43">
<path style="stroke:none;" d="M 0.5 2.9375 C 1.25 1.488281 1.726562 0.429688 1.9375 -0.234375 C 2.269531 -1.242188 2.4375 -2.425781 2.4375 -3.78125 C 2.4375 -5.132812 2.242188 -6.375 1.859375 -7.5 C 1.628906 -8.195312 1.171875 -9.195312 0.484375 -10.5 L 1.328125 -10.5 C 2.046875 -9.34375 2.484375 -8.628906 2.640625 -8.359375 C 2.796875 -8.097656 2.960938 -7.726562 3.140625 -7.25 C 3.359375 -6.664062 3.515625 -6.085938 3.609375 -5.515625 C 3.710938 -4.941406 3.765625 -4.390625 3.765625 -3.859375 C 3.765625 -2.472656 3.546875 -1.234375 3.109375 -0.140625 C 2.828125 0.554688 2.25 1.582031 1.375 2.9375 Z M 0.5 2.9375 "/>
</symbol>
</g>
<clipPath id="91cb5958-e0fd-43cb-a6f1-8b1d369ddecc">
  <path d="M 124.46875 31.929688 L 454 31.929688 L 454 253 L 124.46875 253 Z M 124.46875 31.929688 "/>
</clipPath>
<clipPath id="c34bdf44-0030-4c0e-b877-f631146c4577">
  <path d="M 176 31.929688 L 178 31.929688 L 178 253 L 176 253 Z M 176 31.929688 "/>
</clipPath>
<clipPath id="c386603f-fa92-4673-8c39-5a3df044fba3">
  <path d="M 251 31.929688 L 252 31.929688 L 252 253 L 251 253 Z M 251 31.929688 "/>
</clipPath>
<clipPath id="30ae2e91-7cad-49e6-b6f9-447373319973">
  <path d="M 326 31.929688 L 327 31.929688 L 327 253 L 326 253 Z M 326 31.929688 "/>
</clipPath>
<clipPath id="9202618f-abca-41cb-9439-4361a36f8b53">
  <path d="M 400 31.929688 L 402 31.929688 L 402 253 L 400 253 Z M 400 31.929688 "/>
</clipPath>
<clipPath id="7b187910-c47f-4de7-a84e-3bb4b0b69ff1">
  <path d="M 124.46875 230 L 454.601562 230 L 454.601562 232 L 124.46875 232 Z M 124.46875 230 "/>
</clipPath>
<clipPath id="052b884d-a133-4c0d-8f50-f4832f99b191">
  <path d="M 124.46875 194 L 454.601562 194 L 454.601562 196 L 124.46875 196 Z M 124.46875 194 "/>
</clipPath>
<clipPath id="57a4403c-1554-49ec-ac53-55bb4eb8af8b">
  <path d="M 124.46875 159 L 454.601562 159 L 454.601562 161 L 124.46875 161 Z M 124.46875 159 "/>
</clipPath>
<clipPath id="7e65281b-c734-4b76-985b-0be31660bcdb">
  <path d="M 124.46875 123 L 454.601562 123 L 454.601562 125 L 124.46875 125 Z M 124.46875 123 "/>
</clipPath>
<clipPath id="6a13e421-509a-4e01-aa46-d448601f758b">
  <path d="M 124.46875 88 L 454.601562 88 L 454.601562 90 L 124.46875 90 Z M 124.46875 88 "/>
</clipPath>
<clipPath id="9837d2fe-b00d-4d59-a2cf-2de85a0a9949">
  <path d="M 124.46875 52 L 454.601562 52 L 454.601562 54 L 124.46875 54 Z M 124.46875 52 "/>
</clipPath>
<clipPath id="8a55bc00-9b0c-4814-b131-3dec0dcc91b1">
  <path d="M 138 31.929688 L 140 31.929688 L 140 253 L 138 253 Z M 138 31.929688 "/>
</clipPath>
<clipPath id="337de2ed-01d0-4db1-9adb-e7fd301d5427">
  <path d="M 213 31.929688 L 215 31.929688 L 215 253 L 213 253 Z M 213 31.929688 "/>
</clipPath>
<clipPath id="bda1062a-0b08-464a-90e2-162f78f8d4b4">
  <path d="M 288 31.929688 L 290 31.929688 L 290 253 L 288 253 Z M 288 31.929688 "/>
</clipPath>
<clipPath id="d312f40c-a7fa-4653-a0af-f4e26480cf33">
  <path d="M 363 31.929688 L 365 31.929688 L 365 253 L 363 253 Z M 363 31.929688 "/>
</clipPath>
<clipPath id="943d1fb3-2e5a-4632-9cf0-e93edd69c68d">
  <path d="M 438 31.929688 L 440 31.929688 L 440 253 L 438 253 Z M 438 31.929688 "/>
</clipPath>
</defs>
<g id="814d7088-9eba-41c6-8b38-ca4dff6237b3">
<rect x="0" y="0" width="468" height="289" style="fill:rgb(100%,100%,100%);fill-opacity:1;stroke:none;"/>
<rect x="0" y="0" width="468" height="289" style="fill:rgb(100%,100%,100%);fill-opacity:1;stroke:none;"/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:round;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 0 289 L 468 289 L 468 0 L 0 0 Z M 0 289 "/>
<g clip-path="url(#91cb5958-e0fd-43cb-a6f1-8b1d369ddecc)" clip-rule="nonzero">
<path style=" stroke:none;fill-rule:nonzero;fill:rgb(89.803922%,89.803922%,89.803922%);fill-opacity:1;" d="M 124.46875 252.027344 L 453.601562 252.027344 L 453.601562 31.925781 L 124.46875 31.925781 Z M 124.46875 252.027344 "/>
</g>
<g clip-path="url(#c34bdf44-0030-4c0e-b877-f631146c4577)" clip-rule="nonzero">
<path style="fill:none;stroke-width:0.531496;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(94.901961%,94.901961%,94.901961%);stroke-opacity:1;stroke-miterlimit:10;" d="M 176.832031 252.027344 L 176.832031 31.929688 "/>
</g>
<g clip-path="url(#c386603f-fa92-4673-8c39-5a3df044fba3)" clip-rule="nonzero">
<path style="fill:none;stroke-width:0.531496;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(94.901961%,94.901961%,94.901961%);stroke-opacity:1;stroke-miterlimit:10;" d="M 251.632812 252.027344 L 251.632812 31.929688 "/>
</g>
<g clip-path="url(#30ae2e91-7cad-49e6-b6f9-447373319973)" clip-rule="nonzero">
<path style="fill:none;stroke-width:0.531496;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(94.901961%,94.901961%,94.901961%);stroke-opacity:1;stroke-miterlimit:10;" d="M 326.4375 252.027344 L 326.4375 31.929688 "/>
</g>
<g clip-path="url(#9202618f-abca-41cb-9439-4361a36f8b53)" clip-rule="nonzero">
<path style="fill:none;stroke-width:0.531496;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(94.901961%,94.901961%,94.901961%);stroke-opacity:1;stroke-miterlimit:10;" d="M 401.238281 252.027344 L 401.238281 31.929688 "/>
</g>
<g clip-path="url(#7b187910-c47f-4de7-a84e-3bb4b0b69ff1)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 230.730469 L 453.601562 230.730469 "/>
</g>
<g clip-path="url(#052b884d-a133-4c0d-8f50-f4832f99b191)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 195.230469 L 453.601562 195.230469 "/>
</g>
<g clip-path="url(#57a4403c-1554-49ec-ac53-55bb4eb8af8b)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 159.730469 L 453.601562 159.730469 "/>
</g>
<g clip-path="url(#7e65281b-c734-4b76-985b-0be31660bcdb)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 124.226562 L 453.601562 124.226562 "/>
</g>
<g clip-path="url(#6a13e421-509a-4e01-aa46-d448601f758b)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 88.726562 L 453.601562 88.726562 "/>
</g>
<g clip-path="url(#9837d2fe-b00d-4d59-a2cf-2de85a0a9949)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 53.226562 L 453.601562 53.226562 "/>
</g>
<g clip-path="url(#8a55bc00-9b0c-4814-b131-3dec0dcc91b1)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 252.027344 L 139.429688 31.929688 "/>
</g>
<g clip-path="url(#337de2ed-01d0-4db1-9adb-e7fd301d5427)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 214.230469 252.027344 L 214.230469 31.929688 "/>
</g>
<g clip-path="url(#bda1062a-0b08-464a-90e2-162f78f8d4b4)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 289.035156 252.027344 L 289.035156 31.929688 "/>
</g>
<g clip-path="url(#d312f40c-a7fa-4653-a0af-f4e26480cf33)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 363.835938 252.027344 L 363.835938 31.929688 "/>
</g>
<g clip-path="url(#943d1fb3-2e5a-4632-9cf0-e93edd69c68d)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 438.640625 252.027344 L 438.640625 31.929688 "/>
</g>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 246.703125 L 342.246094 246.703125 L 342.246094 214.753906 L 139.429688 214.753906 Z M 139.429688 246.703125 "/>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 211.203125 L 376.941406 211.203125 L 376.941406 179.253906 L 139.429688 179.253906 Z M 139.429688 211.203125 "/>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 175.703125 L 245.632812 175.703125 L 245.632812 143.753906 L 139.429688 143.753906 Z M 139.429688 175.703125 "/>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 140.203125 L 268.757812 140.203125 L 268.757812 108.253906 L 139.429688 108.253906 Z M 139.429688 140.203125 "/>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 104.703125 L 286.421875 104.703125 L 286.421875 72.753906 L 139.429688 72.753906 Z M 139.429688 104.703125 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 69.203125 L 139.429688 37.253906 Z M 139.429688 69.203125 "/>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#5c5d006e-d463-4237-90f3-4cfeea901fc0" x="345.792969" y="235.816406"/>
  <use xlink:href="#dcbc5f3d-49cd-4465-993f-f45a255e141e" x="353.674698" y="235.816406"/>
  <use xlink:href="#d61ba169-9977-4c07-a00f-b652e2c3add3" x="357.612106" y="235.816406"/>
  <use xlink:href="#27fef74f-3c4a-4715-b53c-2f1f1afaf2df" x="365.493835" y="235.816406"/>
  <use xlink:href="#d3ae3f82-2764-475e-8d13-26c33b6be63b" x="373.375565" y="235.816406"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#5c5d006e-d463-4237-90f3-4cfeea901fc0" x="272.304688" y="129.3125"/>
  <use xlink:href="#dcbc5f3d-49cd-4465-993f-f45a255e141e" x="280.186417" y="129.3125"/>
  <use xlink:href="#5c5d006e-d463-4237-90f3-4cfeea901fc0" x="284.123825" y="129.3125"/>
  <use xlink:href="#85e9991f-abff-4766-b513-9f561e0651be" x="292.005554" y="129.3125"/>
  <use xlink:href="#d3ae3f82-2764-475e-8d13-26c33b6be63b" x="299.887283" y="129.3125"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#5c5d006e-d463-4237-90f3-4cfeea901fc0" x="289.96875" y="93.8125"/>
  <use xlink:href="#dcbc5f3d-49cd-4465-993f-f45a255e141e" x="297.850479" y="93.8125"/>
  <use xlink:href="#5c5d006e-d463-4237-90f3-4cfeea901fc0" x="301.787888" y="93.8125"/>
  <use xlink:href="#29071ec5-ff69-4f8d-b427-30ecd709e34b" x="309.669617" y="93.8125"/>
  <use xlink:href="#85e9991f-abff-4766-b513-9f561e0651be" x="317.551346" y="93.8125"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#5c5d006e-d463-4237-90f3-4cfeea901fc0" x="380.488281" y="200.316406"/>
  <use xlink:href="#dcbc5f3d-49cd-4465-993f-f45a255e141e" x="388.37001" y="200.316406"/>
  <use xlink:href="#d61ba169-9977-4c07-a00f-b652e2c3add3" x="392.307419" y="200.316406"/>
  <use xlink:href="#6abb19dd-b08d-430d-8d6c-fec5d1934dbc" x="400.189148" y="200.316406"/>
  <use xlink:href="#29071ec5-ff69-4f8d-b427-30ecd709e34b" x="408.070877" y="200.316406"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#5c5d006e-d463-4237-90f3-4cfeea901fc0" x="142.976562" y="58.3125"/>
  <use xlink:href="#dcbc5f3d-49cd-4465-993f-f45a255e141e" x="150.858292" y="58.3125"/>
  <use xlink:href="#5c5d006e-d463-4237-90f3-4cfeea901fc0" x="154.7957" y="58.3125"/>
  <use xlink:href="#5c5d006e-d463-4237-90f3-4cfeea901fc0" x="162.677429" y="58.3125"/>
  <use xlink:href="#5c5d006e-d463-4237-90f3-4cfeea901fc0" x="170.559158" y="58.3125"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#5c5d006e-d463-4237-90f3-4cfeea901fc0" x="249.179688" y="164.816406"/>
  <use xlink:href="#dcbc5f3d-49cd-4465-993f-f45a255e141e" x="257.061417" y="164.816406"/>
  <use xlink:href="#5c5d006e-d463-4237-90f3-4cfeea901fc0" x="260.998825" y="164.816406"/>
  <use xlink:href="#ee373b28-b66e-4cc7-ac39-3a87321aebf9" x="268.880554" y="164.816406"/>
  <use xlink:href="#d61ba169-9977-4c07-a00f-b652e2c3add3" x="276.762283" y="164.816406"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#5971a0bb-62a5-417f-8d1d-c81c33ae291d" x="27.800781" y="234.167969"/>
  <use xlink:href="#cf1266df-5613-4cfd-8e84-be19123280f6" x="33.661026" y="234.167969"/>
  <use xlink:href="#ab183365-5912-4dab-9540-4d3a4618f91d" x="38.996613" y="234.167969"/>
  <use xlink:href="#57b18eff-5b5d-4232-adf3-c7189f058183" x="44.332199" y="234.167969"/>
  <use xlink:href="#c21db32d-b24a-445b-a1fe-14a9cadd0e36" x="46.99765" y="234.167969"/>
  <use xlink:href="#c6d1eef7-4221-45eb-a0c4-949e09fb587f" x="53.396606" y="234.167969"/>
  <use xlink:href="#c6d1eef7-4221-45eb-a0c4-949e09fb587f" x="58.732193" y="234.167969"/>
  <use xlink:href="#3b53d5af-88b1-49de-898a-8ab1c5f91df8" x="64.06778" y="234.167969"/>
  <use xlink:href="#57b18eff-5b5d-4232-adf3-c7189f058183" x="68.864655" y="234.167969"/>
  <use xlink:href="#c6d1eef7-4221-45eb-a0c4-949e09fb587f" x="71.530106" y="234.167969"/>
  <use xlink:href="#1615171e-04fc-4eda-94b2-ba5c1c136930" x="76.865692" y="234.167969"/>
  <use xlink:href="#57b18eff-5b5d-4232-adf3-c7189f058183" x="79.531143" y="234.167969"/>
  <use xlink:href="#0691f01a-4f9b-4509-a0f7-1b3ca7e5be1f" x="82.196594" y="234.167969"/>
  <use xlink:href="#c6d1eef7-4221-45eb-a0c4-949e09fb587f" x="90.188263" y="234.167969"/>
  <use xlink:href="#22145fa8-a45c-442e-b087-71214dc5c528" x="95.523849" y="234.167969"/>
  <use xlink:href="#857ce6f1-780e-4daa-8abc-abd87153a886" x="98.718643" y="234.167969"/>
  <use xlink:href="#c6d1eef7-4221-45eb-a0c4-949e09fb587f" x="106.710312" y="234.167969"/>
  <use xlink:href="#df47b663-6ef6-46cd-a0e8-6990cadc8cf6" x="112.045898" y="234.167969"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#5971a0bb-62a5-417f-8d1d-c81c33ae291d" x="37.925781" y="198.667969"/>
  <use xlink:href="#cf1266df-5613-4cfd-8e84-be19123280f6" x="43.786026" y="198.667969"/>
  <use xlink:href="#ab183365-5912-4dab-9540-4d3a4618f91d" x="49.121613" y="198.667969"/>
  <use xlink:href="#57b18eff-5b5d-4232-adf3-c7189f058183" x="54.457199" y="198.667969"/>
  <use xlink:href="#e3ad6941-ec3e-46a4-abde-7e65550a378c" x="57.12265" y="198.667969"/>
  <use xlink:href="#cf1266df-5613-4cfd-8e84-be19123280f6" x="64.050949" y="198.667969"/>
  <use xlink:href="#07d61c43-94b5-4efe-941c-12e6dfafc433" x="69.386536" y="198.667969"/>
  <use xlink:href="#857ce6f1-780e-4daa-8abc-abd87153a886" x="74.722122" y="198.667969"/>
  <use xlink:href="#857ce6f1-780e-4daa-8abc-abd87153a886" x="82.713791" y="198.667969"/>
  <use xlink:href="#07d61c43-94b5-4efe-941c-12e6dfafc433" x="90.70546" y="198.667969"/>
  <use xlink:href="#b8d2e071-2217-4d8d-a77b-85b7b56affdc" x="96.041046" y="198.667969"/>
  <use xlink:href="#07d61c43-94b5-4efe-941c-12e6dfafc433" x="101.376633" y="198.667969"/>
  <use xlink:href="#ce83838c-a289-4795-9394-f874b0069e6c" x="106.712219" y="198.667969"/>
  <use xlink:href="#07d61c43-94b5-4efe-941c-12e6dfafc433" x="112.047806" y="198.667969"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#5971a0bb-62a5-417f-8d1d-c81c33ae291d" x="54.996094" y="163.167969"/>
  <use xlink:href="#cf1266df-5613-4cfd-8e84-be19123280f6" x="60.856339" y="163.167969"/>
  <use xlink:href="#ab183365-5912-4dab-9540-4d3a4618f91d" x="66.191925" y="163.167969"/>
  <use xlink:href="#57b18eff-5b5d-4232-adf3-c7189f058183" x="71.527512" y="163.167969"/>
  <use xlink:href="#e93771c4-04ea-4a52-adb6-a6d63904d72a" x="74.192963" y="163.167969"/>
  <use xlink:href="#c6d1eef7-4221-45eb-a0c4-949e09fb587f" x="81.121262" y="163.167969"/>
  <use xlink:href="#f40ca2db-a00f-472a-8894-e4ed273b2c73" x="86.456848" y="163.167969"/>
  <use xlink:href="#c2df39ed-2692-4a38-9f0c-d07ce50a552c" x="88.588272" y="163.167969"/>
  <use xlink:href="#57b18eff-5b5d-4232-adf3-c7189f058183" x="93.385147" y="163.167969"/>
  <use xlink:href="#c21db32d-b24a-445b-a1fe-14a9cadd0e36" x="96.050598" y="163.167969"/>
  <use xlink:href="#393595dc-7276-4c55-92ea-2876b048911a" x="102.449554" y="163.167969"/>
  <use xlink:href="#0b3efa9d-0428-40c6-b740-44e39cf37874" x="104.580978" y="163.167969"/>
  <use xlink:href="#f40ca2db-a00f-472a-8894-e4ed273b2c73" x="109.916565" y="163.167969"/>
  <use xlink:href="#ab183365-5912-4dab-9540-4d3a4618f91d" x="112.047989" y="163.167969"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#5971a0bb-62a5-417f-8d1d-c81c33ae291d" x="23.011719" y="127.664062"/>
  <use xlink:href="#cf1266df-5613-4cfd-8e84-be19123280f6" x="28.871964" y="127.664062"/>
  <use xlink:href="#ab183365-5912-4dab-9540-4d3a4618f91d" x="34.20755" y="127.664062"/>
  <use xlink:href="#57b18eff-5b5d-4232-adf3-c7189f058183" x="39.543137" y="127.664062"/>
  <use xlink:href="#cfb32a16-a1ce-41d8-a0f4-973421ca8435" x="42.208588" y="127.664062"/>
  <use xlink:href="#ab183365-5912-4dab-9540-4d3a4618f91d" x="47.005463" y="127.664062"/>
  <use xlink:href="#ede5b5f9-4726-405a-a246-476addbf1412" x="52.341049" y="127.664062"/>
  <use xlink:href="#393595dc-7276-4c55-92ea-2876b048911a" x="59.269348" y="127.664062"/>
  <use xlink:href="#6f7ae1f5-9c92-4ea2-b134-868cd7d53483" x="61.400772" y="127.664062"/>
  <use xlink:href="#cf1266df-5613-4cfd-8e84-be19123280f6" x="66.197647" y="127.664062"/>
  <use xlink:href="#57b18eff-5b5d-4232-adf3-c7189f058183" x="71.533234" y="127.664062"/>
  <use xlink:href="#c98524f6-9574-4eef-ae06-783b867aa52f" x="74.198685" y="127.664062"/>
  <use xlink:href="#a82b918f-4808-4be8-97d9-430f9c3fd8e4" x="80.597641" y="127.664062"/>
  <use xlink:href="#22145fa8-a45c-442e-b087-71214dc5c528" x="85.394516" y="127.664062"/>
  <use xlink:href="#393595dc-7276-4c55-92ea-2876b048911a" x="88.58931" y="127.664062"/>
  <use xlink:href="#b8d2e071-2217-4d8d-a77b-85b7b56affdc" x="90.720734" y="127.664062"/>
  <use xlink:href="#8fb04012-17dc-4b05-8af4-bbdcf10cf7f4" x="96.05632" y="127.664062"/>
  <use xlink:href="#026e711f-a1e9-4afa-9894-65f3796f7440" x="98.721771" y="127.664062"/>
  <use xlink:href="#22145fa8-a45c-442e-b087-71214dc5c528" x="104.057358" y="127.664062"/>
  <use xlink:href="#ab183365-5912-4dab-9540-4d3a4618f91d" x="107.252151" y="127.664062"/>
  <use xlink:href="#6f7ae1f5-9c92-4ea2-b134-868cd7d53483" x="112.587738" y="127.664062"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#5971a0bb-62a5-417f-8d1d-c81c33ae291d" x="72.585938" y="92.164062"/>
  <use xlink:href="#cf1266df-5613-4cfd-8e84-be19123280f6" x="78.446182" y="92.164062"/>
  <use xlink:href="#ab183365-5912-4dab-9540-4d3a4618f91d" x="83.781769" y="92.164062"/>
  <use xlink:href="#57b18eff-5b5d-4232-adf3-c7189f058183" x="89.117355" y="92.164062"/>
  <use xlink:href="#42eda7bd-9a65-44ce-872a-38f8dbeda5ea" x="91.782806" y="92.164062"/>
  <use xlink:href="#c6d1eef7-4221-45eb-a0c4-949e09fb587f" x="98.181763" y="92.164062"/>
  <use xlink:href="#22145fa8-a45c-442e-b087-71214dc5c528" x="103.517349" y="92.164062"/>
  <use xlink:href="#07d61c43-94b5-4efe-941c-12e6dfafc433" x="106.712143" y="92.164062"/>
  <use xlink:href="#df47b663-6ef6-46cd-a0e8-6990cadc8cf6" x="112.047729" y="92.164062"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#5971a0bb-62a5-417f-8d1d-c81c33ae291d" x="70.984375" y="56.664062"/>
  <use xlink:href="#cf1266df-5613-4cfd-8e84-be19123280f6" x="76.84462" y="56.664062"/>
  <use xlink:href="#ab183365-5912-4dab-9540-4d3a4618f91d" x="82.180206" y="56.664062"/>
  <use xlink:href="#57b18eff-5b5d-4232-adf3-c7189f058183" x="87.515793" y="56.664062"/>
  <use xlink:href="#1221b63e-5ebd-4ef4-8c9c-430a97c34395" x="90.181244" y="56.664062"/>
  <use xlink:href="#ab183365-5912-4dab-9540-4d3a4618f91d" x="96.5802" y="56.664062"/>
  <use xlink:href="#ce83838c-a289-4795-9394-f874b0069e6c" x="101.915787" y="56.664062"/>
  <use xlink:href="#07d61c43-94b5-4efe-941c-12e6dfafc433" x="107.251373" y="56.664062"/>
  <use xlink:href="#6f7ae1f5-9c92-4ea2-b134-868cd7d53483" x="112.58696" y="56.664062"/>
</g>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 120.21875 230.730469 L 124.46875 230.730469 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 120.21875 195.230469 L 124.46875 195.230469 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 120.21875 159.730469 L 124.46875 159.730469 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 120.21875 124.226562 L 124.46875 124.226562 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 120.21875 88.726562 L 124.46875 88.726562 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 120.21875 53.226562 L 124.46875 53.226562 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 256.28125 L 139.429688 252.027344 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 214.230469 256.28125 L 214.230469 252.027344 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 289.035156 256.28125 L 289.035156 252.027344 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 363.835938 256.28125 L 363.835938 252.027344 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(49.803922%,49.803922%,49.803922%);stroke-opacity:1;stroke-miterlimit:10;" d="M 438.640625 256.28125 L 438.640625 252.027344 "/>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#bd729ac8-8583-4cb8-ba89-700eabbd6b65" x="130.09375" y="265.992188"/>
  <use xlink:href="#0ea4294d-30a7-49b0-8b1b-62e9568bafa2" x="135.429337" y="265.992188"/>
  <use xlink:href="#bd729ac8-8583-4cb8-ba89-700eabbd6b65" x="138.094788" y="265.992188"/>
  <use xlink:href="#bd729ac8-8583-4cb8-ba89-700eabbd6b65" x="143.430374" y="265.992188"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#bd729ac8-8583-4cb8-ba89-700eabbd6b65" x="204.894531" y="265.992188"/>
  <use xlink:href="#0ea4294d-30a7-49b0-8b1b-62e9568bafa2" x="210.230118" y="265.992188"/>
  <use xlink:href="#bd729ac8-8583-4cb8-ba89-700eabbd6b65" x="212.895569" y="265.992188"/>
  <use xlink:href="#058b580f-461d-4127-af7d-0f7c6304ddd0" x="218.231155" y="265.992188"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#bd729ac8-8583-4cb8-ba89-700eabbd6b65" x="279.699219" y="265.992188"/>
  <use xlink:href="#0ea4294d-30a7-49b0-8b1b-62e9568bafa2" x="285.034805" y="265.992188"/>
  <use xlink:href="#cc3cf413-77e0-4d52-ba1f-b6e7a6b29b0c" x="287.700256" y="265.992188"/>
  <use xlink:href="#bd729ac8-8583-4cb8-ba89-700eabbd6b65" x="293.035843" y="265.992188"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#bd729ac8-8583-4cb8-ba89-700eabbd6b65" x="354.5" y="265.992188"/>
  <use xlink:href="#0ea4294d-30a7-49b0-8b1b-62e9568bafa2" x="359.835587" y="265.992188"/>
  <use xlink:href="#cc3cf413-77e0-4d52-ba1f-b6e7a6b29b0c" x="362.501038" y="265.992188"/>
  <use xlink:href="#058b580f-461d-4127-af7d-0f7c6304ddd0" x="367.836624" y="265.992188"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#bd729ac8-8583-4cb8-ba89-700eabbd6b65" x="429.304688" y="265.992188"/>
  <use xlink:href="#0ea4294d-30a7-49b0-8b1b-62e9568bafa2" x="434.640274" y="265.992188"/>
  <use xlink:href="#dbd8936a-1341-4072-b754-d0739b9eba04" x="437.305725" y="265.992188"/>
  <use xlink:href="#bd729ac8-8583-4cb8-ba89-700eabbd6b65" x="442.641312" y="265.992188"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#2591db38-be3f-428a-bbfd-adf67cc57832" x="150.121094" y="28.328125"/>
  <use xlink:href="#8b04a0c9-b7c5-4bc3-a5ab-4dadd8f1f196" x="159.72995" y="28.328125"/>
  <use xlink:href="#e408f01e-2dd0-4277-8a66-f34c44f3fc29" x="167.74202" y="28.328125"/>
  <use xlink:href="#a48c4574-ef1e-404d-9acf-53d9027dc574" x="175.754089" y="28.328125"/>
  <use xlink:href="#8b04a0c9-b7c5-4bc3-a5ab-4dadd8f1f196" x="179.756607" y="28.328125"/>
  <use xlink:href="#e408f01e-2dd0-4277-8a66-f34c44f3fc29" x="187.768677" y="28.328125"/>
  <use xlink:href="#cdf227e9-8d94-4779-b30b-a6118845c8c0" x="195.780746" y="28.328125"/>
  <use xlink:href="#8b04a0c9-b7c5-4bc3-a5ab-4dadd8f1f196" x="202.983871" y="28.328125"/>
  <use xlink:href="#4c059457-afe6-439a-9d19-a6e2a7f13973" x="210.995941" y="28.328125"/>
  <use xlink:href="#d57ec852-aedc-4979-a6f4-8d7b13ce9875" x="218.199066" y="28.328125"/>
  <use xlink:href="#11ebbd4d-41b6-452a-a3b9-cb9f78f58802" x="222.201584" y="28.328125"/>
  <use xlink:href="#353caa0d-36e5-43c7-82be-ad3ebdf3c679" x="232.605316" y="28.328125"/>
  <use xlink:href="#a48c4574-ef1e-404d-9acf-53d9027dc574" x="235.805923" y="28.328125"/>
  <use xlink:href="#fffbcce7-5e4c-4ffb-9d3c-94ac1d37225c" x="239.808441" y="28.328125"/>
  <use xlink:href="#d57ec852-aedc-4979-a6f4-8d7b13ce9875" x="247.820511" y="28.328125"/>
  <use xlink:href="#5af4611e-989f-4983-b4e6-dca4e48cf9e9" x="251.823029" y="28.328125"/>
  <use xlink:href="#353caa0d-36e5-43c7-82be-ad3ebdf3c679" x="261.431885" y="28.328125"/>
  <use xlink:href="#b1d887fd-26a6-49b7-8426-2647855b1af5" x="264.632492" y="28.328125"/>
  <use xlink:href="#8ad7a25b-2e63-49e8-ab98-f681bf6aa87c" x="272.644562" y="28.328125"/>
  <use xlink:href="#8b04a0c9-b7c5-4bc3-a5ab-4dadd8f1f196" x="275.845169" y="28.328125"/>
  <use xlink:href="#e408f01e-2dd0-4277-8a66-f34c44f3fc29" x="283.857239" y="28.328125"/>
  <use xlink:href="#a48c4574-ef1e-404d-9acf-53d9027dc574" x="291.869308" y="28.328125"/>
  <use xlink:href="#d57ec852-aedc-4979-a6f4-8d7b13ce9875" x="295.871826" y="28.328125"/>
  <use xlink:href="#810a2665-bbb6-4d66-bcdc-0dd9568e8203" x="299.874344" y="28.328125"/>
  <use xlink:href="#b1d887fd-26a6-49b7-8426-2647855b1af5" x="313.471649" y="28.328125"/>
  <use xlink:href="#95e5869a-4b2e-4e0c-9368-6ca39692abb9" x="321.483719" y="28.328125"/>
  <use xlink:href="#ff196a4b-4737-45af-a6f3-cd8c5c90c25f" x="326.281113" y="28.328125"/>
  <use xlink:href="#4c059457-afe6-439a-9d19-a6e2a7f13973" x="334.293182" y="28.328125"/>
  <use xlink:href="#d57ec852-aedc-4979-a6f4-8d7b13ce9875" x="341.496307" y="28.328125"/>
  <use xlink:href="#2c72bf9c-7582-4e2c-997f-8cd506f26c1a" x="345.498825" y="28.328125"/>
  <use xlink:href="#fbf0f2b4-68c4-4e6e-af95-49421d72c4da" x="350.296219" y="28.328125"/>
  <use xlink:href="#b1d887fd-26a6-49b7-8426-2647855b1af5" x="360.699951" y="28.328125"/>
  <use xlink:href="#95e5869a-4b2e-4e0c-9368-6ca39692abb9" x="368.712021" y="28.328125"/>
  <use xlink:href="#ed519697-0544-4696-b68f-4c50db167766" x="373.509415" y="28.328125"/>
  <use xlink:href="#becc9896-188c-49f1-8006-34cca4e06782" x="385.509933" y="28.328125"/>
  <use xlink:href="#8ad7a25b-2e63-49e8-ab98-f681bf6aa87c" x="393.522003" y="28.328125"/>
  <use xlink:href="#353caa0d-36e5-43c7-82be-ad3ebdf3c679" x="396.72261" y="28.328125"/>
  <use xlink:href="#e2d7f321-37d9-4771-8391-076ba067850b" x="399.923218" y="28.328125"/>
  <use xlink:href="#8b04a0c9-b7c5-4bc3-a5ab-4dadd8f1f196" x="407.126343" y="28.328125"/>
  <use xlink:href="#ff196a4b-4737-45af-a6f3-cd8c5c90c25f" x="415.138412" y="28.328125"/>
  <use xlink:href="#194b2940-296a-4a77-8b7a-40287a322f43" x="423.150482" y="28.328125"/>
</g>
</g>
</svg>


Now things are more interesting.
The Vedas has no violent sentences at all.
Far ahead of the others we have Dhammapada and the Book of Mormon.

The next logical question is, "which words triggered the most sentences?"
We could visualize this with just a series of barcharts, but since we're specifically producing counts of words I think word clouds would be a lot more fun.

```clojure
(def violent-word-counts
  (->> (map #(select-keys % [:book :word]) violent-sentences)
       frequencies
       (map (fn [[bw c]] (into bw {:count c})))))

(defn word-cloud [book]
  (let [fname (str "resources/" 
                   (->> book
                        ;; Lower case the title.
                        str/lower-case 
                        ;; Replace spaces with dashes.
                        ((fn [s] (str/replace s #"\s" "-"))))
                   ".png")]
    ;; This builds the word cloud and drops it into a file.
  	(doto (WordCloud. 450 225 CollisionMode/PIXEL_PERFECT)
    	(.build
      	(java.util.ArrayList.
          	;; Select the relevant book
        	(->> (filter #(= (:book %) book) violent-word-counts)
                 ;; Get only the words and counts.
            	 (map #(select-keys % [:word :count]))
                 ;; Place each in a WordFrequency object.
             	(map #(WordFrequency. (:word %) (:count %))))))
        ;; Save to a file.
    	(.writeToFile fname))
    
    ;; Read the file and render.
  	(img/image-view (ImageIO/read (io/file fname)))))
```

<span class='clj-var'>#&#x27;violence-in-religious-text/word-cloud</span>

### The Holy Bible

```clojure
(word-cloud "The Holy Bible")
```

<img src="data:image/png;base64,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" width="450" height="225" alt="" />

### The Jewish Scriptures

```clojure
(word-cloud "The Jewish Scriptures")
```

<img src="data:image/png;base64,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" width="450" height="225" alt="" />

### The Koran

```clojure
(word-cloud "The Koran")
```

<img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAcIAAADhCAYAAABbV7VpAAAzY0lEQVR42u2dCbxM5f/Hn+xE9l2W7Mm+ZCciW3ayRMgaIkSbFkVKpSQqkS2RlCQp2StZIlL+UiGRrV/ImuX7fz7Pc869c2fmLnPdbWY+c1/v152zzNy5zzlzPuf7fb6LUkoJIYQQEsZwEAghhFAICSGEEAohIYQQQiEkhBBCKISEEEIIhZAQQgihEBJyvdSvr+TqVSVly/pu69xZSY0aSsqUUXLtmpJGjaI+5/gRQiiEJOhp2VKJiJK77vLd9scfSubNU1K3rt2nU6eozzl+hBAKIQl6WrSIKoRFilgrEc8zZ1aSOjWFkBBCISQhTOvWVtjg6qxUScmJE0r27FGSIYP93aYNhZAQQiEkIUy7dlbYnnxSyT//KDl+XEn58kqKFrXrH3qIQkgIoRCSEAYBMRA2lx497Ppy5exy//4UQkIIhZCEML17W2FbsULJkSNKDh1Skju3kqpV7fqePSmEhBAKIQlhhg61woa0iDp1lFy5omTZMiUVK9r1ffpQCAkhFEIS4hYhxC9nTrv89NNW6CCEyC/s2FFJhQp2XYMGUZ9z/AghFEIS9Nxwg5J8+aKuK1TI/nbFERQv7v85IYRQCAkhhBAKISGEEEIhJIQQQiiEhBBCCIWQEEIIoRASQgghFEJCCCGEQkgIIYRQCAkhhBAKISGEEEIhJIQQQiiEhBBCCIWQEEIIoRASQogXRTVDOA6EQkgICTVu0rytyR3LfiM1hzlehEJICAk1qmrw0yGW/R7RHOJ4kQCEMG8OJf3bKpn3tJLRPZRkSMfBI4QkE+k14zTrNL9o5jvrZ2uOOUJ4RrNXo69dKqtmvGaF5mXNzY4QntZ85QjiIk1eji2FMAa+n6Pk4kYl+z9WIt8p2TJLSdo0HEBCSDLQzRG76ZqnNKud9TU1c5xtqzRPOi7SXzWXNUs0VzRvOUKIn82aqZpTmg84thTCaChdxIpfiUJ2uUVtu3xfSw4gISQZgEV3TvO1po7XtnyOwA11lp9wlpto0jrPH3OE8IjmBme/gZrzmiwcXwqhH0oVtsJXvrh1kRYvqGTHXCUTB3MACSHJRANHCPHzmlewDH7GOMvvak54bD/vWIrec4RdnNeV49hSCP2QKYOSf1ZZMfSkU2MOICEkGYDVV9dxe8Itek2TwdmWyRG0sc5yD2cZbtMyjgt0tscc4b1OYM3Pmt81nPKhEPqjYkklG99S8lBXJe0aKmmr78QGtVeSKhUHkBCSDDzriJv7M89jG4TwqmaQs5xds8lj3/85AviQI6Duz34n4pTjSyH0R+aMSiYNVTLrCSWvj1QyfqCSp/oqubsuB5AQkkzcqrlLk9/PtloeFqJLcU0lTWpNbU0qJ5q0gPNetAQphDFRTJ8o+z5U8usSJRc2RLpG33uGA0gIISTMEupzZbNWIYSwSmkOICGEkDARwjSplRTJZ+cLby2m5NAyJe+O5QASQggJAyEsW1TJmTW+UaP92nAACSGEhIEQ5sxqy6r1bmWT6etUUJI7GwePEEJIiAsh5gJbOZGh1coqmfOkkt0LlMx8XEn+XBw8QgghIS6Ef39py6jdcIOS35bYeqOLJyg5vVrJ/y1Skj4tB5AQQkgIC+HZtXYeENGhmBMslMeuR4m1a5tscj0HkBBCSMgK4YY3lez9QMmAdkr+1aL44fM2pxDbUGsUlWY4gIQQQkJWCBEtCiH0jhZFBOmVb5XUrcgBJIQQEgZRo4XzWdHr3FjJ8C5KXhxin3PwCCGEhE1CPdIlpo5SsvA5JT2aK8nOnl2EEELCSQi/nKLk0kbrGv3fKiWHlyspwBQKQggh4SCEWTIp+e9rGyUKMby/tZJjn9skew4gIYSQkBfCCiWsJVg0v5LZY5Vsn6tk22wlzw7gABJCCAkDIUybRsn59Up6tVRS8mb7HMLYrCYHkBBCSJjMEaLCTPNa9vktBZW0qc/BI4QQEiZCmCe7komDlbwyLJIX9HK5WziAhBBCwkAI0YNwzyJbbxRc/kbJ1W+VNKzCASSEEBImrlFPmt5u5whLFeYAEkIICUMhRBTpX58peaI3B5AQQkgYCuGmd2wR7nubcQAJIYSEoRASQgghFEJCCCGEQkgIIYRQCAkhhBAKISGEEEIhJIQQQiiEhBBCCIWQEEIIoRASQgghFEJCCCGEQkgIIYRQCAkhhBAKISGEEEIhJIQQQiiEhBBCCIWQEEIIoRASQgghFEJCCCGEQkgIIYRQCAkhhBAKISGEEEIhJIQQQiiEhBBCCIWQEEIIoRASQgghFEJCQpQbbuAYEEIhJCHNoPZK5DslZYuG5//ft6+SPHn8b6tXT8nJk0rSpeN5QgiFkIQcWTIpmThYyZZZVgjXT1ey6nUlX79t1y17SUmhPKE9Bpn0GPz6q5ImTfxv79hRj43oscoS8/vg9T/8wHOKEAohCSrqVFBy5VsrguDCBm39fKHk0DIl+z9W8tsSJZVLhc7/W6JEVMuueHH7u3TpqPsVKKAkZ87ohTBXLm09l1WSKpVdvv12JVOn2v1gXeLvuC7VkiWVFC7Mc42QsBbCm25S0r+/ktdeU/LEE0patODBTVEWUQYlgztaIQwl0fPHtm1K3nrLcQcPUvLvv0oqVLACdsstSjJnVrJypV2+elVJ9+5RhRDC9rq2mK9ds+D9YFG+/bbdH/tduKDklVfs+jVr7Dowfz7PNULCUggrVlRy8GDkxcBl40Yl+fLxIKcUqt+qZPZYbS2lDe3/85FHlJw9a0Vt9279P89W0rChPSeLFVPSp4/d3q2bknnzrNB5CmG/fvb5/ffb51euKEmTxr43bvCwzT2vO3VScuKEff232urevz9yX0JImAghvvQ7d9qLw+HDSp591l54jh616774gtF4KSkqcngXJTMeU1K1TOj+n7lzK7l4Ucl779lzsFKlSAHLm1fJkiVKli2LdIli386dI4Vw4UIlmzfb7bAWsd197wYNIi1LLMMCPHfOWoqLF/u6XwkhYSCEuMjgwnDpkr0Auevz57d30thWqhQPdErg9nLWNXpunZJmNUP7f3VFEDdiWG7ePNKSe/JJGzhTpIiS99+3blJPi3DMGCW//WZdqD162PXuXGLNmnYZc4dYnjtXyalTSmrXtud8z55KsmXjuUZIWAlh+/b2woCLife2L7+027APD3TKmCeECA4Kg+OBwBbcnDVqZJdr1LA3ZtmzW8GCEOLcPH9eSdOmVigx75c2rbX29u61r//vP7sP3Knu++J1CLTBMvbdtCly7hBTBG5wDiEkTISwWjV7AThwQEmGDFFdpj/9ZLe5FyOS/CyeoOTIchtJmie7JVuW0PxfEfXpuZw1a1Q3cfXqkXmFWHbFzQUBNtiOeUUIKNYhghTnvD93rOsuJYSEYbDMvn2RwTFwFc2ZYwMHsO7yZSWjRtnAA1iGVarwoCcX2bXgHV0RmUbhck1bNHdW5/gQQiiE8aJgQSt23hGjMXHvvTzwyQGsGaRQPNVXydMePHqfkpxZOT6EEAphvC+uL7ygZPp0GzGKHEKEr48dq2TiRJvPhWi6r76ykXjr10cGGpAkDmwqpeTvL5WcWKnk949sIj1+H1yqpEkNjg8hhEJ4XaCyxujRSqZNs78RTcqDm7JIn1bJzMeVrJ6q5LuZlgNLrXu0HOe3CCEUwvjTuLGSM2eiuj9RkQPzhalT8yCnZHo0t0JYoQTHghBCIYwXEDo3WOb4cfsb1TwQeo7nkyfzIKck8uZQkiFdZDqFW3YNUaQcH0IIhTAeIAoUgrdjh02TwPOtW23oOXKrYCm6RYvDGSRyDxxoK5E8/LCS9OmT/jM0rBIZKepZhPvwcus25ZeREEIhjAft2lnxe/NNuwxBxPIddyjZsiWyvmO4H+jt220pLjeR++uvk95tjBuSx3rZeqPzn1HyyjAlI7uFbh4hIYRCmCQgKdmzlBVSI7CMPEIUI4aLNG2YWxtlytgxKVrULrdqZZdR3zKpP0saLb51Kypp31BJ39ZKntNW6tynQr8fISGEQph4kYjpbRkqtLrBc1SU+eWXyKAZt3hxOIO+dRiLqlWtGJYrp2TPHptikqTzuals/0HvhPpTX+nPWIjHiRBCIYw3M2bYC/2dd9plzA/iQr9rFyvJABRyRmFm78ICd92VtJ/jjqpW+F4boaRFbZs7iLSJNIzsJYRQCK8PdAPHRT1jRh5Qf9x2m5LPP7e97VDgGSRHdR1YfSintup1BjARQiiECQpEsFevqOtQgNhtXUN8wXih8EBi/52M6bUAt7FBMUM6Kdk531qFm96xy5gnrMZKP4QQCuH1pQWgTQ2iId11KKsG1x/a3gwezIMMCmlr7Oab7Q1C+fK2K/q6dbYYQWKmUozp4Tsn6M3FjTankMeJEEIhjAd3321F78MP7XKHDnYZATT4ffasnSML54Ncq1bMRchbtky8v505o5L6lZVULGkb8yJxvl6lSNB1AnOH/CISQiiE8QQWHy7mKLiN5e+/t8vo1L1ihX1et254H2TkCzZrpqRbN9sJvUULaxFu2GBTK9ALj18EQgiFMEipV8+K3dKlSrp3t89//tkGY6AjBZbbtuWB9gZjM348x4EQQoJeCBEp6rpBXWD1YNuaNXYZ1Wd4oJMnWIYQQiiEScCQITYwBqL38st2HRr2nj6t5LffbJAIg2WSJ1iGEEIohElEtmxW/DzXYe6LbZiSN1iGEEIohMmcX/jpp74CyWAZBssQQkhYCOGSJdbi8U62JwyWIYSQkBJCFJOuU0dJzZpK6te31k/79rbeKIRw1izbg69qGOerpYR+hIQQQiFMBHr0iHn+y5MffwzfA50S+hESQgiFMBHAHBc60qMNEzosHD+u5MABm0uI7vS46O/dayvPtG4dngc5JfUjJIQQCmESgiR7XPD79g3vg5xS+hESQgiFMIlx+xQiSjKcD3JK6UdICCEUwiQGbZhQgi3cD3JK6UdICCEUwkQA0Y+Y++va1RfkzN16Kw+yO0/4wQfWXYx8whtv5JgQQkhICOGgQTFHil64oCQDe93J7t1Kfv9dySef2DHBHGH+/BwXQggJeiFEIAgS57/80vLFF/ai7wrh3Lk8yAiOwVhgrLBcoICNrH3uOY4NISSFk0qjDR61UPNoHNZzjtCSNq2S2bPtxX/OHJ5IlSrZsYB71F33009Kxo3j2BBCUjgjNfi5qJkTh/XJJYT58uWTG2+8UfLnzy8PPPCAZMqUKdkHL3t26wK8elVJ5szhfSKhliiKCQwbZpdz5FBy/rztQsEvGiEkRbNLs09TII7rE1oIM2TIIJ06dZKbbrophovsDTJmzBi5++67pWzZsvL0009LoUKFknXgKldW8vHHkQn1WA73kylrViXp0kUuez4nhJAkQd+Eq7sdl+YQzU0aXIu6eOyTUdNck1Zzu2P1faJBGlz+GNa7ry+qaaUp67hPsS6Dsx4NBupq0LM2UxyFEJYehK1ChQox/nO5c+eWjBkzSunSpc3+hQsXNuthGWbJkiXJB3v48Mg5wmPHWFczlT4Zhg61jXgRXIQi5EifQGQtqst0784vKCEkkSmsOe4ImPszUPO087yYs980zVXNW1774udjZ7u/9XjtM17rN2tQSlJf/9S/mh88tr3o8dnSpk0rTZs2lf79+8vIkSP1BfJes6Fjx47y2GOPGWEbO3asjBo1yliH9evXN5Yftnfp0sUIIF5TsGDBCIsQzyGOnu8Htyle36tXL2nWrFmiuk9h7SCJHgnjSCYP9xMwVy4lf/8dc3Qtv6iEkETlHkeAJmpgnDTVFNfMdta71+qPNNc0VTTjnW0Qv5rOPpWjWd/QWbdI00az35k/zK0Z62z7U9PT+f2xx2crX768Ea/mzZtLnTp1pGfPnhHC1apVK7OtR48eUrduXfMcPPHEE3L//feb53CJ4nfVqlWlXLly5nn16tW19THaiGeePHnM/OHjjz9u5g/btm0rTz75pPl7CTnIpUsrefRRJe++q+T555X+vDzx/AURucW3Gza0HeuLFbOdKTg+hJBEJbtmtyNIqzXVnfVznXVpnOUvHOvNCJSz7UGv9/K3/g1HQJc6AnjOsTix7Ulnf7fASg2Pvw/guoTlB4uwePHiUf4YrDYIW+3atc1ykyZNjIgVKVJE0qdPL507dzYWIvaBoLqi+tRTT5nfJUqUcObsKhshrFevngwbNsxYmDfffHOCDTDcfAiM8bZyFiywbkGehNZKfvllOy7//afk4EGljyPHhRCShMASHKw5pLmsaaR5yRGpHM4+K/0I4bBohNBz/QfOupOOxZjXY5srhDfHECwDUbrvvvuMeLVp08bDgkhr1jVo0CBCCB955JEob4DAGOwDt2iVKlXMc1iVjz76qPTr10/SpEljXKJYDzGElZktW7YEG9hChayF43aZeEPfFWzZYqNFsY65ckoyZlTy/fdKrl2zKRNIozhyRMnOnRwbQkgSgWCVvo7L8wFHmJ7XDHeeP6zprdHXcXXWeU1lZ9tDXu/lb/3DzrrXNA00/Rw3ay0PISwczWeDRVi0aFHjvsR8HgQL0aLYmDp1arPcqFGjaIWwQIECZp9bb71VatasaZ4jyhTLeI75RwArEZZhrly5pFKlShFW5vXi9iPcsSPqegSCYP2hQzwBs2Wz5dXgDnXXlSyppE8fjg0hJIkY7RXI8pfmVmd+7w+vbRec1zR1lnt5vZe/9WmdAJuTHu/ziwb502Oc5bzRfDaInDv3B+DudDfCmoMrFAKHZez78MMPe11ksxmRgxsU84SYP0yXLp3Z1rp1ayOc2Kdr167GIsTfwHtCHBNicBHxCMH77jvfiz8sIGzDc56IhBCSzJTStNVUdKI53fVIoajjWHqlNW7v2AoaNFQv6fU+0a13K85U9XKDwu16Vyx5hIjwhJD5S3WA2xQuUjyH1YiIUF/XW8aInMLMmTNHyTH0fE+8D4Jw3PdLCDDPdfmyFbx33lFyxx1KOnSwjXixDo16eQIS4h04ldZMZyBorU+fPvp7cwfHhQQ1NWrUkLvuuksaNmxoPI4wzOCFrFatmjHmoEeJWlkmPsBSHDduXIK814AB0acF9OrFE4SEL/jylyxZ0szTP/PMM7JkyRLZs2ePvnm8LN6P119/nWNGghZkPSCbYfDgwRGeR3gh8Rt6E4sBljwfes2aNfLWW28l2PvdfruSRYtsDc19+2yXhcaNeXKQ8ANTGu3atZNPP/1UTp8+LXF9YF/EBXAMSTADLyam63LkyOF4DYsYYcyaNWvchRDBLKlSpYpIn3DXIx/QM0AmZ86cEfuUKVMmYhuCZOBmxW8E3bjziwDLjRs3NvODO3bskEmTJiXaYFSpomTtWtuQFrU2eYKQlE6Om5RULRP/8xXTEuPHj5e//vpL4vPYvXs3jwMJCSGE8LlCiOk4CGMsVc6irti4caNMmDDBlFU7efKk8a8+9NBD8uuvv5ov2sqVK82X5urVq9K9e3d54403tOCsNa/t1q2bnDt3TiZOnGi+jO4XcurUqWZ+8eeffzavu3LlilkPkzVwV4+tHRpdj0GkU6DjhBsoc+IE+xGSlM+EQUrOr9fn7HdKds5Xkj5tYK+HJffDDz/ESwD//vtvWbZsmdx22208FiTowXdh0KBBMnz4cBOwCf2CVsXyuqgrli5dqq2oz2Xy5MnmS4Io0FWrVsmCBQvMpPrZs2eN4M2bN0+2bdsmy5cvN/MOeC1Kqv3zzz/y4osvmte+/fbbpsLM77//bl6zf/9+Y6bCojx//rz5gIH+k337WoFD7iCqyKDFkBsliooy6Krgzg9u3GgrzvDkICmZXPrcvfyNFUGXUQHWf4Un59q1azGK3TfffCMzZ840kd+o8ISbXE9PDyGhFAyGGzuUAkUQDQrABCSEsAYhXH/++accO3bM3ClCtCBkEDwsYz/8gYsXL8rixYsjhBAVamAFvvnmm/K///3P5BMOHTpUzpw5I++//77MmjUr4u/A2sS2wM1emwzuGRDz7bdK/73IZVRNQZAMXaIkGLjnzqgiCJa9FPj7zJ07N1ohxHcZwTCIposleo6QkAkUw2+k88UyP+grhIguwwPiBdMS0WVwZcLfClcmXKSw6iBscJPiy7V9+3bjh33ttdfk6NGjMn/+/Ah3ae/evc37DRkyRA4fPmzUGXVLL1y4YGqRxk/tkaOoZPVq3yjRdeuQ5sGTgAQPLev4CuHWd+PnEsLNKL67MT3gmcH8fBxCygkJSpAWBEMLnhJM86G0Z0BCiAnFf//9V6ZNm2YqzkAEXYsPYgchxANWIoJekKvhGYp9/PhxI46rV6+OCGnFAwE1cKO684NwoSK09Xr/YbhG582LzCUEBw4oefBBtl8iSRChmVqfa52VzHpCycvDlIzuoeS+lkqa1VRSuZSSEoWUFC8YM3fX9RXCXxbH/zNhPh43oHCFxvY4dOiQuYG95ZZbeDxJSIC8dgTLoH72PffcYzQNy9mzZw8sfQLFs90ya7DePN8Ad5DoLuE5t4DKMZhvwNwfinDDJQrRdLdjm/sc62FR4v0TMlS7aFEl06cruXgxqou0XTueGCTxmDjYV8QSihMrlXw/R8ncp5QMbK+kQonAi8gjaf7VV181UxUxPeChQZcZHlMS7EB/IHzohgQvJuphI2oUN4gpLo8wsShQQMnkyZGFuBE8Q1cpSZToNC1KR5YnnhD64/RqG2GaJVPcPyduPtEuDSlLMT2QaM/jSkIB1M1GoCamCpBMjwYQAblGQ4XcuW2nhaef5klBEocM6fQN17qkFUKXY58r6drU/+eCxwVf/hUrVpho0bg+tm7dyuNKQiZqFBZh+/btDW5OYdgJISFJwYJxySOE4L+vlVTxSA/CdAWC2GJKo4jusXfvXlOjkceUhAIoCoOpOsx9V6xY0bQSdGtiUwgJSfAvnJI+dyv5/FUb4HJpY9KK4c8LrWWKsmq7du2Ks/AhSAbBa26rNJZWI6EkgshRdzsqYX4QWQsBVZZJsUnHuXKZgqqejYMJiTMIMpmiqRTDPk001zQ5r+PvZLeRpJjDy53Nkj2LXc6YXknaNHZuEQKKPFcsZ8qgZNd7viIHgX28l5JF4+3cYHRi+FRfZe56o3sgbQl1fXExwJ1xHNxEhAQt+fLlMwJYqlQpUw0NN4lxeF1w/HPw944dO9bUMOXBJgGDkmXLNA1j2KeNss07C8bzbzRXtqFoPEr6rXnDV+BKF4ncnjmjknH9/Qvhl1NspByqPkX3OHDggKkqg1JTcBnxnCAhGcCWOrWZH3zwwQdNJbQ4JNIHlxCiOgDyDimEJFbqaXJ7WIJNnN8tVGQzUAhjTYfohBCCdqdDVmcdGn72c6zGBzQlnPVI05nvvP41ZTtoB/CZl77oK3C1y/vu9+pDvvv9+andhl5sR44ciZNrFBGiyBVGhSh4W3jekGAH6T+e7lDP5w888EBslmFw/JNQdkTCIS9kwIABhr59+5qmwjwJSBQ2O9Yfnvdy3J31HZGqZt2XaquzjJ+X/AhhFs0OzSXHytvliOcEzUXNWWfbLue14zTHndf/oXkzsM+8eIKvwDWp4bsfEvD9WYU3OSlC6AYzZsyYWPMGPR8IrkHd4Hr16vHcIUFL8eLFjecQNUYxVVCpUiXzG815q1SpEhoWISY6mzRpYkrnuGCSHz5gngQkCoM1VxxB26L5TFPXEakKjkV3znFlIr3mf5o0XkLYRXNC01mzVnNMk00z0dlnmuYezWVHIF1L1P0bAX7md8f6ilv9yr77YV7xn1W++6KKjed++F6gXOJ7770np06dipMgomoUzx8SCqkTKOLizg+iAAymDkImfQL/oNsjEZYgSsDxwBMfYM39q/nIEabGjqtSHFcm1q9y9q3rIX6eQrjAETkI6vuaMs7+EMDTjih2dvbP6eE2FS93axx5bqCvuBXN739f1CH13rd4wZi/N7iJRMs0zBXGFFTD84cEO8WKFTNdkyCEmBPHlNqjjz4aGq5R/FMonIpqAZjTgLkLFxCLBhO/vOmI0vfO8p3OMiynMZqjjrjN0Oz24xqd6bg/GznLvTW3aOY6Vib2b+bs74pkeWe5fuCf97biNi/QFbZ106Pf961HooogqtukSR33v4W6vyioj6L5KK3mukdxseC5Q4IdBINB/NAgHgGWyK/FbzTsDXohRM1EzBGimHCHDh0i6slxjpD4pbzj/uzsLFd25goLOYE0/+eIFqy+bh5iCQsws+ZmzQZnWRw3KSy9VzXfOfvDurzquESxXC7+rlHQsIoVufEDbcpFdPsVyafk/xZZEURlm3YN4z9OSDLGFAOb8pKQqfiUIYMprQarsGXLlqbGKLQib968wS+EpUuXNv8YvrAImMHEKH7HUi2AhDO5NJ49KbN6ba/mlSpxgyOUnvvk0JR2ok5dt6vnPlU85gjB7V5/MxEpmNvmIcZlX9w4BhBKTkhQA+sPzSPgMYQwogtSyKRPIAQWrlEIIExfWIc86CQYgOsyU4ak+3u4+0V7pc2bN5sm2K77c9++fbJw4UIZOHAgq8mQkAK9bpFChFKBaECNFoGYQkPgTBx6bwbPP5o+fXqj8nCNtm7dOi6RQIQkC4juhMsSVWGOrlBy9VvryvzrMyUb37JRoo/01HeueRP+b2PuPLbmvHhs3LiRAWckZIDVh+pjgwcPNq5Q12DCb0yrIWgs6IUQifRoJcMDTlJ81FoBG+wSl1qhFzcqmTJSSb6cCfO3ESUXyOP06dPmppLHjYSSWxRJ9G4pQXRjgTDGMjUQHJYgaoyibE7BggXNXSzmCBEdxKhRkpJAIMuhZYEXzz6/XsmzAwJvvOsJoqlPnDgRcOcJ5Bnie8XjR0JFCCF8rhCiHyeEMaiLbkPFPcvleIPuwzz4JKW4Q7fPvb5uEvOfCSwVwhO4hDwfly5dkhkzZpjIOVTYQOUYpCD99NNPPmKIThQ8hiQUwNz3oEGDTIoQvB3oRAFPSVAHy8DiQz5I586dZcSIEUbtUV0caROskUhSEm3qJ0xrpUlD4/f3p0yZEkXcunXrFq2HZdKkST5iiEo0PI4kVKrLIMMAtXQRRINzPiSiRhEkw1qIJCXz/RxfUTu4NDJQxgU9C8f0ULJnkX8hvKL3r35r4H//iy++iOLujG3aYMGCBVGE8J133uFxJCFTXaZXr16mSMQ999wTl6Cw4PL91qpVy4THwuTt2bMne6uRFMGd1X0FDQJYp4L/zhJDO1lX6qjuSi5/47v9g/GBf4atW7cGVC4NUdfnzp2LeA1ez2NJgh005kXvTQggpgTgTUSCvVueM6iFEJP5mBNEGCzCw4cOHWrMXuQX8uCT5Obhe33FbPJwu61BZd9t6GZ/g5N437Wp/+CZGzMG9hnefffdKBZeXG4SkULhPlBujbmFJNhBAj20At0n3DgTLMcSEBYc/xwqiCMfJJbIH0KShemjfcWsbsWY3aYtakduR3Nd7+21ygf2Gbp27RpFCHEXHNtr5syZE+U12bJl4/EkQU+XLl3M+Y/fsA4RJBbUCfWY5ISiIwQW/xj8vgiUgZnL1AmSUlj5qq+Q5bgpcvu9zfx3lne3j+7hu73DHYF9BnhHjh8/HiFq6Fg/YcIEmT17tnz11Veye/duOXjwoEmx2L9/v6xduzZK30I857EkoQC+C6gqA/foHXfcEfxtmFBfNLrUiZEjR8bWWoOQJGHvB1FFDNVkokSxpVFyeLmv2FUrG717tM/dgX+OmTNnSnwfCJ7hsSQMlkmBoONwqVKlTGUZt/swfleoUMHAg05SAj+9H1XETqz03eexXr5ih0AabHuws++21vUC+wwNGjQwuYPxfaBOI48lYbBMCid79uxmAhRVZdBjip0nSErhq9d9hSxDuqj7wFV6dq3/ucR3HvNdf3u5AK3SvXvjLYJMqCcMlgkCIHqjRo3ycY9C6aH4+Od5EpDkYu5TcQt2eWWY73475io59VXUddc2BV5/dMuWLVHE7ZdffpFXXnnFuIhQp7dRo0ZSv359Y/nhO4OCxGvWrDEuUZQr5HEkDJZJ4dxyyy3G3wtBLFy4sPTo0cP8o5gIhUC2bduWJwBJNvq29hW4mY/77lcoj02oj626zOZZgX8GVN/ftGmTCYxBni1cRDw2hMEyIRAs44JIUc/aoug6jBqkiCZFxRnkFfIESFgwxu3atTO/OR4xkzeHbwUZdI/Plc1335eHxS6EiCLluBIS2NwgSqv5Iw43hcHxT8KshYsHyfS4ON93332mmCrWI8cQ5i9PhviD5Gv40DEPi7sngMAk3HzgzopjFDvr/bReem6g737Zsij5/aPoRfCHeYEn0xMS7sAYii7DANMAQd19wnuesEWLFqZTPQoEo+Qa1qMOKeY/eDLEH1Rqj+4kAuhsAMubAUrR06SGr6htm+1/X7Rr2v+x7/5IuyicL/7TByimvXLlyjjx2WefydKlS+WDDz4wN5joas/jSIIVeAuRUeCPkiVLhsYcIf4JWC2ZM2c2F2OEwtJll7AnEcLvmzVrZkBABQIsIIK48YD1jefwuXO8ouftR6MK24fPxzDmeZSM62/FcsVkJcPuib8Igs8//1yu57Fw4UIeQxIyblKIHwwkdCuKw2uC4x+D1efPUsE/ywOfeBY4xhgnE04sNEZGgQNW9Ike9BJ8pKeSnfOVfDJJSdUySfe3jx07dl1C+Oeff/IYkqAH9XL79+9vgin79u1rSnO2atUqdOYIkT/oJtPDesFFGhYiD37iAWsQxQzwvG7dujJ69GgKYQrlueeeuy4hfOGFFziOJOhBFRkEUubMmTOiKAu0IhYPYnD+swjqYIf6pL8ZYb5myvec9OnTR3r37m1AgBlAcBnAHfIff/zhI4KYI+T4kVAA6XUQQjdlws04iKUbS3D8c6VLl5aBAwea/EF8oRHcMXbsWF6YExCEGcPCRiUGRFih4Dlz0eIPehEO76Jkykgln71iG/Hu+1DJhjftXOLddX2rzyQFmDNBvqHnY+fOnTzWJKipWbOmdOvWzQAhhDcLHVngJsVyLC37guOfxIUZScL4Jzt06GCCOVBmjSdAwoDgI/jUvedg4V+vWrWquXgOGDCAkYVxoGxRJctfjj1XEPz1mU3GT5UqaT8jbnK2b98eRQy7d+/O40eCliJFikjjxo39gmbusdzoBc8/mitXLgNMXFQXRyoF+6clXJQVokXbt28vbdq0MbmaiBDFxRE+d4DKPjjZOF7RM7aP/47zsbF6qs0vTMrPCjep5+O7777jMSThSnB8ULjrvC0WXJiZQpG0c4Qch+hB8WzUCA1UBF3QwQIVapLq88IN/u+//0YIIZ7zOBIKYQoPAkDEIixBJNIjeRh+XwgkD2L8wXyrmySPCyMiQ+FTv/fee00LE45RHF2NaZX836L4i6DL12/b3oWJ+VnLlClj5tdnzZolR48ejWIVukUqCKEQpkDwBYUViFBYLLu1RjlPeH1gTHEzgUAZ5AliGTccrtXdtGlTjlMcGNIp5gLa855W8uIQ26Xix/eUXPk2+v2xX6LlOaZJYzrUR/dAwAGPJ6EQpmAQAeRWOsHFGr95AK9fCHFxxNwfnlerVs2sx9wr5gdxsxFL2DFRttOEt6Btn6ukQoloJvbzKVn1un8hvLhR3/jlTZzPCU9KdI/Lly9zzp1QCIMhoAN3rIgaxe84tNYgcRBCJMyj4Dae165dO2IbcjSxDsW3OVYxA5emp5idXq2kQK7YXzeiq38xfG1E9K/B+Y8HApgCiRJt2bKlmedFUIz347///ovzjSUK3HM+kVAIkzhtAnep/mAOYezhxHCDHT9+3ORfHjp0SK5cuWLEzd0HAUduA8tHHnnEzB0hTQXiiPlYdp+IGydWRhWyL16L+2vnPOkrhAeWRr8/onvxcKcJ4gJCyPHA/CDc4Ii4HjdunKnKj4T7QBrzPvvss6acG487oRAmocUSHZ7VA4h/KlWqJP/884+5CB45csQ0b/V2lSE/MLoxhjjG0r4k7EFSvLeQQdzifLOXScmhZb7vUfJm//sjvQUPWHgoi4ZKMu5NIeosovs8bmiQa4t1mEZYt26dec2GDRtk/PjxUqpUKalRo4apwjFixAhTYB37Ys4dFh8qzXh6B1DT96WXXpI5c+bI2rVr5ddff+WxJxTCpKJEiRKmqow/YLEwpN9PQnfZsuYiCFAwG0nxeKxYscJYBG4NPu8gCswFwt2GCF1cBFm0IO788UlUEdsSYIf5WU/4CuGd1f3vi1xPPGDdnzlzxjxHTiDcn19//bVZ3rNnj/mN6F9YfNu2bTPL+D19+nQjoJgTvHbtmpw8edJYePCyHDx4UP7++2/56aefzP7o7o0av2fPnjWeBff9kYzP404ohEkMKprAZYc7X1ywMU9Cl51/rl69GmNxZVxA/bUmwZwgLAS4yVzy589v9kUhA4giarxyjH1Bpwnv7vSZA2iuO6i9rxB2u8v/vh07djTHcc2aNeZ4/Pjjj/LGG2/Iww8/bNaj0j7cpni4Ub+4ucEDLlEsT5482Qhh8+bNjWWIeUOIJkQQKTVbt2414gf3+pQpU+Svv/6KKFH15Zdfyvr163ncCYUwqUG3CUzmY34DvfFGjhxpXKO4C+ZBjEr9+vWN2wwXPdw8oEoM3GUowowuznB7wQL0fA0ucrAcY3JFu65S1qT05d5mvkI2YVDcXw/R8359m/oxW4QofYflXbt2ybx582T+/PkRLktY9p5C6EaLQkRdIdy7d2+U933//fcjAmfgAoX7FOuXLVtmeh26+3300UfG1crjTiiESUz16tVN0W1c5DHngeRvCGEgkXPhCOZ+4M7ChfLDDz+UHTt2mIsdxtF7X8wDwRXWpEmTiHlDBFNg2W3Wy/6P0SfUewfMIA0CvQlfGaZk0Xgl38xQ8usSJcf1fhc22O1HltuIUxTj9hbC8sX9/y0ENnlGjcIyxLwdvh/nzp0z6yGSePTr1y8iDxcP1OqNTginTZsmFy9eNDdPsAThUsX5gPZOWI+/W6tWLfntt99k8+bNPO6EQpjUuOH9ED+kTsA9imXOYcXMxo0bTeTo4cOHZffu3eYiBvdXbFYdtsP9jDFGwA3HMnaQCH+9lWU8i3GnjqYQN2rA4pi63hAEsRw4cMC4sD0LaZ86dSrCkoPbGw/c0GAZc4RwqXpPP6xatcpYhHhAVPG3ECy1YMECI4Z44PzhHGHCUzifvslpomRAOyXj+tsiDAVzc1wohH4sFgTI4Dm6JbAKRhyCOP74w8zxuMsIMoqtgggiDzEXi/lCCGEcujuHPem0Rbh2WsIJIVo3xRip6pE6BLe2Z64nAqIQZAYB81wPaw7H1hU9zBv6e29EYuN13u5zzEfiNQhQYyRxAt/o57bVhq5+a+vVwruAggs5buLYUAjJdbNo0SKT/Ay3Jqw8zC8hUhAi551wDbeZZ3k1F1xYOZaBR33Gl/++1mJWhGMaTnS4wx77UoU5FhRCkuBgvgih8XicP3/e/PYX8Yc5JMwHIpgGyfdwiyLgBtGHDI6JmeIFr6/rhDfjB3JMw4U0qZXkzqakWAFrBa6bbvNHb8zIsaEQkgQF86iw7GbMmGGiBhFo5G8/fzmZcKFGcaPeoEntPM/n9R7oth5dG6HUXsvZNSHyZYf76tJGX0FDQAwqzGDusH9bJd3vUtK2gZLmtZS0a2iXh92jZNpoJT/Ms++BmqUZ0/OcDQeQXnPs8+hviPZ/bIOwOFYUQpLAuHNEcQUl2GAhRqx7VqMv8Gq7Bj+jnfXPaS456xZrEOiBC/pkzd+ay87zDJqPnf2uaJ4KjXF9fWTUQBc06M2VLbD3oBUQni5R3Bx1aqyka1N7c4TfPVtYOEYUQpIAIIx++fLlEeW2Jk2aZOYNvfdDwjzyDFGuC1VKEIoPNyksSZTrQj1SE3E4zRExbcGo+Zqxml7OusEabdGokx6ieULTXdPN2Wegs66rZodmV+iMdcWSSjo2soEzPPdIIBTKo2TiYOsmBa8+pKR9Q44LhZAkCAifv3Dhgly6dMkEyEDsECzjXaMVUYTDhw83ooe5QgTNoHAB0lUgghDHChUqiJqu91/jvC69Y/kt1HzjrEMvvT+c5zs1bzjP73eEcIHmtGMNfqQpxWNECFznh5dbtzj6Vf79pQ2aopeAQkgSgD///NPkjKHg9tSpU01YvGdVkthAFRqU3IpYByH83Gu/JzS7HbcnrMKLzpyg/mKrOZqcmu8dS/E9x1Vaw5lj7Oq8LgTGGj0EezRX0qtlYMAFBncY3GSYP8zMi1/YAU8C5gSRQ3hmjc0pRCpFvUocGwohuW42bdpkeP75503tSLg78TuupemQUB2lY8UUzTKv/WDV7dOcd0QQc4WY32isOerMD55z3KAlNVs0Vx0Lca8mb/CPM1xZCRU5ijqlI7vx3A0nECmKPMLL3yh5qq+9qcK5cFtxjg2FkFw36EOH4spuVRC4RRcvXhz/98yvKRbNtoqO9Qexu9FZl9ZZ/4FjIbr7QvxCJFcOQTEXNyZc+gQ4+QUjBsON1q0tpnhBFiUjRnBMKIQkwcD8H+b4ECQzYcKEaNMnEpwhjtt0umMpDgjN8UXye0KKoEvR/Dx3w4U7qkYed7fCDIiuFRehEJIAQP1JVJNJluLk6L6wQvNJ6Iqgy+ZZCSuCO+fz3A03kFvar42SBzooef4Bex7ky8lxoRCS62bfvn3GJfrqq69yPBKR/Lns3M622TY5fsdcy3aH7+dYsB2cXRu9CE4ebkPoOa7hC4JlcC7UuJVjQSEk182GDRtM+yXvAsokealQQsmBpf6FsHcrjk+4Ubu8ku9mKvn0JSWfvaJkzyKbPpEtC8eGQkiumyFDhsjp06eldu3aHI8URp7sthehtxAeXMpAmXADwTFvP2qFcOFzSl4bYQs0cGwohCQBmDt3bkS0KKJHr1y5YvrMURhTBqhCA1eqtxgO78KxCSfgCv/weSVvPIxeoBwPCiFJUNq2bWs6jCNa1AVl01BSjeOTMhjY3lcIj33OcQknGlWzeahDOnEsKIQkUUBXiXr16pl+g/nz5+eYpDBuutEm0XuLIepPcnzCA7RjQu7ovc04FhRCkuA0adLEdKl3H2fOnJEWLVpwbFIY7471FcImNTgu4QJaeR1aZnMHN72j5NsZStZPV1KaDZophOT6OXz4sOzevduUSatVq5Zs27ZNTp48GecSayTxLnwoo4aL3a9LlJz6ylcIH+zMcQoXEB2K7hNo5+XyyjDmEVIIyXWTM2dOYwWivqi7rnPnzmad6STBMUo2nh0Qe1I9GvpyrMKDtGlsE+fyTm1RFOFGBCnHhkJIEmBu8ODBg/Lpp59GrBs8eLARQlSc4RglH0i2j00I+7bmOIULKLGGgttZM9vlskXtOVCiEMeGQkjiBRrxYi4QIGXCnRsE6EuIVIrs2bNzrJIRz472/kCX+7w5OE5hE93dQMn59ZG5gyjkjijSyuzXSSEk8eP222+XkSNHyqhRo/wCqxDWIscq+bi1mK0n6k8EMW9YpTTHKJxAQ14cd5TeWzBOyZZZSvZ9yHGhEBISBsAFhma+3e+yuWS3FOSYhCuoJtSpsU2sX/aSkkq0BimEhBBCSFLw/4IKEZF1CGcLAAAAAElFTkSuQmCC" width="450" height="225" alt="" />

### The Book of Mormon

```clojure
(word-cloud "The Book of Mormon")
```

<img src="data:image/png;base64,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" width="450" height="225" alt="" />

### The Dhammapada

```clojure
(word-cloud "The Dhammapada")
```

<img src="data:image/png;base64,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" width="450" height="225" alt="" />

Now things are really interesting!
Fear is a common theme amongst all of the texts (except Vedas, which would have been a really boring word cloud).

The Jewish Scriptures, the Bible, and the Book of Mormon all have a large number of sentences containing "destroy", something much less prominent in the Koran.
The Bible also has a large number of sentences with "kill", though this is likely a consequence of the translation as much as anything else.
The other texts use much more formal language.

Another standout is the prominence of "suffer" in both the Dhammapada and the Book of Mormon - our two "most violent" texts.
We can get a sense of context by just sampling the sentences that match our queries.

```clojure

(doseq [s (->>  violent-sentences 
                ;; Get only Dhammapada.
     		    (filter #(= (:book %) "The Dhammapada"))
                ;; Rearrange.
     		    shuffle
                ;; Grab the first ten.
     		    (take 10)
                ;; Extract the sentence.
     		    (map :sentence))]
  (println s)
  (println))
```

```
He who has tasted the sweetness of solitude and tranquillity, is free from fear and free from sin, while he tastes the sweetness of drinking in the law.

The brilliant chariots of kings are destroyed, the body also approaches destruction, but the virtue of good people never approaches destruction,--thus do the good say to the good.

Beware of the anger of the tongue, and control thy tongue!

A man is not learned because he talks much; he who is patient, free from hatred and fear, he is called learned.

A Bhikshu (mendicant) who delights in earnestness, who looks with fear on thoughtlessness, moves about like fire, burning all his fetters, small or large.

There is no suffering for him who has finished his journey, and abandoned grief, who has freed himself on all sides, and thrown off all fetters.

Him I call indeed a Brahmana from whom anger and hatred, pride and envy have dropt like a mustard seed from the point of a needle.

From pleasure comes grief, from pleasure comes fear; he who is free from pleasure knows neither grief nor fear.

He who overcomes this fierce thirst, difficult to be conquered in this world, sufferings fall off from him, like water-drops from a lotus leaf.

The evil-doer suffers in this world, and he suffers in the next; he suffers in both.


```

<span class='clj-nil'>nil</span>

It's pretty clear from this sample that we're looking at advice _against_ violence here.
This makes sense, the Dhammapada is a collection of sayings, not an actual story.
Suffering is also a key theme in Buddhism, so it's no surprise we see it heavily represented.

Let's look at the Book of Mormon.

```clojure
(doseq [s (->>  violent-sentences 
                ;; Get only Book of Mormon.
     		    (filter #(= (:book %) "The Book of Mormon"))
                ;; Rearrange.
     		    shuffle
                ;; Grab the first ten.
     		    (take 10)
                ;; Extract the sentence.
     		    (map :sentence))]
  (println s)
  (println))
```

```
Alma :   Now it came to pass that when Lehi and Moroni knew that Teancum was dead they were exceedingly sorrowful; for behold, he had been a man who had fought valiantly for his country, yea, a true friend to liberty; and he had suffered very many exceedingly sore afflictions.

Moroni :   And now behold, my son, I fear lest the Lamanites shall destroy this people; for they do not repent, and Satan stirreth them up continually to anger one with another.

Behold, my soul is rent with anguish because of you, and my heart is pained; I fear lest ye shall be cast off forever.

Alma :   For behold, I have somewhat to say unto them by the way of condemnation; for behold, ye yourselves know that ye have been appointed to gather together men, and arm them with swords, and with cimeters, and all manner of weapons of war of every kind, and send forth against the Lamanites, in whatsoever parts they should come into our land.

I say unto you, Nay; he would rather suffer that the Lamanites might destroy all his people who are called the people of Nephi, if it were possible that they could fall into sins and transgressions, after having had so much light and so much knowledge given unto them of the Lord their God; Alma :   Yea, after having been such a highly favored people of the Lord; yea, after having been favored above every other nation, kindred, tongue, or people; after having had all things made known unto them, according to their desires, and their faith, and prayers, of that which has been, and which is, and which is to come; Alma :   Having been visited by the Spirit of God; having conversed with angels, and having been spoken unto by the voice of the Lord; and having the spirit of prophecy, and the spirit of revelation, and also many gifts, the gift of speaking with tongues, and the gift of preaching, and the gift of the Holy Ghost, and the gift of translation; Alma :   Yea, and after having been delivered of God out of the land of Jerusalem, by the hand of the Lord; having been saved from famine, and from sickness, and all manner of diseases of every kind; and they having waxed strong in battle, that they might not be destroyed; having been brought out of bondage time after time, and having been kept and preserved until now; and they have been prospered until they are rich in all manner of things-- Alma :   And now behold I say unto you, that if this people, who have received so many blessings from the hand of the Lord, should transgress contrary to the light and knowledge which they do have, I say unto you that if this be the case, that if they should fall into transgression, it would be far more tolerable for the Lamanites than for them.

Jacob :   And a hundredth part of the proceedings of this people, which now began to be numerous, cannot be written upon these plates; but many of their proceedings are written upon the larger plates, and their wars, and their contentions, and the reigns of their kings.

Alma :   And he lifted up his voice to heaven, and cried, saying: O, how long, O Lord, wilt thou suffer that thy servants shall dwell here below in the flesh, to behold such gross wickedness among the children of men? Alma :   Behold, O God, they cry unto thee, and yet their hearts are swallowed up in their pride.

Alma :   Yea, had it not been for the war which broke out among ourselves; yea, were it not for these king-men, who caused so much bloodshed among ourselves; yea, at the time we were contending among ourselves, if we had united our strength as we hitherto have done; yea, had it not been for the desire of power and authority which those king-men had over us; had they been true to the cause of our freedom, and united with us, and gone forth against our enemies, instead of taking up their swords against us, which was the cause of so much bloodshed among ourselves; yea, if we had gone forth against them in the strength of the Lord, we should have dispersed our enemies, for it would have been done, according to the fulfilling of his word.

Alma :   Therefore they did not fear Ammon, for they supposed that one of their men could slay him according to their pleasure, for they knew not that the Lord had promised Mosiah that he would deliver his sons out of their hands; neither did they know anything concerning the Lord; therefore they delighted in the destruction of their brethren; and for this cause they stood to scatter the flocks of the king.

Now behold, I will show unto you that they did not establish a king over the land; but in this same year, yea, the thirtieth year, they did destroy upon the judgment seat, yea, did murder the chief judge of the land.


```

<span class='clj-nil'>nil</span>

The Book or Mormon, on the other hand,contains a mixture of sayings and warnings against violence with actual violence in the form of a story.
This is very much in line with the style seen in the Jewish Scriptures and the Bible, which are presented as a mixture of narrative, dialogue, and proverb.
