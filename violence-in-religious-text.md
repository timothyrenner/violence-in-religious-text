# A Quantitative Look at Violence in Religious Texts

There's been a lot of dicussion about religion and violence in light of recent events, with some claiming that certain religions are inherently more violent than others.
I thought it would be interesting to examine this assertion by looking at holy texts from six major religions (Buddhism, Christianity, Hinduism, Islam, Judaism, and the Church of Jesus Christ of Latter-day Saints) and "measuring" the violence in each.

Now obviously there's no way this analysis is a complete treatment of the subject.
This subject is incredibly nuanced; an (admittedly incomplete) examination of the texts is one very small facet of the overall discussion.
Regardless, I think it's well worth a look, and the results may surprise you.
At the very least it's quite an exercise in data cleaning - if you're into that sort of thing.

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

<span class='clj-var'>#&#x27;violence-in-religious-text-nb/get-sentences</span>

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
I will spare you the details, but suffice it to say there's a solid amount of code required. 
Hit the code bar at your own risk.

```clojure
;; Each of these texts requires a substantial amount of cleaning.
;; Rather than scrub this offline, I'll do it here so it can be reproduced easily just by downloading the files.
;; The scrubbing is not trivial.

;; In addition to formatting differences, each text has front and tail matter that should be removed.
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

<span class='clj-var'>#&#x27;violence-in-religious-text-nb/book-of-mormon</span>

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
This is where the arbitrary nature of the analysis rears its ugly head.
Here's 24 words I picked kind-of randomly with a little googling.

**wound, hurt, fight, violate, destroy, slaughter, murder, kill, attack, break, crush, provoke, anger, hatred, bloodshed, rage, fear, suffer, violent, war, stab, shoot strike, rape**.

[Clone](https://github.com/timothyrenner/violence-in-religious-text) and run the notebook if you want to pick your own list of violent words (or any words, actually).

```clojure
(def violent-words
  ["wound"     "hurt"    "fight"  "violate" "destroy" 
   "slaughter" "murder"  "kill"   "attack"  "break" 
   "crush"     "provoke" "anger"  "hatred"  "bloodshed" 
   "rage"      "fear"    "suffer" "violent" 
   "war"       "stab"    "shoot"  "strike"  "rape"])
```

<span class='clj-var'>#&#x27;violence-in-religious-text-nb/violent-words</span>

Finally, we need a way to detect when a word is _in_ a sentence.
This is even harder than detecting sentences; we have to detect _words_ within a sentence.

Suppose we want to search a book for instances of the word _kill_.
We can search for the exact word and we'll do okay, but we'd miss any that were at the end of a sentence (_kill?_, _kill!_, _kill._) or really any instance followed by punctuation (_kill,_, _kill;_).
We'd also miss words we may want to count: _killing_, _killed_, etc.

Luckily, this is not an uncommon need; it's called the _search problem_.
There are a number of ways to tackle this, but since I'm feeling especially lazy I opted for [Apache Lucene](http://lucene.apache.org/).
Lucene is essentially a document store that let's you pull documents based on textual queries.
It does this by analyzing the documents as they're added and indexes them based on their contents using natural language processing techniques with (probably) a sprinkle of magic.
We can use it to index each sentence of each book, then perform a search on the words we want.
At that point all we'd need to do is count the results of our search and we have our answer.

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

<span class='clj-var'>#&#x27;violence-in-religious-text-nb/violent-sentence-counts</span>

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
(spit "images/violent-count-plot.svg" (gg4clj/render violent-count-plot))

;; Render it in the REPL.
(gg4clj/view violent-count-plot)
```

<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="468pt" height="289pt" viewBox="0 0 468 289" version="1.1">
<defs>
<g>
<symbol overflow="visible" id="24c6aa26-c4d3-4745-9361-b540f5284f4a">
<path style="stroke:none;" d="M 0.453125 0 L 0.453125 -10.171875 L 8.53125 -10.171875 L 8.53125 0 Z M 7.25 -1.265625 L 7.25 -8.890625 L 1.734375 -8.890625 L 1.734375 -1.265625 Z M 7.25 -1.265625 "/>
</symbol>
<symbol overflow="visible" id="e1f59f04-5e4e-4005-845e-f577cc40f92a">
<path style="stroke:none;" d="M 1.359375 -7.015625 L 1.359375 -7.96875 C 2.253906 -8.0625 2.878906 -8.207031 3.234375 -8.40625 C 3.585938 -8.613281 3.851562 -9.101562 4.03125 -9.875 L 5.015625 -9.875 L 5.015625 0 L 3.6875 0 L 3.6875 -7.015625 Z M 1.359375 -7.015625 "/>
</symbol>
<symbol overflow="visible" id="666c3977-4de8-4c0e-95f1-8d3c61fb29aa">
<path style="stroke:none;" d="M 1.171875 1.453125 C 1.492188 1.390625 1.71875 1.164062 1.84375 0.78125 C 1.914062 0.570312 1.953125 0.367188 1.953125 0.171875 C 1.953125 0.140625 1.945312 0.109375 1.9375 0.078125 C 1.9375 0.0546875 1.9375 0.03125 1.9375 0 L 1.171875 0 L 1.171875 -1.515625 L 2.65625 -1.515625 L 2.65625 -0.109375 C 2.65625 0.441406 2.546875 0.921875 2.328125 1.328125 C 2.109375 1.742188 1.722656 2.003906 1.171875 2.109375 Z M 1.171875 1.453125 "/>
</symbol>
<symbol overflow="visible" id="944ffb7c-627f-4dfd-9d04-e2d0af53ba08">
<path style="stroke:none;" d="M 3.828125 -9.90625 C 5.109375 -9.90625 6.035156 -9.378906 6.609375 -8.328125 C 7.054688 -7.503906 7.28125 -6.382812 7.28125 -4.96875 C 7.28125 -3.625 7.078125 -2.507812 6.671875 -1.625 C 6.097656 -0.363281 5.148438 0.265625 3.828125 0.265625 C 2.640625 0.265625 1.753906 -0.25 1.171875 -1.28125 C 0.679688 -2.144531 0.4375 -3.300781 0.4375 -4.75 C 0.4375 -5.875 0.582031 -6.84375 0.875 -7.65625 C 1.425781 -9.15625 2.410156 -9.90625 3.828125 -9.90625 Z M 3.8125 -0.859375 C 4.457031 -0.859375 4.972656 -1.144531 5.359375 -1.71875 C 5.742188 -2.289062 5.9375 -3.359375 5.9375 -4.921875 C 5.9375 -6.046875 5.796875 -6.96875 5.515625 -7.6875 C 5.242188 -8.414062 4.707031 -8.78125 3.90625 -8.78125 C 3.175781 -8.78125 2.640625 -8.4375 2.296875 -7.75 C 1.960938 -7.0625 1.796875 -6.046875 1.796875 -4.703125 C 1.796875 -3.691406 1.90625 -2.878906 2.125 -2.265625 C 2.445312 -1.328125 3.007812 -0.859375 3.8125 -0.859375 Z M 3.8125 -0.859375 "/>
</symbol>
<symbol overflow="visible" id="9f3ee9e5-2e24-446e-a5cf-37bf69073e19">
<path style="stroke:none;" d="M 3.6875 0.265625 C 2.507812 0.265625 1.65625 -0.0507812 1.125 -0.6875 C 0.601562 -1.332031 0.34375 -2.117188 0.34375 -3.046875 L 1.640625 -3.046875 C 1.691406 -2.398438 1.8125 -1.929688 2 -1.640625 C 2.320312 -1.117188 2.90625 -0.859375 3.75 -0.859375 C 4.40625 -0.859375 4.929688 -1.035156 5.328125 -1.390625 C 5.722656 -1.742188 5.921875 -2.195312 5.921875 -2.75 C 5.921875 -3.425781 5.710938 -3.898438 5.296875 -4.171875 C 4.878906 -4.453125 4.300781 -4.59375 3.5625 -4.59375 C 3.476562 -4.59375 3.390625 -4.585938 3.296875 -4.578125 C 3.210938 -4.578125 3.128906 -4.578125 3.046875 -4.578125 L 3.046875 -5.671875 C 3.171875 -5.660156 3.273438 -5.648438 3.359375 -5.640625 C 3.453125 -5.640625 3.550781 -5.640625 3.65625 -5.640625 C 4.125 -5.640625 4.503906 -5.710938 4.796875 -5.859375 C 5.328125 -6.117188 5.59375 -6.582031 5.59375 -7.25 C 5.59375 -7.738281 5.414062 -8.113281 5.0625 -8.375 C 4.71875 -8.644531 4.3125 -8.78125 3.84375 -8.78125 C 3.007812 -8.78125 2.4375 -8.503906 2.125 -7.953125 C 1.945312 -7.648438 1.84375 -7.21875 1.8125 -6.65625 L 0.59375 -6.65625 C 0.59375 -7.394531 0.738281 -8.023438 1.03125 -8.546875 C 1.539062 -9.460938 2.429688 -9.921875 3.703125 -9.921875 C 4.710938 -9.921875 5.492188 -9.695312 6.046875 -9.25 C 6.609375 -8.800781 6.890625 -8.148438 6.890625 -7.296875 C 6.890625 -6.679688 6.722656 -6.1875 6.390625 -5.8125 C 6.191406 -5.570312 5.929688 -5.390625 5.609375 -5.265625 C 6.128906 -5.117188 6.535156 -4.835938 6.828125 -4.421875 C 7.117188 -4.015625 7.265625 -3.519531 7.265625 -2.9375 C 7.265625 -1.988281 6.953125 -1.21875 6.328125 -0.625 C 5.703125 -0.03125 4.820312 0.265625 3.6875 0.265625 Z M 3.6875 0.265625 "/>
</symbol>
<symbol overflow="visible" id="99f9ca60-18bc-479b-a193-d20416ed80f5">
<path style="stroke:none;" d="M 7.40625 -9.75 L 7.40625 -8.65625 C 7.09375 -8.34375 6.671875 -7.800781 6.140625 -7.03125 C 5.609375 -6.269531 5.140625 -5.445312 4.734375 -4.5625 C 4.335938 -3.695312 4.035156 -2.910156 3.828125 -2.203125 C 3.691406 -1.742188 3.519531 -1.007812 3.3125 0 L 1.9375 0 C 2.25 -1.894531 2.9375 -3.773438 4 -5.640625 C 4.632812 -6.742188 5.300781 -7.691406 6 -8.484375 L 0.515625 -8.484375 L 0.515625 -9.75 Z M 7.40625 -9.75 "/>
</symbol>
<symbol overflow="visible" id="1e5a3a02-b92d-49ca-96c1-e5a9ee43a644">
<path style="stroke:none;" d="M 0.4375 0 C 0.488281 -0.851562 0.664062 -1.59375 0.96875 -2.21875 C 1.28125 -2.851562 1.878906 -3.429688 2.765625 -3.953125 L 4.09375 -4.71875 C 4.6875 -5.0625 5.101562 -5.359375 5.34375 -5.609375 C 5.726562 -5.984375 5.921875 -6.421875 5.921875 -6.921875 C 5.921875 -7.492188 5.742188 -7.945312 5.390625 -8.28125 C 5.046875 -8.625 4.585938 -8.796875 4.015625 -8.796875 C 3.160156 -8.796875 2.566406 -8.472656 2.234375 -7.828125 C 2.066406 -7.484375 1.972656 -7.003906 1.953125 -6.390625 L 0.6875 -6.390625 C 0.695312 -7.253906 0.851562 -7.957031 1.15625 -8.5 C 1.695312 -9.457031 2.648438 -9.9375 4.015625 -9.9375 C 5.148438 -9.9375 5.976562 -9.628906 6.5 -9.015625 C 7.03125 -8.410156 7.296875 -7.726562 7.296875 -6.96875 C 7.296875 -6.175781 7.015625 -5.5 6.453125 -4.9375 C 6.128906 -4.613281 5.550781 -4.21875 4.71875 -3.75 L 3.765625 -3.21875 C 3.316406 -2.96875 2.960938 -2.734375 2.703125 -2.515625 C 2.242188 -2.109375 1.953125 -1.660156 1.828125 -1.171875 L 7.25 -1.171875 L 7.25 0 Z M 0.4375 0 "/>
</symbol>
<symbol overflow="visible" id="bba4a26d-021f-4a7d-b42d-4c8e89e912f6">
<path style="stroke:none;" d="M 3.859375 -5.75 C 4.398438 -5.75 4.828125 -5.898438 5.140625 -6.203125 C 5.453125 -6.515625 5.609375 -6.882812 5.609375 -7.3125 C 5.609375 -7.6875 5.457031 -8.023438 5.15625 -8.328125 C 4.863281 -8.640625 4.414062 -8.796875 3.8125 -8.796875 C 3.207031 -8.796875 2.769531 -8.640625 2.5 -8.328125 C 2.238281 -8.023438 2.109375 -7.664062 2.109375 -7.25 C 2.109375 -6.78125 2.28125 -6.410156 2.625 -6.140625 C 2.976562 -5.878906 3.390625 -5.75 3.859375 -5.75 Z M 3.9375 -0.84375 C 4.507812 -0.84375 4.984375 -1 5.359375 -1.3125 C 5.742188 -1.625 5.9375 -2.09375 5.9375 -2.71875 C 5.9375 -3.351562 5.738281 -3.835938 5.34375 -4.171875 C 4.957031 -4.503906 4.457031 -4.671875 3.84375 -4.671875 C 3.25 -4.671875 2.757812 -4.5 2.375 -4.15625 C 2 -3.820312 1.8125 -3.351562 1.8125 -2.75 C 1.8125 -2.238281 1.984375 -1.789062 2.328125 -1.40625 C 2.671875 -1.03125 3.207031 -0.84375 3.9375 -0.84375 Z M 2.15625 -5.28125 C 1.8125 -5.425781 1.539062 -5.597656 1.34375 -5.796875 C 0.976562 -6.171875 0.796875 -6.648438 0.796875 -7.234375 C 0.796875 -7.972656 1.0625 -8.609375 1.59375 -9.140625 C 2.132812 -9.671875 2.894531 -9.9375 3.875 -9.9375 C 4.832031 -9.9375 5.578125 -9.6875 6.109375 -9.1875 C 6.648438 -8.6875 6.921875 -8.101562 6.921875 -7.4375 C 6.921875 -6.8125 6.765625 -6.3125 6.453125 -5.9375 C 6.273438 -5.71875 6.003906 -5.503906 5.640625 -5.296875 C 6.046875 -5.109375 6.367188 -4.890625 6.609375 -4.640625 C 7.046875 -4.179688 7.265625 -3.582031 7.265625 -2.84375 C 7.265625 -1.96875 6.972656 -1.226562 6.390625 -0.625 C 5.804688 -0.0195312 4.976562 0.28125 3.90625 0.28125 C 2.9375 0.28125 2.117188 0.0195312 1.453125 -0.5 C 0.785156 -1.019531 0.453125 -1.78125 0.453125 -2.78125 C 0.453125 -3.363281 0.59375 -3.867188 0.875 -4.296875 C 1.164062 -4.722656 1.59375 -5.050781 2.15625 -5.28125 Z M 2.15625 -5.28125 "/>
</symbol>
<symbol overflow="visible" id="55311ce1-a84c-4978-992f-599891defeb0">
<path style="stroke:none;" d="M 4.6875 -3.515625 L 4.6875 -8 L 1.515625 -3.515625 Z M 4.703125 0 L 4.703125 -2.421875 L 0.359375 -2.421875 L 0.359375 -3.640625 L 4.90625 -9.9375 L 5.953125 -9.9375 L 5.953125 -3.515625 L 7.40625 -3.515625 L 7.40625 -2.421875 L 5.953125 -2.421875 L 5.953125 0 Z M 4.703125 0 "/>
</symbol>
<symbol overflow="visible" id="7e029b41-3520-491e-b479-90727ece6964">
<path style="stroke:none;" d="M 1.75 -2.53125 C 1.832031 -1.8125 2.160156 -1.316406 2.734375 -1.046875 C 3.035156 -0.910156 3.378906 -0.84375 3.765625 -0.84375 C 4.503906 -0.84375 5.050781 -1.078125 5.40625 -1.546875 C 5.757812 -2.015625 5.9375 -2.535156 5.9375 -3.109375 C 5.9375 -3.804688 5.722656 -4.34375 5.296875 -4.71875 C 4.878906 -5.09375 4.375 -5.28125 3.78125 -5.28125 C 3.351562 -5.28125 2.984375 -5.195312 2.671875 -5.03125 C 2.367188 -4.863281 2.109375 -4.632812 1.890625 -4.34375 L 0.8125 -4.40625 L 1.578125 -9.75 L 6.71875 -9.75 L 6.71875 -8.546875 L 2.5 -8.546875 L 2.078125 -5.78125 C 2.304688 -5.957031 2.523438 -6.085938 2.734375 -6.171875 C 3.109375 -6.328125 3.535156 -6.40625 4.015625 -6.40625 C 4.929688 -6.40625 5.703125 -6.113281 6.328125 -5.53125 C 6.960938 -4.945312 7.28125 -4.203125 7.28125 -3.296875 C 7.28125 -2.359375 6.988281 -1.53125 6.40625 -0.8125 C 5.832031 -0.101562 4.910156 0.25 3.640625 0.25 C 2.828125 0.25 2.109375 0.0234375 1.484375 -0.421875 C 0.867188 -0.878906 0.523438 -1.582031 0.453125 -2.53125 Z M 1.75 -2.53125 "/>
</symbol>
<symbol overflow="visible" id="1ed57c79-5767-4507-8d8b-1b348eb28d8c">
<path style="stroke:none;" d="M 0.3125 0 L 0.3125 -6.875 L 5.765625 -6.875 L 5.765625 0 Z M 4.90625 -0.859375 L 4.90625 -6.015625 L 1.171875 -6.015625 L 1.171875 -0.859375 Z M 4.90625 -0.859375 "/>
</symbol>
<symbol overflow="visible" id="659056ac-269c-4440-86be-8021d19011db">
<path style="stroke:none;" d="M 5.734375 -6.875 L 5.734375 -6.0625 L 3.421875 -6.0625 L 3.421875 0 L 2.46875 0 L 2.46875 -6.0625 L 0.15625 -6.0625 L 0.15625 -6.875 Z M 5.734375 -6.875 "/>
</symbol>
<symbol overflow="visible" id="ffd5e809-4dbb-44a8-89b0-c7eee7ab2dea">
<path style="stroke:none;" d="M 0.625 -6.90625 L 1.46875 -6.90625 L 1.46875 -4.34375 C 1.664062 -4.59375 1.84375 -4.769531 2 -4.875 C 2.269531 -5.050781 2.609375 -5.140625 3.015625 -5.140625 C 3.742188 -5.140625 4.238281 -4.882812 4.5 -4.375 C 4.632812 -4.09375 4.703125 -3.707031 4.703125 -3.21875 L 4.703125 0 L 3.84375 0 L 3.84375 -3.15625 C 3.84375 -3.53125 3.796875 -3.800781 3.703125 -3.96875 C 3.546875 -4.25 3.257812 -4.390625 2.84375 -4.390625 C 2.488281 -4.390625 2.171875 -4.265625 1.890625 -4.015625 C 1.609375 -3.773438 1.46875 -3.320312 1.46875 -2.65625 L 1.46875 0 L 0.625 0 Z M 0.625 -6.90625 "/>
</symbol>
<symbol overflow="visible" id="f9964a6e-4473-4aa2-9d0f-7ffba800f5a6">
<path style="stroke:none;" d="M 2.703125 -5.125 C 3.054688 -5.125 3.398438 -5.039062 3.734375 -4.875 C 4.078125 -4.707031 4.332031 -4.492188 4.5 -4.234375 C 4.675781 -3.972656 4.789062 -3.675781 4.84375 -3.34375 C 4.894531 -3.113281 4.921875 -2.742188 4.921875 -2.234375 L 1.234375 -2.234375 C 1.253906 -1.722656 1.375 -1.3125 1.59375 -1 C 1.820312 -0.695312 2.171875 -0.546875 2.640625 -0.546875 C 3.085938 -0.546875 3.441406 -0.691406 3.703125 -0.984375 C 3.847656 -1.148438 3.953125 -1.347656 4.015625 -1.578125 L 4.84375 -1.578125 C 4.820312 -1.390625 4.75 -1.179688 4.625 -0.953125 C 4.507812 -0.734375 4.375 -0.550781 4.21875 -0.40625 C 3.957031 -0.15625 3.640625 0.015625 3.265625 0.109375 C 3.054688 0.148438 2.828125 0.171875 2.578125 0.171875 C 1.953125 0.171875 1.421875 -0.0507812 0.984375 -0.5 C 0.554688 -0.957031 0.34375 -1.59375 0.34375 -2.40625 C 0.34375 -3.21875 0.5625 -3.875 1 -4.375 C 1.4375 -4.875 2.003906 -5.125 2.703125 -5.125 Z M 4.046875 -2.90625 C 4.015625 -3.269531 3.9375 -3.5625 3.8125 -3.78125 C 3.582031 -4.1875 3.195312 -4.390625 2.65625 -4.390625 C 2.269531 -4.390625 1.941406 -4.25 1.671875 -3.96875 C 1.410156 -3.695312 1.273438 -3.34375 1.265625 -2.90625 Z M 2.625 -5.140625 Z M 2.625 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="1f74055e-05d4-44c8-8db4-06e2f87a1cb9">
<path style="stroke:none;" d=""/>
</symbol>
<symbol overflow="visible" id="848cac35-f92f-4404-90fd-7af43acabf9f">
<path style="stroke:none;" d="M 3.3125 -3.96875 C 3.707031 -3.96875 4.015625 -4.023438 4.234375 -4.140625 C 4.578125 -4.304688 4.75 -4.613281 4.75 -5.0625 C 4.75 -5.507812 4.566406 -5.8125 4.203125 -5.96875 C 3.992188 -6.0625 3.6875 -6.109375 3.28125 -6.109375 L 1.625 -6.109375 L 1.625 -3.96875 Z M 3.625 -0.796875 C 4.195312 -0.796875 4.609375 -0.960938 4.859375 -1.296875 C 5.003906 -1.503906 5.078125 -1.753906 5.078125 -2.046875 C 5.078125 -2.546875 4.851562 -2.890625 4.40625 -3.078125 C 4.175781 -3.171875 3.863281 -3.21875 3.46875 -3.21875 L 1.625 -3.21875 L 1.625 -0.796875 Z M 0.703125 -6.875 L 3.65625 -6.875 C 4.46875 -6.875 5.039062 -6.632812 5.375 -6.15625 C 5.582031 -5.875 5.6875 -5.546875 5.6875 -5.171875 C 5.6875 -4.742188 5.5625 -4.390625 5.3125 -4.109375 C 5.1875 -3.960938 5.003906 -3.828125 4.765625 -3.703125 C 5.109375 -3.566406 5.367188 -3.414062 5.546875 -3.25 C 5.859375 -2.945312 6.015625 -2.535156 6.015625 -2.015625 C 6.015625 -1.566406 5.875 -1.164062 5.59375 -0.8125 C 5.175781 -0.269531 4.515625 0 3.609375 0 L 0.703125 0 Z M 0.703125 -6.875 "/>
</symbol>
<symbol overflow="visible" id="7cf6fc57-e82c-4f2f-9a25-e39b7da76c97">
<path style="stroke:none;" d="M 2.609375 -0.546875 C 3.171875 -0.546875 3.554688 -0.753906 3.765625 -1.171875 C 3.972656 -1.597656 4.078125 -2.070312 4.078125 -2.59375 C 4.078125 -3.0625 4 -3.441406 3.84375 -3.734375 C 3.601562 -4.191406 3.195312 -4.421875 2.625 -4.421875 C 2.101562 -4.421875 1.722656 -4.222656 1.484375 -3.828125 C 1.253906 -3.441406 1.140625 -2.96875 1.140625 -2.40625 C 1.140625 -1.875 1.253906 -1.429688 1.484375 -1.078125 C 1.722656 -0.722656 2.097656 -0.546875 2.609375 -0.546875 Z M 2.640625 -5.15625 C 3.285156 -5.15625 3.832031 -4.941406 4.28125 -4.515625 C 4.726562 -4.085938 4.953125 -3.453125 4.953125 -2.609375 C 4.953125 -1.804688 4.753906 -1.140625 4.359375 -0.609375 C 3.960938 -0.078125 3.351562 0.1875 2.53125 0.1875 C 1.84375 0.1875 1.296875 -0.046875 0.890625 -0.515625 C 0.484375 -0.984375 0.28125 -1.613281 0.28125 -2.40625 C 0.28125 -3.238281 0.492188 -3.90625 0.921875 -4.40625 C 1.347656 -4.90625 1.921875 -5.15625 2.640625 -5.15625 Z M 2.609375 -5.140625 Z M 2.609375 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="5fad5bf2-b61e-43d9-a589-8d2c0b2eb207">
<path style="stroke:none;" d="M 0.59375 -6.875 L 1.40625 -6.875 L 1.40625 -2.890625 L 3.578125 -5.015625 L 4.65625 -5.015625 L 2.734375 -3.140625 L 4.765625 0 L 3.6875 0 L 2.125 -2.53125 L 1.40625 -1.890625 L 1.40625 0 L 0.59375 0 Z M 0.59375 -6.875 "/>
</symbol>
<symbol overflow="visible" id="6288c52d-503a-4ad6-8249-f1e4230911dd">
<path style="stroke:none;" d="M 0.828125 -5.78125 C 0.835938 -6.132812 0.898438 -6.390625 1.015625 -6.546875 C 1.210938 -6.835938 1.59375 -6.984375 2.15625 -6.984375 C 2.207031 -6.984375 2.257812 -6.976562 2.3125 -6.96875 C 2.375 -6.96875 2.4375 -6.96875 2.5 -6.96875 L 2.5 -6.1875 C 2.414062 -6.195312 2.351562 -6.203125 2.3125 -6.203125 C 2.28125 -6.203125 2.242188 -6.203125 2.203125 -6.203125 C 1.953125 -6.203125 1.800781 -6.132812 1.75 -6 C 1.695312 -5.875 1.671875 -5.539062 1.671875 -5 L 2.5 -5 L 2.5 -4.328125 L 1.65625 -4.328125 L 1.65625 0 L 0.828125 0 L 0.828125 -4.328125 L 0.125 -4.328125 L 0.125 -5 L 0.828125 -5 Z M 0.828125 -5.78125 "/>
</symbol>
<symbol overflow="visible" id="19538bb7-81d0-4791-9867-734392c76cbf">
<path style="stroke:none;" d="M 0.703125 -6.875 L 2.046875 -6.875 L 4.015625 -1.0625 L 5.984375 -6.875 L 7.296875 -6.875 L 7.296875 0 L 6.421875 0 L 6.421875 -4.0625 C 6.421875 -4.195312 6.421875 -4.425781 6.421875 -4.75 C 6.429688 -5.082031 6.4375 -5.429688 6.4375 -5.796875 L 4.46875 0 L 3.546875 0 L 1.578125 -5.796875 L 1.578125 -5.59375 C 1.578125 -5.425781 1.578125 -5.171875 1.578125 -4.828125 C 1.585938 -4.484375 1.59375 -4.226562 1.59375 -4.0625 L 1.59375 0 L 0.703125 0 Z M 0.703125 -6.875 "/>
</symbol>
<symbol overflow="visible" id="83a3e4ab-24d0-4a1a-a17c-a56cc2320191">
<path style="stroke:none;" d="M 0.640625 -5.015625 L 1.4375 -5.015625 L 1.4375 -4.15625 C 1.507812 -4.320312 1.671875 -4.523438 1.921875 -4.765625 C 2.179688 -5.003906 2.476562 -5.125 2.8125 -5.125 C 2.820312 -5.125 2.847656 -5.125 2.890625 -5.125 C 2.929688 -5.125 2.992188 -5.117188 3.078125 -5.109375 L 3.078125 -4.21875 C 3.023438 -4.226562 2.976562 -4.234375 2.9375 -4.234375 C 2.894531 -4.234375 2.851562 -4.234375 2.8125 -4.234375 C 2.382812 -4.234375 2.054688 -4.097656 1.828125 -3.828125 C 1.597656 -3.554688 1.484375 -3.242188 1.484375 -2.890625 L 1.484375 0 L 0.640625 0 Z M 0.640625 -5.015625 "/>
</symbol>
<symbol overflow="visible" id="6484a8e0-84d2-4646-9c98-c4a42c63c4e2">
<path style="stroke:none;" d="M 0.625 -5.015625 L 1.453125 -5.015625 L 1.453125 -4.3125 C 1.648438 -4.550781 1.832031 -4.726562 2 -4.84375 C 2.269531 -5.03125 2.582031 -5.125 2.9375 -5.125 C 3.34375 -5.125 3.664062 -5.023438 3.90625 -4.828125 C 4.039062 -4.722656 4.164062 -4.5625 4.28125 -4.34375 C 4.46875 -4.601562 4.6875 -4.796875 4.9375 -4.921875 C 5.195312 -5.054688 5.484375 -5.125 5.796875 -5.125 C 6.472656 -5.125 6.929688 -4.882812 7.171875 -4.40625 C 7.304688 -4.132812 7.375 -3.78125 7.375 -3.34375 L 7.375 0 L 6.5 0 L 6.5 -3.484375 C 6.5 -3.816406 6.410156 -4.046875 6.234375 -4.171875 C 6.066406 -4.296875 5.863281 -4.359375 5.625 -4.359375 C 5.300781 -4.359375 5.019531 -4.25 4.78125 -4.03125 C 4.539062 -3.8125 4.421875 -3.441406 4.421875 -2.921875 L 4.421875 0 L 3.5625 0 L 3.5625 -3.28125 C 3.5625 -3.613281 3.519531 -3.859375 3.4375 -4.015625 C 3.3125 -4.253906 3.070312 -4.375 2.71875 -4.375 C 2.40625 -4.375 2.117188 -4.25 1.859375 -4 C 1.597656 -3.75 1.46875 -3.300781 1.46875 -2.65625 L 1.46875 0 L 0.625 0 Z M 0.625 -5.015625 "/>
</symbol>
<symbol overflow="visible" id="a21d095b-1ac0-40fa-bdae-94ecfc59b1cb">
<path style="stroke:none;" d="M 0.625 -5.015625 L 1.421875 -5.015625 L 1.421875 -4.3125 C 1.660156 -4.601562 1.910156 -4.8125 2.171875 -4.9375 C 2.441406 -5.0625 2.738281 -5.125 3.0625 -5.125 C 3.769531 -5.125 4.25 -4.878906 4.5 -4.390625 C 4.632812 -4.117188 4.703125 -3.726562 4.703125 -3.21875 L 4.703125 0 L 3.84375 0 L 3.84375 -3.15625 C 3.84375 -3.46875 3.800781 -3.71875 3.71875 -3.90625 C 3.5625 -4.21875 3.289062 -4.375 2.90625 -4.375 C 2.695312 -4.375 2.53125 -4.351562 2.40625 -4.3125 C 2.175781 -4.238281 1.972656 -4.097656 1.796875 -3.890625 C 1.660156 -3.734375 1.570312 -3.566406 1.53125 -3.390625 C 1.488281 -3.210938 1.46875 -2.957031 1.46875 -2.625 L 1.46875 0 L 0.625 0 Z M 2.59375 -5.140625 Z M 2.59375 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="b18e76e1-6fd0-4ebc-9265-35d35ac02aef">
<path style="stroke:none;" d="M 3.375 -0.796875 C 3.6875 -0.796875 3.945312 -0.828125 4.15625 -0.890625 C 4.507812 -1.015625 4.804688 -1.25 5.046875 -1.59375 C 5.222656 -1.875 5.351562 -2.234375 5.4375 -2.671875 C 5.488281 -2.921875 5.515625 -3.160156 5.515625 -3.390625 C 5.515625 -4.242188 5.34375 -4.90625 5 -5.375 C 4.664062 -5.84375 4.117188 -6.078125 3.359375 -6.078125 L 1.703125 -6.078125 L 1.703125 -0.796875 Z M 0.765625 -6.875 L 3.5625 -6.875 C 4.507812 -6.875 5.242188 -6.539062 5.765625 -5.875 C 6.222656 -5.269531 6.453125 -4.492188 6.453125 -3.546875 C 6.453125 -2.816406 6.316406 -2.15625 6.046875 -1.5625 C 5.566406 -0.519531 4.734375 0 3.546875 0 L 0.765625 0 Z M 0.765625 -6.875 "/>
</symbol>
<symbol overflow="visible" id="55057c74-a3c0-4b7e-877a-53e79f85f2ca">
<path style="stroke:none;" d="M 1.265625 -1.328125 C 1.265625 -1.085938 1.351562 -0.894531 1.53125 -0.75 C 1.707031 -0.613281 1.921875 -0.546875 2.171875 -0.546875 C 2.460938 -0.546875 2.75 -0.613281 3.03125 -0.75 C 3.5 -0.976562 3.734375 -1.351562 3.734375 -1.875 L 3.734375 -2.546875 C 3.628906 -2.484375 3.492188 -2.429688 3.328125 -2.390625 C 3.171875 -2.347656 3.015625 -2.316406 2.859375 -2.296875 L 2.34375 -2.234375 C 2.039062 -2.191406 1.8125 -2.125 1.65625 -2.03125 C 1.394531 -1.882812 1.265625 -1.648438 1.265625 -1.328125 Z M 3.3125 -3.046875 C 3.5 -3.066406 3.628906 -3.144531 3.703125 -3.28125 C 3.734375 -3.351562 3.75 -3.460938 3.75 -3.609375 C 3.75 -3.890625 3.644531 -4.09375 3.4375 -4.21875 C 3.238281 -4.351562 2.945312 -4.421875 2.5625 -4.421875 C 2.125 -4.421875 1.8125 -4.304688 1.625 -4.078125 C 1.519531 -3.941406 1.453125 -3.742188 1.421875 -3.484375 L 0.640625 -3.484375 C 0.660156 -4.097656 0.863281 -4.523438 1.25 -4.765625 C 1.632812 -5.015625 2.078125 -5.140625 2.578125 -5.140625 C 3.171875 -5.140625 3.65625 -5.023438 4.03125 -4.796875 C 4.394531 -4.578125 4.578125 -4.226562 4.578125 -3.75 L 4.578125 -0.859375 C 4.578125 -0.773438 4.59375 -0.707031 4.625 -0.65625 C 4.664062 -0.601562 4.742188 -0.578125 4.859375 -0.578125 C 4.890625 -0.578125 4.925781 -0.578125 4.96875 -0.578125 C 5.019531 -0.578125 5.070312 -0.582031 5.125 -0.59375 L 5.125 0.015625 C 5 0.0546875 4.898438 0.0820312 4.828125 0.09375 C 4.765625 0.101562 4.671875 0.109375 4.546875 0.109375 C 4.253906 0.109375 4.046875 0.00390625 3.921875 -0.203125 C 3.847656 -0.304688 3.796875 -0.460938 3.765625 -0.671875 C 3.597656 -0.441406 3.351562 -0.242188 3.03125 -0.078125 C 2.707031 0.0859375 2.351562 0.171875 1.96875 0.171875 C 1.5 0.171875 1.117188 0.03125 0.828125 -0.25 C 0.535156 -0.53125 0.390625 -0.882812 0.390625 -1.3125 C 0.390625 -1.78125 0.53125 -2.140625 0.8125 -2.390625 C 1.101562 -2.648438 1.488281 -2.8125 1.96875 -2.875 Z M 2.609375 -5.140625 Z M 2.609375 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="c7e8f52d-1df3-48c9-8a70-113c539f55ea">
<path style="stroke:none;" d="M 2.734375 -0.5625 C 3.128906 -0.5625 3.457031 -0.726562 3.71875 -1.0625 C 3.976562 -1.394531 4.109375 -1.882812 4.109375 -2.53125 C 4.109375 -2.9375 4.050781 -3.28125 3.9375 -3.5625 C 3.71875 -4.125 3.316406 -4.40625 2.734375 -4.40625 C 2.148438 -4.40625 1.75 -4.109375 1.53125 -3.515625 C 1.414062 -3.203125 1.359375 -2.804688 1.359375 -2.328125 C 1.359375 -1.941406 1.414062 -1.613281 1.53125 -1.34375 C 1.75 -0.820312 2.148438 -0.5625 2.734375 -0.5625 Z M 0.546875 -5 L 1.375 -5 L 1.375 -4.328125 C 1.539062 -4.554688 1.722656 -4.734375 1.921875 -4.859375 C 2.210938 -5.046875 2.546875 -5.140625 2.921875 -5.140625 C 3.492188 -5.140625 3.976562 -4.921875 4.375 -4.484375 C 4.769531 -4.046875 4.96875 -3.425781 4.96875 -2.625 C 4.96875 -1.53125 4.679688 -0.75 4.109375 -0.28125 C 3.742188 0.0195312 3.320312 0.171875 2.84375 0.171875 C 2.46875 0.171875 2.148438 0.0859375 1.890625 -0.078125 C 1.742188 -0.171875 1.578125 -0.332031 1.390625 -0.5625 L 1.390625 2 L 0.546875 2 Z M 0.546875 -5 "/>
</symbol>
<symbol overflow="visible" id="7353f8fb-4e9f-4f78-8a04-634ea64cccfc">
<path style="stroke:none;" d="M 1.15625 -2.453125 C 1.15625 -1.910156 1.269531 -1.457031 1.5 -1.09375 C 1.726562 -0.738281 2.09375 -0.5625 2.59375 -0.5625 C 2.976562 -0.5625 3.296875 -0.726562 3.546875 -1.0625 C 3.804688 -1.394531 3.9375 -1.875 3.9375 -2.5 C 3.9375 -3.132812 3.804688 -3.601562 3.546875 -3.90625 C 3.285156 -4.21875 2.960938 -4.375 2.578125 -4.375 C 2.148438 -4.375 1.804688 -4.207031 1.546875 -3.875 C 1.285156 -3.550781 1.15625 -3.078125 1.15625 -2.453125 Z M 2.421875 -5.109375 C 2.804688 -5.109375 3.128906 -5.023438 3.390625 -4.859375 C 3.535156 -4.765625 3.703125 -4.601562 3.890625 -4.375 L 3.890625 -6.90625 L 4.703125 -6.90625 L 4.703125 0 L 3.953125 0 L 3.953125 -0.703125 C 3.753906 -0.390625 3.519531 -0.164062 3.25 -0.03125 C 2.976562 0.101562 2.671875 0.171875 2.328125 0.171875 C 1.765625 0.171875 1.28125 -0.0625 0.875 -0.53125 C 0.46875 -1 0.265625 -1.625 0.265625 -2.40625 C 0.265625 -3.132812 0.445312 -3.765625 0.8125 -4.296875 C 1.1875 -4.835938 1.722656 -5.109375 2.421875 -5.109375 Z M 2.421875 -5.109375 "/>
</symbol>
<symbol overflow="visible" id="1fcf57b7-5792-4872-9ed3-df5115617b53">
<path style="stroke:none;" d="M 0.75 -6.875 L 1.703125 -6.875 L 1.703125 -4.03125 L 5.28125 -4.03125 L 5.28125 -6.875 L 6.21875 -6.875 L 6.21875 0 L 5.28125 0 L 5.28125 -3.21875 L 1.703125 -3.21875 L 1.703125 0 L 0.75 0 Z M 0.75 -6.875 "/>
</symbol>
<symbol overflow="visible" id="f5ce2708-213d-46a5-a040-2eea16cdd56b">
<path style="stroke:none;" d="M 0.640625 -6.875 L 1.484375 -6.875 L 1.484375 0 L 0.640625 0 Z M 0.640625 -6.875 "/>
</symbol>
<symbol overflow="visible" id="e1c9905d-912a-4e22-89d8-77c01f61b0ce">
<path style="stroke:none;" d="M 3.75 -5.015625 L 4.6875 -5.015625 C 4.5625 -4.691406 4.296875 -3.957031 3.890625 -2.8125 C 3.585938 -1.945312 3.332031 -1.242188 3.125 -0.703125 C 2.632812 0.578125 2.289062 1.359375 2.09375 1.640625 C 1.894531 1.921875 1.550781 2.0625 1.0625 2.0625 C 0.945312 2.0625 0.851562 2.054688 0.78125 2.046875 C 0.71875 2.035156 0.640625 2.015625 0.546875 1.984375 L 0.546875 1.21875 C 0.691406 1.257812 0.796875 1.285156 0.859375 1.296875 C 0.929688 1.304688 0.992188 1.3125 1.046875 1.3125 C 1.203125 1.3125 1.316406 1.285156 1.390625 1.234375 C 1.460938 1.179688 1.523438 1.117188 1.578125 1.046875 C 1.585938 1.015625 1.640625 0.882812 1.734375 0.65625 C 1.835938 0.425781 1.910156 0.253906 1.953125 0.140625 L 0.09375 -5.015625 L 1.046875 -5.015625 L 2.40625 -0.9375 Z M 2.390625 -5.140625 Z M 2.390625 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="eb7e45a8-7452-4c2e-92e0-02ab9a8ee723">
<path style="stroke:none;" d="M 0.625 -5 L 1.46875 -5 L 1.46875 0 L 0.625 0 Z M 0.625 -6.875 L 1.46875 -6.875 L 1.46875 -5.921875 L 0.625 -5.921875 Z M 0.625 -6.875 "/>
</symbol>
<symbol overflow="visible" id="abe8160a-1919-4d75-b0f5-32065de8ee3d">
<path style="stroke:none;" d="M 0.546875 -6.90625 L 1.375 -6.90625 L 1.375 -4.40625 C 1.5625 -4.644531 1.78125 -4.828125 2.03125 -4.953125 C 2.289062 -5.078125 2.566406 -5.140625 2.859375 -5.140625 C 3.484375 -5.140625 3.988281 -4.925781 4.375 -4.5 C 4.769531 -4.070312 4.96875 -3.441406 4.96875 -2.609375 C 4.96875 -1.816406 4.773438 -1.15625 4.390625 -0.625 C 4.003906 -0.101562 3.472656 0.15625 2.796875 0.15625 C 2.410156 0.15625 2.085938 0.0664062 1.828125 -0.109375 C 1.671875 -0.222656 1.503906 -0.398438 1.328125 -0.640625 L 1.328125 0 L 0.546875 0 Z M 2.75 -0.578125 C 3.195312 -0.578125 3.535156 -0.757812 3.765625 -1.125 C 3.992188 -1.488281 4.109375 -1.96875 4.109375 -2.5625 C 4.109375 -3.09375 3.992188 -3.53125 3.765625 -3.875 C 3.535156 -4.21875 3.203125 -4.390625 2.765625 -4.390625 C 2.378906 -4.390625 2.039062 -4.25 1.75 -3.96875 C 1.46875 -3.6875 1.328125 -3.21875 1.328125 -2.5625 C 1.328125 -2.09375 1.382812 -1.710938 1.5 -1.421875 C 1.726562 -0.859375 2.144531 -0.578125 2.75 -0.578125 Z M 2.75 -0.578125 "/>
</symbol>
<symbol overflow="visible" id="1792dfa6-17a0-4c27-b6de-545e7b48e905">
<path style="stroke:none;" d="M 4.109375 -2.046875 C 4.109375 -1.472656 4.019531 -1.023438 3.84375 -0.703125 C 3.53125 -0.109375 2.925781 0.1875 2.03125 0.1875 C 1.519531 0.1875 1.078125 0.046875 0.703125 -0.234375 C 0.335938 -0.515625 0.15625 -1.015625 0.15625 -1.734375 L 0.15625 -2.21875 L 1.046875 -2.21875 L 1.046875 -1.734375 C 1.046875 -1.359375 1.128906 -1.070312 1.296875 -0.875 C 1.460938 -0.6875 1.722656 -0.59375 2.078125 -0.59375 C 2.566406 -0.59375 2.890625 -0.765625 3.046875 -1.109375 C 3.140625 -1.316406 3.1875 -1.710938 3.1875 -2.296875 L 3.1875 -6.875 L 4.109375 -6.875 Z M 4.109375 -2.046875 "/>
</symbol>
<symbol overflow="visible" id="1851c680-3a62-4ed4-9965-20223a18a155">
<path style="stroke:none;" d="M 1 -5.015625 L 1.96875 -1.0625 L 2.953125 -5.015625 L 3.890625 -5.015625 L 4.875 -1.09375 L 5.90625 -5.015625 L 6.75 -5.015625 L 5.296875 0 L 4.421875 0 L 3.390625 -3.890625 L 2.40625 0 L 1.53125 0 L 0.078125 -5.015625 Z M 1 -5.015625 "/>
</symbol>
<symbol overflow="visible" id="6fc71a2d-be20-4a59-a6ff-fc2e8ad8e748">
<path style="stroke:none;" d="M 1.125 -1.578125 C 1.144531 -1.296875 1.210938 -1.078125 1.328125 -0.921875 C 1.546875 -0.648438 1.914062 -0.515625 2.4375 -0.515625 C 2.75 -0.515625 3.019531 -0.582031 3.25 -0.71875 C 3.488281 -0.851562 3.609375 -1.066406 3.609375 -1.359375 C 3.609375 -1.566406 3.515625 -1.726562 3.328125 -1.84375 C 3.203125 -1.914062 2.960938 -1.992188 2.609375 -2.078125 L 1.9375 -2.25 C 1.507812 -2.351562 1.195312 -2.472656 1 -2.609375 C 0.632812 -2.835938 0.453125 -3.15625 0.453125 -3.5625 C 0.453125 -4.03125 0.617188 -4.410156 0.953125 -4.703125 C 1.296875 -4.992188 1.757812 -5.140625 2.34375 -5.140625 C 3.09375 -5.140625 3.640625 -4.921875 3.984375 -4.484375 C 4.191406 -4.203125 4.289062 -3.898438 4.28125 -3.578125 L 3.484375 -3.578125 C 3.472656 -3.765625 3.40625 -3.9375 3.28125 -4.09375 C 3.09375 -4.3125 2.757812 -4.421875 2.28125 -4.421875 C 1.957031 -4.421875 1.710938 -4.359375 1.546875 -4.234375 C 1.390625 -4.117188 1.3125 -3.960938 1.3125 -3.765625 C 1.3125 -3.546875 1.414062 -3.367188 1.625 -3.234375 C 1.75 -3.160156 1.9375 -3.09375 2.1875 -3.03125 L 2.734375 -2.890625 C 3.347656 -2.742188 3.753906 -2.601562 3.953125 -2.46875 C 4.285156 -2.25 4.453125 -1.910156 4.453125 -1.453125 C 4.453125 -1.003906 4.28125 -0.617188 3.9375 -0.296875 C 3.601562 0.0234375 3.085938 0.1875 2.390625 0.1875 C 1.648438 0.1875 1.125 0.0195312 0.8125 -0.3125 C 0.5 -0.65625 0.332031 -1.078125 0.3125 -1.578125 Z M 2.359375 -5.140625 Z M 2.359375 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="73c6d84e-53b2-4866-a787-2703090c3e72">
<path style="stroke:none;" d="M 1.34375 -2.21875 C 1.363281 -1.832031 1.453125 -1.515625 1.609375 -1.265625 C 1.921875 -0.804688 2.46875 -0.578125 3.25 -0.578125 C 3.601562 -0.578125 3.921875 -0.628906 4.203125 -0.734375 C 4.765625 -0.929688 5.046875 -1.28125 5.046875 -1.78125 C 5.046875 -2.15625 4.925781 -2.421875 4.6875 -2.578125 C 4.445312 -2.734375 4.078125 -2.867188 3.578125 -2.984375 L 2.640625 -3.1875 C 2.035156 -3.332031 1.601562 -3.488281 1.34375 -3.65625 C 0.90625 -3.9375 0.6875 -4.363281 0.6875 -4.9375 C 0.6875 -5.550781 0.898438 -6.054688 1.328125 -6.453125 C 1.765625 -6.859375 2.375 -7.0625 3.15625 -7.0625 C 3.875 -7.0625 4.484375 -6.882812 4.984375 -6.53125 C 5.492188 -6.1875 5.75 -5.628906 5.75 -4.859375 L 4.875 -4.859375 C 4.820312 -5.234375 4.722656 -5.515625 4.578125 -5.703125 C 4.285156 -6.066406 3.800781 -6.25 3.125 -6.25 C 2.570312 -6.25 2.175781 -6.132812 1.9375 -5.90625 C 1.695312 -5.675781 1.578125 -5.40625 1.578125 -5.09375 C 1.578125 -4.757812 1.71875 -4.515625 2 -4.359375 C 2.1875 -4.253906 2.601562 -4.128906 3.25 -3.984375 L 4.21875 -3.765625 C 4.675781 -3.660156 5.035156 -3.515625 5.296875 -3.328125 C 5.734375 -3.003906 5.953125 -2.535156 5.953125 -1.921875 C 5.953125 -1.160156 5.671875 -0.613281 5.109375 -0.28125 C 4.554688 0.0390625 3.914062 0.203125 3.1875 0.203125 C 2.332031 0.203125 1.660156 -0.015625 1.171875 -0.453125 C 0.691406 -0.890625 0.457031 -1.476562 0.46875 -2.21875 Z M 3.21875 -7.0625 Z M 3.21875 -7.0625 "/>
</symbol>
<symbol overflow="visible" id="71cbe45c-e2b4-4fc6-8717-e4b8940c9cae">
<path style="stroke:none;" d="M 2.546875 -5.15625 C 3.117188 -5.15625 3.582031 -5.019531 3.9375 -4.75 C 4.289062 -4.476562 4.503906 -4.003906 4.578125 -3.328125 L 3.75 -3.328125 C 3.695312 -3.640625 3.582031 -3.894531 3.40625 -4.09375 C 3.226562 -4.300781 2.941406 -4.40625 2.546875 -4.40625 C 2.015625 -4.40625 1.632812 -4.144531 1.40625 -3.625 C 1.25 -3.28125 1.171875 -2.859375 1.171875 -2.359375 C 1.171875 -1.859375 1.273438 -1.4375 1.484375 -1.09375 C 1.703125 -0.75 2.039062 -0.578125 2.5 -0.578125 C 2.84375 -0.578125 3.117188 -0.679688 3.328125 -0.890625 C 3.535156 -1.109375 3.675781 -1.40625 3.75 -1.78125 L 4.578125 -1.78125 C 4.484375 -1.113281 4.25 -0.625 3.875 -0.3125 C 3.5 -0.0078125 3.019531 0.140625 2.4375 0.140625 C 1.78125 0.140625 1.253906 -0.0976562 0.859375 -0.578125 C 0.472656 -1.054688 0.28125 -1.65625 0.28125 -2.375 C 0.28125 -3.25 0.492188 -3.929688 0.921875 -4.421875 C 1.347656 -4.910156 1.890625 -5.15625 2.546875 -5.15625 Z M 2.421875 -5.140625 Z M 2.421875 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="efa93de8-ea89-458f-be2d-eaf720aea985">
<path style="stroke:none;" d="M 0.78125 -6.421875 L 1.640625 -6.421875 L 1.640625 -5.015625 L 2.4375 -5.015625 L 2.4375 -4.328125 L 1.640625 -4.328125 L 1.640625 -1.046875 C 1.640625 -0.878906 1.695312 -0.765625 1.8125 -0.703125 C 1.882812 -0.671875 1.992188 -0.65625 2.140625 -0.65625 C 2.179688 -0.65625 2.222656 -0.65625 2.265625 -0.65625 C 2.316406 -0.65625 2.375 -0.660156 2.4375 -0.671875 L 2.4375 0 C 2.34375 0.03125 2.242188 0.0507812 2.140625 0.0625 C 2.035156 0.0703125 1.921875 0.078125 1.796875 0.078125 C 1.398438 0.078125 1.128906 -0.0195312 0.984375 -0.21875 C 0.847656 -0.425781 0.78125 -0.6875 0.78125 -1 L 0.78125 -4.328125 L 0.109375 -4.328125 L 0.109375 -5.015625 L 0.78125 -5.015625 Z M 0.78125 -6.421875 "/>
</symbol>
<symbol overflow="visible" id="72b4a8cb-c01e-4a43-90c4-4382a6a86ac5">
<path style="stroke:none;" d="M 1.46875 -5.015625 L 1.46875 -1.6875 C 1.46875 -1.425781 1.503906 -1.21875 1.578125 -1.0625 C 1.734375 -0.757812 2.015625 -0.609375 2.421875 -0.609375 C 3.003906 -0.609375 3.40625 -0.867188 3.625 -1.390625 C 3.738281 -1.671875 3.796875 -2.054688 3.796875 -2.546875 L 3.796875 -5.015625 L 4.640625 -5.015625 L 4.640625 0 L 3.84375 0 L 3.84375 -0.734375 C 3.738281 -0.546875 3.601562 -0.382812 3.4375 -0.25 C 3.113281 0.0078125 2.722656 0.140625 2.265625 0.140625 C 1.554688 0.140625 1.070312 -0.0976562 0.8125 -0.578125 C 0.664062 -0.835938 0.59375 -1.179688 0.59375 -1.609375 L 0.59375 -5.015625 Z M 2.625 -5.140625 Z M 2.625 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="8e559626-3c66-4af9-8519-a12d02b3a72d">
<path style="stroke:none;" d="M 0.734375 -6.875 L 1.640625 -6.875 L 1.640625 -3.53125 L 5 -6.875 L 6.28125 -6.875 L 3.421875 -4.109375 L 6.359375 0 L 5.140625 0 L 2.734375 -3.453125 L 1.640625 -2.40625 L 1.640625 0 L 0.734375 0 Z M 0.734375 -6.875 "/>
</symbol>
<symbol overflow="visible" id="6ca87a35-a9c4-4e6d-b217-24747592d47b">
<path style="stroke:none;" d="M 1.28125 -6.875 L 3.25 -1.015625 L 5.203125 -6.875 L 6.25 -6.875 L 3.734375 0 L 2.75 0 L 0.25 -6.875 Z M 1.28125 -6.875 "/>
</symbol>
<symbol overflow="visible" id="6a07bcc2-6d15-42e2-a912-c439f123a0c3">
<path style="stroke:none;" d="M 2.59375 -6.703125 C 3.457031 -6.703125 4.085938 -6.347656 4.484375 -5.640625 C 4.773438 -5.085938 4.921875 -4.328125 4.921875 -3.359375 C 4.921875 -2.453125 4.785156 -1.695312 4.515625 -1.09375 C 4.128906 -0.238281 3.488281 0.1875 2.59375 0.1875 C 1.78125 0.1875 1.179688 -0.160156 0.796875 -0.859375 C 0.460938 -1.453125 0.296875 -2.238281 0.296875 -3.21875 C 0.296875 -3.976562 0.394531 -4.632812 0.59375 -5.1875 C 0.957031 -6.195312 1.625 -6.703125 2.59375 -6.703125 Z M 2.578125 -0.578125 C 3.015625 -0.578125 3.363281 -0.769531 3.625 -1.15625 C 3.882812 -1.550781 4.015625 -2.273438 4.015625 -3.328125 C 4.015625 -4.085938 3.921875 -4.710938 3.734375 -5.203125 C 3.546875 -5.703125 3.179688 -5.953125 2.640625 -5.953125 C 2.148438 -5.953125 1.789062 -5.71875 1.5625 -5.25 C 1.332031 -4.78125 1.21875 -4.09375 1.21875 -3.1875 C 1.21875 -2.5 1.289062 -1.945312 1.4375 -1.53125 C 1.65625 -0.894531 2.035156 -0.578125 2.578125 -0.578125 Z M 2.578125 -0.578125 "/>
</symbol>
<symbol overflow="visible" id="a4385ed1-1006-4b85-ba44-08fda2942e48">
<path style="stroke:none;" d="M 0.921875 -4.75 L 0.921875 -5.390625 C 1.523438 -5.453125 1.945312 -5.550781 2.1875 -5.6875 C 2.425781 -5.832031 2.609375 -6.164062 2.734375 -6.6875 L 3.390625 -6.6875 L 3.390625 0 L 2.5 0 L 2.5 -4.75 Z M 0.921875 -4.75 "/>
</symbol>
<symbol overflow="visible" id="21e751a0-55ec-4cee-9bd5-97d46c6344fa">
<path style="stroke:none;" d="M 0.296875 0 C 0.328125 -0.570312 0.445312 -1.070312 0.65625 -1.5 C 0.863281 -1.9375 1.269531 -2.328125 1.875 -2.671875 L 2.765625 -3.1875 C 3.171875 -3.425781 3.457031 -3.628906 3.625 -3.796875 C 3.875 -4.054688 4 -4.351562 4 -4.6875 C 4 -5.070312 3.878906 -5.378906 3.640625 -5.609375 C 3.410156 -5.835938 3.101562 -5.953125 2.71875 -5.953125 C 2.132812 -5.953125 1.734375 -5.734375 1.515625 -5.296875 C 1.398438 -5.066406 1.335938 -4.742188 1.328125 -4.328125 L 0.46875 -4.328125 C 0.476562 -4.910156 0.582031 -5.382812 0.78125 -5.75 C 1.144531 -6.40625 1.789062 -6.734375 2.71875 -6.734375 C 3.488281 -6.734375 4.050781 -6.523438 4.40625 -6.109375 C 4.757812 -5.691406 4.9375 -5.226562 4.9375 -4.71875 C 4.9375 -4.1875 4.75 -3.726562 4.375 -3.34375 C 4.15625 -3.125 3.757812 -2.851562 3.1875 -2.53125 L 2.546875 -2.1875 C 2.242188 -2.019531 2.003906 -1.859375 1.828125 -1.703125 C 1.515625 -1.429688 1.316406 -1.128906 1.234375 -0.796875 L 4.90625 -0.796875 L 4.90625 0 Z M 0.296875 0 "/>
</symbol>
<symbol overflow="visible" id="c148a3ce-e7e7-4c9d-a2ca-fd60a9ec6339">
<path style="stroke:none;" d="M 2.484375 0.1875 C 1.691406 0.1875 1.117188 -0.03125 0.765625 -0.46875 C 0.410156 -0.90625 0.234375 -1.4375 0.234375 -2.0625 L 1.109375 -2.0625 C 1.148438 -1.625 1.234375 -1.304688 1.359375 -1.109375 C 1.578125 -0.753906 1.96875 -0.578125 2.53125 -0.578125 C 2.976562 -0.578125 3.335938 -0.695312 3.609375 -0.9375 C 3.878906 -1.175781 4.015625 -1.484375 4.015625 -1.859375 C 4.015625 -2.316406 3.867188 -2.640625 3.578125 -2.828125 C 3.296875 -3.015625 2.90625 -3.109375 2.40625 -3.109375 C 2.351562 -3.109375 2.296875 -3.101562 2.234375 -3.09375 C 2.179688 -3.09375 2.125 -3.09375 2.0625 -3.09375 L 2.0625 -3.84375 C 2.144531 -3.832031 2.21875 -3.820312 2.28125 -3.8125 C 2.34375 -3.8125 2.40625 -3.8125 2.46875 -3.8125 C 2.789062 -3.8125 3.050781 -3.863281 3.25 -3.96875 C 3.601562 -4.144531 3.78125 -4.457031 3.78125 -4.90625 C 3.78125 -5.238281 3.660156 -5.492188 3.421875 -5.671875 C 3.191406 -5.859375 2.914062 -5.953125 2.59375 -5.953125 C 2.03125 -5.953125 1.644531 -5.765625 1.4375 -5.390625 C 1.3125 -5.179688 1.242188 -4.882812 1.234375 -4.5 L 0.390625 -4.5 C 0.390625 -5 0.492188 -5.425781 0.703125 -5.78125 C 1.046875 -6.40625 1.648438 -6.71875 2.515625 -6.71875 C 3.191406 -6.71875 3.71875 -6.5625 4.09375 -6.25 C 4.46875 -5.945312 4.65625 -5.507812 4.65625 -4.9375 C 4.65625 -4.519531 4.546875 -4.1875 4.328125 -3.9375 C 4.191406 -3.78125 4.015625 -3.65625 3.796875 -3.5625 C 4.148438 -3.46875 4.425781 -3.28125 4.625 -3 C 4.820312 -2.71875 4.921875 -2.378906 4.921875 -1.984375 C 4.921875 -1.347656 4.707031 -0.828125 4.28125 -0.421875 C 3.863281 -0.015625 3.265625 0.1875 2.484375 0.1875 Z M 2.484375 0.1875 "/>
</symbol>
<symbol overflow="visible" id="afdc6c34-4cb2-4b98-a307-5d600d65d568">
<path style="stroke:none;" d="M 0.46875 0 L 0.46875 -10.328125 L 8.671875 -10.328125 L 8.671875 0 Z M 7.375 -1.296875 L 7.375 -9.046875 L 1.765625 -9.046875 L 1.765625 -1.296875 Z M 7.375 -1.296875 "/>
</symbol>
<symbol overflow="visible" id="d9b8efb5-1fb2-450a-a183-d3c88d144fd5">
<path style="stroke:none;" d="M 2.015625 -3.328125 C 2.046875 -2.742188 2.179688 -2.269531 2.421875 -1.90625 C 2.890625 -1.21875 3.707031 -0.875 4.875 -0.875 C 5.40625 -0.875 5.882812 -0.953125 6.3125 -1.109375 C 7.144531 -1.398438 7.5625 -1.921875 7.5625 -2.671875 C 7.5625 -3.234375 7.390625 -3.632812 7.046875 -3.875 C 6.679688 -4.101562 6.117188 -4.304688 5.359375 -4.484375 L 3.96875 -4.796875 C 3.050781 -5.003906 2.40625 -5.234375 2.03125 -5.484375 C 1.375 -5.910156 1.046875 -6.554688 1.046875 -7.421875 C 1.046875 -8.347656 1.363281 -9.109375 2 -9.703125 C 2.644531 -10.296875 3.554688 -10.59375 4.734375 -10.59375 C 5.816406 -10.59375 6.734375 -10.332031 7.484375 -9.8125 C 8.242188 -9.289062 8.625 -8.453125 8.625 -7.296875 L 7.3125 -7.296875 C 7.238281 -7.847656 7.085938 -8.273438 6.859375 -8.578125 C 6.429688 -9.117188 5.707031 -9.390625 4.6875 -9.390625 C 3.863281 -9.390625 3.269531 -9.210938 2.90625 -8.859375 C 2.550781 -8.515625 2.375 -8.113281 2.375 -7.65625 C 2.375 -7.144531 2.582031 -6.773438 3 -6.546875 C 3.28125 -6.390625 3.90625 -6.203125 4.875 -5.984375 L 6.328125 -5.65625 C 7.023438 -5.488281 7.566406 -5.269531 7.953125 -5 C 8.609375 -4.507812 8.9375 -3.804688 8.9375 -2.890625 C 8.9375 -1.742188 8.519531 -0.925781 7.6875 -0.4375 C 6.851562 0.0507812 5.882812 0.296875 4.78125 0.296875 C 3.5 0.296875 2.492188 -0.03125 1.765625 -0.6875 C 1.035156 -1.332031 0.679688 -2.210938 0.703125 -3.328125 Z M 4.84375 -10.609375 Z M 4.84375 -10.609375 "/>
</symbol>
<symbol overflow="visible" id="f1f5f504-1458-4808-8e68-f3fb5929801d">
<path style="stroke:none;" d="M 4.0625 -7.703125 C 4.601562 -7.703125 5.125 -7.578125 5.625 -7.328125 C 6.125 -7.078125 6.503906 -6.753906 6.765625 -6.359375 C 7.015625 -5.972656 7.1875 -5.523438 7.28125 -5.015625 C 7.351562 -4.671875 7.390625 -4.117188 7.390625 -3.359375 L 1.859375 -3.359375 C 1.890625 -2.597656 2.070312 -1.984375 2.40625 -1.515625 C 2.738281 -1.054688 3.257812 -0.828125 3.96875 -0.828125 C 4.632812 -0.828125 5.164062 -1.046875 5.5625 -1.484375 C 5.78125 -1.734375 5.9375 -2.023438 6.03125 -2.359375 L 7.28125 -2.359375 C 7.25 -2.085938 7.140625 -1.78125 6.953125 -1.4375 C 6.765625 -1.09375 6.554688 -0.816406 6.328125 -0.609375 C 5.941406 -0.234375 5.46875 0.0195312 4.90625 0.15625 C 4.601562 0.226562 4.257812 0.265625 3.875 0.265625 C 2.9375 0.265625 2.140625 -0.0703125 1.484375 -0.75 C 0.828125 -1.4375 0.5 -2.394531 0.5 -3.625 C 0.5 -4.832031 0.828125 -5.8125 1.484375 -6.5625 C 2.140625 -7.320312 3 -7.703125 4.0625 -7.703125 Z M 6.078125 -4.375 C 6.023438 -4.914062 5.90625 -5.351562 5.71875 -5.6875 C 5.375 -6.289062 4.796875 -6.59375 3.984375 -6.59375 C 3.398438 -6.59375 2.910156 -6.382812 2.515625 -5.96875 C 2.128906 -5.550781 1.925781 -5.019531 1.90625 -4.375 Z M 3.953125 -7.71875 Z M 3.953125 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="4e72fb75-1d73-438c-b822-0202d43b9c10">
<path style="stroke:none;" d="M 0.921875 -7.53125 L 2.125 -7.53125 L 2.125 -6.46875 C 2.488281 -6.90625 2.867188 -7.21875 3.265625 -7.40625 C 3.660156 -7.601562 4.101562 -7.703125 4.59375 -7.703125 C 5.664062 -7.703125 6.390625 -7.328125 6.765625 -6.578125 C 6.960938 -6.171875 7.0625 -5.585938 7.0625 -4.828125 L 7.0625 0 L 5.78125 0 L 5.78125 -4.75 C 5.78125 -5.207031 5.710938 -5.578125 5.578125 -5.859375 C 5.347656 -6.328125 4.941406 -6.5625 4.359375 -6.5625 C 4.054688 -6.5625 3.804688 -6.53125 3.609375 -6.46875 C 3.265625 -6.363281 2.960938 -6.160156 2.703125 -5.859375 C 2.492188 -5.609375 2.351562 -5.347656 2.28125 -5.078125 C 2.21875 -4.816406 2.1875 -4.441406 2.1875 -3.953125 L 2.1875 0 L 0.921875 0 Z M 3.90625 -7.71875 Z M 3.90625 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="f8c53319-2793-42be-8a06-33b36ee69f00">
<path style="stroke:none;" d="M 1.1875 -9.640625 L 2.46875 -9.640625 L 2.46875 -7.53125 L 3.671875 -7.53125 L 3.671875 -6.5 L 2.46875 -6.5 L 2.46875 -1.578125 C 2.46875 -1.316406 2.554688 -1.144531 2.734375 -1.0625 C 2.828125 -1.007812 2.988281 -0.984375 3.21875 -0.984375 C 3.28125 -0.984375 3.347656 -0.984375 3.421875 -0.984375 C 3.492188 -0.984375 3.578125 -0.988281 3.671875 -1 L 3.671875 0 C 3.523438 0.0390625 3.367188 0.0703125 3.203125 0.09375 C 3.046875 0.113281 2.878906 0.125 2.703125 0.125 C 2.109375 0.125 1.707031 -0.0234375 1.5 -0.328125 C 1.289062 -0.628906 1.1875 -1.023438 1.1875 -1.515625 L 1.1875 -6.5 L 0.15625 -6.5 L 0.15625 -7.53125 L 1.1875 -7.53125 Z M 1.1875 -9.640625 "/>
</symbol>
<symbol overflow="visible" id="9269f457-2621-4361-8a0e-df6fea0d7687">
<path style="stroke:none;" d="M 3.828125 -7.75 C 4.679688 -7.75 5.375 -7.539062 5.90625 -7.125 C 6.4375 -6.71875 6.753906 -6.007812 6.859375 -5 L 5.640625 -5 C 5.554688 -5.46875 5.378906 -5.851562 5.109375 -6.15625 C 4.847656 -6.46875 4.421875 -6.625 3.828125 -6.625 C 3.023438 -6.625 2.453125 -6.226562 2.109375 -5.4375 C 1.878906 -4.925781 1.765625 -4.296875 1.765625 -3.546875 C 1.765625 -2.785156 1.921875 -2.144531 2.234375 -1.625 C 2.554688 -1.113281 3.0625 -0.859375 3.75 -0.859375 C 4.269531 -0.859375 4.679688 -1.019531 4.984375 -1.34375 C 5.296875 -1.664062 5.515625 -2.109375 5.640625 -2.671875 L 6.859375 -2.671875 C 6.722656 -1.671875 6.375 -0.9375 5.8125 -0.46875 C 5.25 -0.0078125 4.53125 0.21875 3.65625 0.21875 C 2.664062 0.21875 1.878906 -0.140625 1.296875 -0.859375 C 0.710938 -1.578125 0.421875 -2.476562 0.421875 -3.5625 C 0.421875 -4.882812 0.738281 -5.910156 1.375 -6.640625 C 2.019531 -7.378906 2.835938 -7.75 3.828125 -7.75 Z M 3.640625 -7.71875 Z M 3.640625 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="26819dd2-f366-499f-83d2-8d25a3e48439">
<path style="stroke:none;" d="M 1.6875 -2.359375 C 1.71875 -1.941406 1.820312 -1.617188 2 -1.390625 C 2.3125 -0.984375 2.863281 -0.78125 3.65625 -0.78125 C 4.125 -0.78125 4.535156 -0.878906 4.890625 -1.078125 C 5.253906 -1.285156 5.4375 -1.601562 5.4375 -2.03125 C 5.4375 -2.351562 5.289062 -2.597656 5 -2.765625 C 4.820312 -2.867188 4.460938 -2.988281 3.921875 -3.125 L 2.90625 -3.390625 C 2.269531 -3.546875 1.796875 -3.722656 1.484375 -3.921875 C 0.941406 -4.265625 0.671875 -4.738281 0.671875 -5.34375 C 0.671875 -6.050781 0.925781 -6.625 1.4375 -7.0625 C 1.957031 -7.507812 2.648438 -7.734375 3.515625 -7.734375 C 4.648438 -7.734375 5.46875 -7.398438 5.96875 -6.734375 C 6.28125 -6.304688 6.429688 -5.847656 6.421875 -5.359375 L 5.234375 -5.359375 C 5.210938 -5.648438 5.113281 -5.910156 4.9375 -6.140625 C 4.644531 -6.472656 4.140625 -6.640625 3.421875 -6.640625 C 2.941406 -6.640625 2.578125 -6.546875 2.328125 -6.359375 C 2.085938 -6.179688 1.96875 -5.945312 1.96875 -5.65625 C 1.96875 -5.320312 2.128906 -5.054688 2.453125 -4.859375 C 2.640625 -4.742188 2.914062 -4.640625 3.28125 -4.546875 L 4.109375 -4.34375 C 5.023438 -4.125 5.632812 -3.910156 5.9375 -3.703125 C 6.4375 -3.378906 6.6875 -2.875 6.6875 -2.1875 C 6.6875 -1.507812 6.429688 -0.925781 5.921875 -0.4375 C 5.410156 0.0390625 4.632812 0.28125 3.59375 0.28125 C 2.46875 0.28125 1.671875 0.03125 1.203125 -0.46875 C 0.742188 -0.976562 0.5 -1.609375 0.46875 -2.359375 Z M 3.546875 -7.71875 Z M 3.546875 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="f34f1599-c6aa-4236-b55b-ffc8d044d92b">
<path style="stroke:none;" d=""/>
</symbol>
<symbol overflow="visible" id="e789c8ac-7bd9-47e9-b425-0da4de1b8fae">
<path style="stroke:none;" d="M 1.515625 -7.53125 L 2.96875 -1.59375 L 4.4375 -7.53125 L 5.859375 -7.53125 L 7.328125 -1.625 L 8.875 -7.53125 L 10.140625 -7.53125 L 7.953125 0 L 6.640625 0 L 5.09375 -5.828125 L 3.609375 0 L 2.296875 0 L 0.125 -7.53125 Z M 1.515625 -7.53125 "/>
</symbol>
<symbol overflow="visible" id="2d8b0f6b-6965-4760-b8fe-2d3e713597be">
<path style="stroke:none;" d="M 0.921875 -7.5 L 2.21875 -7.5 L 2.21875 0 L 0.921875 0 Z M 0.921875 -10.328125 L 2.21875 -10.328125 L 2.21875 -8.890625 L 0.921875 -8.890625 Z M 0.921875 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="fe9d50b4-fb24-4ac8-8e20-718c9982d619">
<path style="stroke:none;" d="M 0.921875 -10.375 L 2.1875 -10.375 L 2.1875 -6.515625 C 2.488281 -6.890625 2.757812 -7.15625 3 -7.3125 C 3.40625 -7.582031 3.914062 -7.71875 4.53125 -7.71875 C 5.625 -7.71875 6.363281 -7.332031 6.75 -6.5625 C 6.957031 -6.144531 7.0625 -5.566406 7.0625 -4.828125 L 7.0625 0 L 5.765625 0 L 5.765625 -4.75 C 5.765625 -5.300781 5.695312 -5.707031 5.5625 -5.96875 C 5.332031 -6.375 4.898438 -6.578125 4.265625 -6.578125 C 3.734375 -6.578125 3.253906 -6.394531 2.828125 -6.03125 C 2.398438 -5.675781 2.1875 -5 2.1875 -4 L 2.1875 0 L 0.921875 0 Z M 0.921875 -10.375 "/>
</symbol>
<symbol overflow="visible" id="88777803-396f-4493-9402-7b7eb914e919">
<path style="stroke:none;" d="M 1.90625 -10.328125 L 4.875 -1.53125 L 7.8125 -10.328125 L 9.390625 -10.328125 L 5.609375 0 L 4.125 0 L 0.359375 -10.328125 Z M 1.90625 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="c3c40542-ca82-43ac-a699-8156b48bb1fe">
<path style="stroke:none;" d="M 3.921875 -0.8125 C 4.753906 -0.8125 5.328125 -1.128906 5.640625 -1.765625 C 5.953125 -2.398438 6.109375 -3.109375 6.109375 -3.890625 C 6.109375 -4.585938 6 -5.160156 5.78125 -5.609375 C 5.414062 -6.296875 4.800781 -6.640625 3.9375 -6.640625 C 3.15625 -6.640625 2.585938 -6.34375 2.234375 -5.75 C 1.890625 -5.164062 1.71875 -4.457031 1.71875 -3.625 C 1.71875 -2.820312 1.890625 -2.148438 2.234375 -1.609375 C 2.585938 -1.078125 3.148438 -0.8125 3.921875 -0.8125 Z M 3.96875 -7.75 C 4.9375 -7.75 5.753906 -7.425781 6.421875 -6.78125 C 7.097656 -6.132812 7.4375 -5.179688 7.4375 -3.921875 C 7.4375 -2.710938 7.140625 -1.707031 6.546875 -0.90625 C 5.953125 -0.113281 5.035156 0.28125 3.796875 0.28125 C 2.765625 0.28125 1.941406 -0.0664062 1.328125 -0.765625 C 0.722656 -1.472656 0.421875 -2.421875 0.421875 -3.609375 C 0.421875 -4.867188 0.738281 -5.875 1.375 -6.625 C 2.019531 -7.375 2.882812 -7.75 3.96875 -7.75 Z M 3.921875 -7.71875 Z M 3.921875 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="7d78e2ff-410c-4d65-8add-83ca564dda13">
<path style="stroke:none;" d="M 0.96875 -10.328125 L 2.234375 -10.328125 L 2.234375 0 L 0.96875 0 Z M 0.96875 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="c5f79883-1856-4a40-be0c-873f60a59de0">
<path style="stroke:none;" d="M 1.78125 -10.328125 L 3.734375 -1.921875 L 6.0625 -10.328125 L 7.578125 -10.328125 L 9.921875 -1.921875 L 11.859375 -10.328125 L 13.40625 -10.328125 L 10.6875 0 L 9.21875 0 L 6.828125 -8.5625 L 4.4375 0 L 2.96875 0 L 0.265625 -10.328125 Z M 1.78125 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="880d1c56-4419-4efc-bf12-b19b05df8215">
<path style="stroke:none;" d="M 0.96875 -7.53125 L 2.171875 -7.53125 L 2.171875 -6.234375 C 2.265625 -6.484375 2.503906 -6.789062 2.890625 -7.15625 C 3.273438 -7.519531 3.71875 -7.703125 4.21875 -7.703125 C 4.238281 -7.703125 4.273438 -7.695312 4.328125 -7.6875 C 4.390625 -7.6875 4.488281 -7.679688 4.625 -7.671875 L 4.625 -6.328125 C 4.550781 -6.347656 4.484375 -6.359375 4.421875 -6.359375 C 4.359375 -6.359375 4.289062 -6.359375 4.21875 -6.359375 C 3.570312 -6.359375 3.078125 -6.15625 2.734375 -5.75 C 2.398438 -5.34375 2.234375 -4.867188 2.234375 -4.328125 L 2.234375 0 L 0.96875 0 Z M 0.96875 -7.53125 "/>
</symbol>
<symbol overflow="visible" id="2190d7a2-ae5b-4713-a8bd-0f8663f8dc6a">
<path style="stroke:none;" d="M 1.734375 -3.671875 C 1.734375 -2.867188 1.898438 -2.195312 2.234375 -1.65625 C 2.578125 -1.113281 3.128906 -0.84375 3.890625 -0.84375 C 4.472656 -0.84375 4.953125 -1.09375 5.328125 -1.59375 C 5.710938 -2.09375 5.90625 -2.816406 5.90625 -3.765625 C 5.90625 -4.710938 5.707031 -5.414062 5.3125 -5.875 C 4.925781 -6.332031 4.445312 -6.5625 3.875 -6.5625 C 3.238281 -6.5625 2.722656 -6.316406 2.328125 -5.828125 C 1.929688 -5.335938 1.734375 -4.617188 1.734375 -3.671875 Z M 3.640625 -7.671875 C 4.210938 -7.671875 4.691406 -7.546875 5.078125 -7.296875 C 5.304688 -7.160156 5.566406 -6.914062 5.859375 -6.5625 L 5.859375 -10.375 L 7.0625 -10.375 L 7.0625 0 L 5.9375 0 L 5.9375 -1.046875 C 5.632812 -0.585938 5.28125 -0.253906 4.875 -0.046875 C 4.476562 0.160156 4.019531 0.265625 3.5 0.265625 C 2.65625 0.265625 1.925781 -0.0820312 1.3125 -0.78125 C 0.695312 -1.488281 0.390625 -2.429688 0.390625 -3.609375 C 0.390625 -4.703125 0.671875 -5.648438 1.234375 -6.453125 C 1.796875 -7.265625 2.597656 -7.671875 3.640625 -7.671875 Z M 3.640625 -7.671875 "/>
</symbol>
</g>
<clipPath id="9993dc39-19c3-4715-a471-51cc866bd12c">
  <path d="M 124.46875 31.929688 L 454 31.929688 L 454 253 L 124.46875 253 Z M 124.46875 31.929688 "/>
</clipPath>
<clipPath id="a4b7967d-ec87-46c0-b831-e5f96ed690d0">
  <path d="M 189 31.929688 L 190 31.929688 L 190 253 L 189 253 Z M 189 31.929688 "/>
</clipPath>
<clipPath id="715a7c0f-235b-467d-a0ae-0b0a4d940ee0">
  <path d="M 288 31.929688 L 290 31.929688 L 290 253 L 288 253 Z M 288 31.929688 "/>
</clipPath>
<clipPath id="fb0d8393-7c3d-4a7e-abb1-cbb1ca7e5c0f">
  <path d="M 388 31.929688 L 390 31.929688 L 390 253 L 388 253 Z M 388 31.929688 "/>
</clipPath>
<clipPath id="07f88569-1012-4d3a-979f-c970412b89fb">
  <path d="M 124.46875 230 L 454.601562 230 L 454.601562 232 L 124.46875 232 Z M 124.46875 230 "/>
</clipPath>
<clipPath id="4cf4ebdf-f545-400a-b77b-2b556714b2f2">
  <path d="M 124.46875 194 L 454.601562 194 L 454.601562 196 L 124.46875 196 Z M 124.46875 194 "/>
</clipPath>
<clipPath id="a9c2dbc2-ca0f-4c76-8c5f-2c77139ccd03">
  <path d="M 124.46875 159 L 454.601562 159 L 454.601562 161 L 124.46875 161 Z M 124.46875 159 "/>
</clipPath>
<clipPath id="92fefbe6-ab87-4712-8e3e-dcab88a8e860">
  <path d="M 124.46875 123 L 454.601562 123 L 454.601562 125 L 124.46875 125 Z M 124.46875 123 "/>
</clipPath>
<clipPath id="2de43b69-cb0f-4a36-84f2-74826e49cc56">
  <path d="M 124.46875 88 L 454.601562 88 L 454.601562 90 L 124.46875 90 Z M 124.46875 88 "/>
</clipPath>
<clipPath id="09ed2bac-181a-49a8-8edd-36eb778fc896">
  <path d="M 124.46875 52 L 454.601562 52 L 454.601562 54 L 124.46875 54 Z M 124.46875 52 "/>
</clipPath>
<clipPath id="bb70d263-209f-4988-85ee-7d119a19c479">
  <path d="M 138 31.929688 L 140 31.929688 L 140 253 L 138 253 Z M 138 31.929688 "/>
</clipPath>
<clipPath id="e3779c88-5535-4140-bcb5-f5860c13803f">
  <path d="M 238 31.929688 L 240 31.929688 L 240 253 L 238 253 Z M 238 31.929688 "/>
</clipPath>
<clipPath id="9f751b91-65e5-4ae7-ab3e-f409d3d919af">
  <path d="M 338 31.929688 L 340 31.929688 L 340 253 L 338 253 Z M 338 31.929688 "/>
</clipPath>
<clipPath id="d82cc667-ae85-43e5-85ca-8a64bdb3b94b">
  <path d="M 438 31.929688 L 440 31.929688 L 440 253 L 438 253 Z M 438 31.929688 "/>
</clipPath>
</defs>
<g id="e5559951-8ef0-460e-9899-66e34ec7bc54">
<rect x="0" y="0" width="468" height="289" style="fill:rgb(100%,100%,100%);fill-opacity:1;stroke:none;"/>
<rect x="0" y="0" width="468" height="289" style="fill:rgb(100%,100%,100%);fill-opacity:1;stroke:none;"/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:round;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 0 289 L 468 289 L 468 0 L 0 0 Z M 0 289 "/>
<g clip-path="url(#9993dc39-19c3-4715-a471-51cc866bd12c)" clip-rule="nonzero">
<path style=" stroke:none;fill-rule:nonzero;fill:rgb(89.803922%,89.803922%,89.803922%);fill-opacity:1;" d="M 124.46875 252.027344 L 453.601562 252.027344 L 453.601562 31.925781 L 124.46875 31.925781 Z M 124.46875 252.027344 "/>
</g>
<g clip-path="url(#a4b7967d-ec87-46c0-b831-e5f96ed690d0)" clip-rule="nonzero">
<path style="fill:none;stroke-width:0.531496;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(94.901961%,94.901961%,94.901961%);stroke-opacity:1;stroke-miterlimit:10;" d="M 189.296875 252.027344 L 189.296875 31.929688 "/>
</g>
<g clip-path="url(#715a7c0f-235b-467d-a0ae-0b0a4d940ee0)" clip-rule="nonzero">
<path style="fill:none;stroke-width:0.531496;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(94.901961%,94.901961%,94.901961%);stroke-opacity:1;stroke-miterlimit:10;" d="M 289.035156 252.027344 L 289.035156 31.929688 "/>
</g>
<g clip-path="url(#fb0d8393-7c3d-4a7e-abb1-cbb1ca7e5c0f)" clip-rule="nonzero">
<path style="fill:none;stroke-width:0.531496;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(94.901961%,94.901961%,94.901961%);stroke-opacity:1;stroke-miterlimit:10;" d="M 388.769531 252.027344 L 388.769531 31.929688 "/>
</g>
<g clip-path="url(#07f88569-1012-4d3a-979f-c970412b89fb)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 230.730469 L 453.601562 230.730469 "/>
</g>
<g clip-path="url(#4cf4ebdf-f545-400a-b77b-2b556714b2f2)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 195.230469 L 453.601562 195.230469 "/>
</g>
<g clip-path="url(#a9c2dbc2-ca0f-4c76-8c5f-2c77139ccd03)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 159.730469 L 453.601562 159.730469 "/>
</g>
<g clip-path="url(#92fefbe6-ab87-4712-8e3e-dcab88a8e860)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 124.226562 L 453.601562 124.226562 "/>
</g>
<g clip-path="url(#2de43b69-cb0f-4a36-84f2-74826e49cc56)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 88.726562 L 453.601562 88.726562 "/>
</g>
<g clip-path="url(#09ed2bac-181a-49a8-8edd-36eb778fc896)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 53.226562 L 453.601562 53.226562 "/>
</g>
<g clip-path="url(#bb70d263-209f-4988-85ee-7d119a19c479)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 252.027344 L 139.429688 31.929688 "/>
</g>
<g clip-path="url(#e3779c88-5535-4140-bcb5-f5860c13803f)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 239.167969 252.027344 L 239.167969 31.929688 "/>
</g>
<g clip-path="url(#9f751b91-65e5-4ae7-ab3e-f409d3d919af)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 338.902344 252.027344 L 338.902344 31.929688 "/>
</g>
<g clip-path="url(#d82cc667-ae85-43e5-85ca-8a64bdb3b94b)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 438.640625 252.027344 L 438.640625 31.929688 "/>
</g>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 246.703125 L 242.257812 246.703125 L 242.257812 214.753906 L 139.429688 214.753906 Z M 139.429688 246.703125 "/>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 211.203125 L 148.105469 211.203125 L 148.105469 179.253906 L 139.429688 179.253906 Z M 139.429688 211.203125 "/>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 175.703125 L 396.550781 175.703125 L 396.550781 143.753906 L 139.429688 143.753906 Z M 139.429688 175.703125 "/>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 140.203125 L 316.164062 140.203125 L 316.164062 108.253906 L 139.429688 108.253906 Z M 139.429688 140.203125 "/>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 104.703125 L 219.617188 104.703125 L 219.617188 72.753906 L 139.429688 72.753906 Z M 139.429688 104.703125 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 69.203125 L 139.429688 37.253906 Z M 139.429688 69.203125 "/>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#e1f59f04-5e4e-4005-845e-f577cc40f92a" x="245.804688" y="235.816406"/>
  <use xlink:href="#666c3977-4de8-4c0e-95f1-8d3c61fb29aa" x="253.686417" y="235.816406"/>
  <use xlink:href="#944ffb7c-627f-4dfd-9d04-e2d0af53ba08" x="257.623825" y="235.816406"/>
  <use xlink:href="#9f3ee9e5-2e24-446e-a5cf-37bf69073e19" x="265.505554" y="235.816406"/>
  <use xlink:href="#e1f59f04-5e4e-4005-845e-f577cc40f92a" x="273.387283" y="235.816406"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#e1f59f04-5e4e-4005-845e-f577cc40f92a" x="319.710938" y="129.3125"/>
  <use xlink:href="#666c3977-4de8-4c0e-95f1-8d3c61fb29aa" x="327.592667" y="129.3125"/>
  <use xlink:href="#99f9ca60-18bc-479b-a193-d20416ed80f5" x="331.530075" y="129.3125"/>
  <use xlink:href="#99f9ca60-18bc-479b-a193-d20416ed80f5" x="339.411804" y="129.3125"/>
  <use xlink:href="#1e5a3a02-b92d-49ca-96c1-e5a9ee43a644" x="347.293533" y="129.3125"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#bba4a26d-021f-4a7d-b42d-4c8e89e912f6" x="221.980469" y="93.8125"/>
  <use xlink:href="#944ffb7c-627f-4dfd-9d04-e2d0af53ba08" x="229.862198" y="93.8125"/>
  <use xlink:href="#55311ce1-a84c-4978-992f-599891defeb0" x="237.743927" y="93.8125"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#bba4a26d-021f-4a7d-b42d-4c8e89e912f6" x="149.683594" y="200.316406"/>
  <use xlink:href="#99f9ca60-18bc-479b-a193-d20416ed80f5" x="157.565323" y="200.316406"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#944ffb7c-627f-4dfd-9d04-e2d0af53ba08" x="140.21875" y="58.3125"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#1e5a3a02-b92d-49ca-96c1-e5a9ee43a644" x="400.097656" y="164.816406"/>
  <use xlink:href="#666c3977-4de8-4c0e-95f1-8d3c61fb29aa" x="407.979385" y="164.816406"/>
  <use xlink:href="#7e029b41-3520-491e-b479-90727ece6964" x="411.916794" y="164.816406"/>
  <use xlink:href="#99f9ca60-18bc-479b-a193-d20416ed80f5" x="419.798523" y="164.816406"/>
  <use xlink:href="#bba4a26d-021f-4a7d-b42d-4c8e89e912f6" x="427.680252" y="164.816406"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#659056ac-269c-4440-86be-8021d19011db" x="27.800781" y="234.167969"/>
  <use xlink:href="#ffd5e809-4dbb-44a8-89b0-c7eee7ab2dea" x="33.661026" y="234.167969"/>
  <use xlink:href="#f9964a6e-4473-4aa2-9d0f-7ffba800f5a6" x="38.996613" y="234.167969"/>
  <use xlink:href="#1f74055e-05d4-44c8-8db4-06e2f87a1cb9" x="44.332199" y="234.167969"/>
  <use xlink:href="#848cac35-f92f-4404-90fd-7af43acabf9f" x="46.99765" y="234.167969"/>
  <use xlink:href="#7cf6fc57-e82c-4f2f-9a25-e39b7da76c97" x="53.396606" y="234.167969"/>
  <use xlink:href="#7cf6fc57-e82c-4f2f-9a25-e39b7da76c97" x="58.732193" y="234.167969"/>
  <use xlink:href="#5fad5bf2-b61e-43d9-a589-8d2c0b2eb207" x="64.06778" y="234.167969"/>
  <use xlink:href="#1f74055e-05d4-44c8-8db4-06e2f87a1cb9" x="68.864655" y="234.167969"/>
  <use xlink:href="#7cf6fc57-e82c-4f2f-9a25-e39b7da76c97" x="71.530106" y="234.167969"/>
  <use xlink:href="#6288c52d-503a-4ad6-8249-f1e4230911dd" x="76.865692" y="234.167969"/>
  <use xlink:href="#1f74055e-05d4-44c8-8db4-06e2f87a1cb9" x="79.531143" y="234.167969"/>
  <use xlink:href="#19538bb7-81d0-4791-9867-734392c76cbf" x="82.196594" y="234.167969"/>
  <use xlink:href="#7cf6fc57-e82c-4f2f-9a25-e39b7da76c97" x="90.188263" y="234.167969"/>
  <use xlink:href="#83a3e4ab-24d0-4a1a-a17c-a56cc2320191" x="95.523849" y="234.167969"/>
  <use xlink:href="#6484a8e0-84d2-4646-9c98-c4a42c63c4e2" x="98.718643" y="234.167969"/>
  <use xlink:href="#7cf6fc57-e82c-4f2f-9a25-e39b7da76c97" x="106.710312" y="234.167969"/>
  <use xlink:href="#a21d095b-1ac0-40fa-bdae-94ecfc59b1cb" x="112.045898" y="234.167969"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#659056ac-269c-4440-86be-8021d19011db" x="37.925781" y="198.667969"/>
  <use xlink:href="#ffd5e809-4dbb-44a8-89b0-c7eee7ab2dea" x="43.786026" y="198.667969"/>
  <use xlink:href="#f9964a6e-4473-4aa2-9d0f-7ffba800f5a6" x="49.121613" y="198.667969"/>
  <use xlink:href="#1f74055e-05d4-44c8-8db4-06e2f87a1cb9" x="54.457199" y="198.667969"/>
  <use xlink:href="#b18e76e1-6fd0-4ebc-9265-35d35ac02aef" x="57.12265" y="198.667969"/>
  <use xlink:href="#ffd5e809-4dbb-44a8-89b0-c7eee7ab2dea" x="64.050949" y="198.667969"/>
  <use xlink:href="#55057c74-a3c0-4b7e-877a-53e79f85f2ca" x="69.386536" y="198.667969"/>
  <use xlink:href="#6484a8e0-84d2-4646-9c98-c4a42c63c4e2" x="74.722122" y="198.667969"/>
  <use xlink:href="#6484a8e0-84d2-4646-9c98-c4a42c63c4e2" x="82.713791" y="198.667969"/>
  <use xlink:href="#55057c74-a3c0-4b7e-877a-53e79f85f2ca" x="90.70546" y="198.667969"/>
  <use xlink:href="#c7e8f52d-1df3-48c9-8a70-113c539f55ea" x="96.041046" y="198.667969"/>
  <use xlink:href="#55057c74-a3c0-4b7e-877a-53e79f85f2ca" x="101.376633" y="198.667969"/>
  <use xlink:href="#7353f8fb-4e9f-4f78-8a04-634ea64cccfc" x="106.712219" y="198.667969"/>
  <use xlink:href="#55057c74-a3c0-4b7e-877a-53e79f85f2ca" x="112.047806" y="198.667969"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#659056ac-269c-4440-86be-8021d19011db" x="54.996094" y="163.167969"/>
  <use xlink:href="#ffd5e809-4dbb-44a8-89b0-c7eee7ab2dea" x="60.856339" y="163.167969"/>
  <use xlink:href="#f9964a6e-4473-4aa2-9d0f-7ffba800f5a6" x="66.191925" y="163.167969"/>
  <use xlink:href="#1f74055e-05d4-44c8-8db4-06e2f87a1cb9" x="71.527512" y="163.167969"/>
  <use xlink:href="#1fcf57b7-5792-4872-9ed3-df5115617b53" x="74.192963" y="163.167969"/>
  <use xlink:href="#7cf6fc57-e82c-4f2f-9a25-e39b7da76c97" x="81.121262" y="163.167969"/>
  <use xlink:href="#f5ce2708-213d-46a5-a040-2eea16cdd56b" x="86.456848" y="163.167969"/>
  <use xlink:href="#e1c9905d-912a-4e22-89d8-77c01f61b0ce" x="88.588272" y="163.167969"/>
  <use xlink:href="#1f74055e-05d4-44c8-8db4-06e2f87a1cb9" x="93.385147" y="163.167969"/>
  <use xlink:href="#848cac35-f92f-4404-90fd-7af43acabf9f" x="96.050598" y="163.167969"/>
  <use xlink:href="#eb7e45a8-7452-4c2e-92e0-02ab9a8ee723" x="102.449554" y="163.167969"/>
  <use xlink:href="#abe8160a-1919-4d75-b0f5-32065de8ee3d" x="104.580978" y="163.167969"/>
  <use xlink:href="#f5ce2708-213d-46a5-a040-2eea16cdd56b" x="109.916565" y="163.167969"/>
  <use xlink:href="#f9964a6e-4473-4aa2-9d0f-7ffba800f5a6" x="112.047989" y="163.167969"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#659056ac-269c-4440-86be-8021d19011db" x="23.011719" y="127.664062"/>
  <use xlink:href="#ffd5e809-4dbb-44a8-89b0-c7eee7ab2dea" x="28.871964" y="127.664062"/>
  <use xlink:href="#f9964a6e-4473-4aa2-9d0f-7ffba800f5a6" x="34.20755" y="127.664062"/>
  <use xlink:href="#1f74055e-05d4-44c8-8db4-06e2f87a1cb9" x="39.543137" y="127.664062"/>
  <use xlink:href="#1792dfa6-17a0-4c27-b6de-545e7b48e905" x="42.208588" y="127.664062"/>
  <use xlink:href="#f9964a6e-4473-4aa2-9d0f-7ffba800f5a6" x="47.005463" y="127.664062"/>
  <use xlink:href="#1851c680-3a62-4ed4-9965-20223a18a155" x="52.341049" y="127.664062"/>
  <use xlink:href="#eb7e45a8-7452-4c2e-92e0-02ab9a8ee723" x="59.269348" y="127.664062"/>
  <use xlink:href="#6fc71a2d-be20-4a59-a6ff-fc2e8ad8e748" x="61.400772" y="127.664062"/>
  <use xlink:href="#ffd5e809-4dbb-44a8-89b0-c7eee7ab2dea" x="66.197647" y="127.664062"/>
  <use xlink:href="#1f74055e-05d4-44c8-8db4-06e2f87a1cb9" x="71.533234" y="127.664062"/>
  <use xlink:href="#73c6d84e-53b2-4866-a787-2703090c3e72" x="74.198685" y="127.664062"/>
  <use xlink:href="#71cbe45c-e2b4-4fc6-8717-e4b8940c9cae" x="80.597641" y="127.664062"/>
  <use xlink:href="#83a3e4ab-24d0-4a1a-a17c-a56cc2320191" x="85.394516" y="127.664062"/>
  <use xlink:href="#eb7e45a8-7452-4c2e-92e0-02ab9a8ee723" x="88.58931" y="127.664062"/>
  <use xlink:href="#c7e8f52d-1df3-48c9-8a70-113c539f55ea" x="90.720734" y="127.664062"/>
  <use xlink:href="#efa93de8-ea89-458f-be2d-eaf720aea985" x="96.05632" y="127.664062"/>
  <use xlink:href="#72b4a8cb-c01e-4a43-90c4-4382a6a86ac5" x="98.721771" y="127.664062"/>
  <use xlink:href="#83a3e4ab-24d0-4a1a-a17c-a56cc2320191" x="104.057358" y="127.664062"/>
  <use xlink:href="#f9964a6e-4473-4aa2-9d0f-7ffba800f5a6" x="107.252151" y="127.664062"/>
  <use xlink:href="#6fc71a2d-be20-4a59-a6ff-fc2e8ad8e748" x="112.587738" y="127.664062"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#659056ac-269c-4440-86be-8021d19011db" x="72.585938" y="92.164062"/>
  <use xlink:href="#ffd5e809-4dbb-44a8-89b0-c7eee7ab2dea" x="78.446182" y="92.164062"/>
  <use xlink:href="#f9964a6e-4473-4aa2-9d0f-7ffba800f5a6" x="83.781769" y="92.164062"/>
  <use xlink:href="#1f74055e-05d4-44c8-8db4-06e2f87a1cb9" x="89.117355" y="92.164062"/>
  <use xlink:href="#8e559626-3c66-4af9-8519-a12d02b3a72d" x="91.782806" y="92.164062"/>
  <use xlink:href="#7cf6fc57-e82c-4f2f-9a25-e39b7da76c97" x="98.181763" y="92.164062"/>
  <use xlink:href="#83a3e4ab-24d0-4a1a-a17c-a56cc2320191" x="103.517349" y="92.164062"/>
  <use xlink:href="#55057c74-a3c0-4b7e-877a-53e79f85f2ca" x="106.712143" y="92.164062"/>
  <use xlink:href="#a21d095b-1ac0-40fa-bdae-94ecfc59b1cb" x="112.047729" y="92.164062"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#659056ac-269c-4440-86be-8021d19011db" x="70.984375" y="56.664062"/>
  <use xlink:href="#ffd5e809-4dbb-44a8-89b0-c7eee7ab2dea" x="76.84462" y="56.664062"/>
  <use xlink:href="#f9964a6e-4473-4aa2-9d0f-7ffba800f5a6" x="82.180206" y="56.664062"/>
  <use xlink:href="#1f74055e-05d4-44c8-8db4-06e2f87a1cb9" x="87.515793" y="56.664062"/>
  <use xlink:href="#6ca87a35-a9c4-4e6d-b217-24747592d47b" x="90.181244" y="56.664062"/>
  <use xlink:href="#f9964a6e-4473-4aa2-9d0f-7ffba800f5a6" x="96.5802" y="56.664062"/>
  <use xlink:href="#7353f8fb-4e9f-4f78-8a04-634ea64cccfc" x="101.915787" y="56.664062"/>
  <use xlink:href="#55057c74-a3c0-4b7e-877a-53e79f85f2ca" x="107.251373" y="56.664062"/>
  <use xlink:href="#6fc71a2d-be20-4a59-a6ff-fc2e8ad8e748" x="112.58696" y="56.664062"/>
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
  <use xlink:href="#6a07bcc2-6d15-42e2-a912-c439f123a0c3" x="136.761719" y="265.992188"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#a4385ed1-1006-4b85-ba44-08fda2942e48" x="228.496094" y="265.992188"/>
  <use xlink:href="#6a07bcc2-6d15-42e2-a912-c439f123a0c3" x="233.83168" y="265.992188"/>
  <use xlink:href="#6a07bcc2-6d15-42e2-a912-c439f123a0c3" x="239.167267" y="265.992188"/>
  <use xlink:href="#6a07bcc2-6d15-42e2-a912-c439f123a0c3" x="244.502853" y="265.992188"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#21e751a0-55ec-4cee-9bd5-97d46c6344fa" x="328.230469" y="265.992188"/>
  <use xlink:href="#6a07bcc2-6d15-42e2-a912-c439f123a0c3" x="333.566055" y="265.992188"/>
  <use xlink:href="#6a07bcc2-6d15-42e2-a912-c439f123a0c3" x="338.901642" y="265.992188"/>
  <use xlink:href="#6a07bcc2-6d15-42e2-a912-c439f123a0c3" x="344.237228" y="265.992188"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#c148a3ce-e7e7-4c9d-a2ca-fd60a9ec6339" x="427.96875" y="265.992188"/>
  <use xlink:href="#6a07bcc2-6d15-42e2-a912-c439f123a0c3" x="433.304337" y="265.992188"/>
  <use xlink:href="#6a07bcc2-6d15-42e2-a912-c439f123a0c3" x="438.639923" y="265.992188"/>
  <use xlink:href="#6a07bcc2-6d15-42e2-a912-c439f123a0c3" x="443.97551" y="265.992188"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#d9b8efb5-1fb2-450a-a183-d3c88d144fd5" x="193.347656" y="28.328125"/>
  <use xlink:href="#f1f5f504-1458-4808-8e68-f3fb5929801d" x="202.956512" y="28.328125"/>
  <use xlink:href="#4e72fb75-1d73-438c-b822-0202d43b9c10" x="210.968582" y="28.328125"/>
  <use xlink:href="#f8c53319-2793-42be-8a06-33b36ee69f00" x="218.980652" y="28.328125"/>
  <use xlink:href="#f1f5f504-1458-4808-8e68-f3fb5929801d" x="222.98317" y="28.328125"/>
  <use xlink:href="#4e72fb75-1d73-438c-b822-0202d43b9c10" x="230.995239" y="28.328125"/>
  <use xlink:href="#9269f457-2621-4361-8a0e-df6fea0d7687" x="239.007309" y="28.328125"/>
  <use xlink:href="#f1f5f504-1458-4808-8e68-f3fb5929801d" x="246.210434" y="28.328125"/>
  <use xlink:href="#26819dd2-f366-499f-83d2-8d25a3e48439" x="254.222504" y="28.328125"/>
  <use xlink:href="#f34f1599-c6aa-4236-b55b-ffc8d044d92b" x="261.425629" y="28.328125"/>
  <use xlink:href="#e789c8ac-7bd9-47e9-b425-0da4de1b8fae" x="265.428146" y="28.328125"/>
  <use xlink:href="#2d8b0f6b-6965-4760-b8fe-2d3e713597be" x="275.831879" y="28.328125"/>
  <use xlink:href="#f8c53319-2793-42be-8a06-33b36ee69f00" x="279.032486" y="28.328125"/>
  <use xlink:href="#fe9d50b4-fb24-4ac8-8e20-718c9982d619" x="283.035004" y="28.328125"/>
  <use xlink:href="#f34f1599-c6aa-4236-b55b-ffc8d044d92b" x="291.047073" y="28.328125"/>
  <use xlink:href="#88777803-396f-4493-9402-7b7eb914e919" x="295.049591" y="28.328125"/>
  <use xlink:href="#2d8b0f6b-6965-4760-b8fe-2d3e713597be" x="304.658447" y="28.328125"/>
  <use xlink:href="#c3c40542-ca82-43ac-a699-8156b48bb1fe" x="307.859055" y="28.328125"/>
  <use xlink:href="#7d78e2ff-410c-4d65-8add-83ca564dda13" x="315.871124" y="28.328125"/>
  <use xlink:href="#f1f5f504-1458-4808-8e68-f3fb5929801d" x="319.071732" y="28.328125"/>
  <use xlink:href="#4e72fb75-1d73-438c-b822-0202d43b9c10" x="327.083801" y="28.328125"/>
  <use xlink:href="#f8c53319-2793-42be-8a06-33b36ee69f00" x="335.095871" y="28.328125"/>
  <use xlink:href="#f34f1599-c6aa-4236-b55b-ffc8d044d92b" x="339.098389" y="28.328125"/>
  <use xlink:href="#c5f79883-1856-4a40-be0c-873f60a59de0" x="343.100906" y="28.328125"/>
  <use xlink:href="#c3c40542-ca82-43ac-a699-8156b48bb1fe" x="356.698212" y="28.328125"/>
  <use xlink:href="#880d1c56-4419-4efc-bf12-b19b05df8215" x="364.710281" y="28.328125"/>
  <use xlink:href="#2190d7a2-ae5b-4713-a8bd-0f8663f8dc6a" x="369.507675" y="28.328125"/>
  <use xlink:href="#26819dd2-f366-499f-83d2-8d25a3e48439" x="377.519745" y="28.328125"/>
</g>
</g>
</svg>


By a landslide, the Bible is on top, followed closely by the Jewish Scriptures.
This isn't unexpected for two reasons:

1. The Bible and Jewish Scriptures have the most sentences.
2. The Jewish Scriptures share a huge amount of material with the Bible.

The Vedas has no violent sentences at all.

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
(spit "images/violent-normalized-plot.svg" (gg4clj/render violent-normalized-plot))

;; ... and render it in the REPL.
(gg4clj/view violent-normalized-plot)
```

<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="468pt" height="289pt" viewBox="0 0 468 289" version="1.1">
<defs>
<g>
<symbol overflow="visible" id="c443da76-c56c-4f37-b39c-bb033534ae27">
<path style="stroke:none;" d="M 0.453125 0 L 0.453125 -10.171875 L 8.53125 -10.171875 L 8.53125 0 Z M 7.25 -1.265625 L 7.25 -8.890625 L 1.734375 -8.890625 L 1.734375 -1.265625 Z M 7.25 -1.265625 "/>
</symbol>
<symbol overflow="visible" id="d97e28b4-f27c-44b3-86d7-3210c0db3593">
<path style="stroke:none;" d="M 3.828125 -9.90625 C 5.109375 -9.90625 6.035156 -9.378906 6.609375 -8.328125 C 7.054688 -7.503906 7.28125 -6.382812 7.28125 -4.96875 C 7.28125 -3.625 7.078125 -2.507812 6.671875 -1.625 C 6.097656 -0.363281 5.148438 0.265625 3.828125 0.265625 C 2.640625 0.265625 1.753906 -0.25 1.171875 -1.28125 C 0.679688 -2.144531 0.4375 -3.300781 0.4375 -4.75 C 0.4375 -5.875 0.582031 -6.84375 0.875 -7.65625 C 1.425781 -9.15625 2.410156 -9.90625 3.828125 -9.90625 Z M 3.8125 -0.859375 C 4.457031 -0.859375 4.972656 -1.144531 5.359375 -1.71875 C 5.742188 -2.289062 5.9375 -3.359375 5.9375 -4.921875 C 5.9375 -6.046875 5.796875 -6.96875 5.515625 -7.6875 C 5.242188 -8.414062 4.707031 -8.78125 3.90625 -8.78125 C 3.175781 -8.78125 2.640625 -8.4375 2.296875 -7.75 C 1.960938 -7.0625 1.796875 -6.046875 1.796875 -4.703125 C 1.796875 -3.691406 1.90625 -2.878906 2.125 -2.265625 C 2.445312 -1.328125 3.007812 -0.859375 3.8125 -0.859375 Z M 3.8125 -0.859375 "/>
</symbol>
<symbol overflow="visible" id="beb9c497-3c67-49bb-868f-d968624281fb">
<path style="stroke:none;" d="M 1.21875 -1.515625 L 2.65625 -1.515625 L 2.65625 0 L 1.21875 0 Z M 1.21875 -1.515625 "/>
</symbol>
<symbol overflow="visible" id="72f708c7-6d7c-458b-9136-e1c55a9152be">
<path style="stroke:none;" d="M 1.359375 -7.015625 L 1.359375 -7.96875 C 2.253906 -8.0625 2.878906 -8.207031 3.234375 -8.40625 C 3.585938 -8.613281 3.851562 -9.101562 4.03125 -9.875 L 5.015625 -9.875 L 5.015625 0 L 3.6875 0 L 3.6875 -7.015625 Z M 1.359375 -7.015625 "/>
</symbol>
<symbol overflow="visible" id="7e149f0d-5e7e-424b-b4af-edd3fa97266a">
<path style="stroke:none;" d="M 3.6875 0.265625 C 2.507812 0.265625 1.65625 -0.0507812 1.125 -0.6875 C 0.601562 -1.332031 0.34375 -2.117188 0.34375 -3.046875 L 1.640625 -3.046875 C 1.691406 -2.398438 1.8125 -1.929688 2 -1.640625 C 2.320312 -1.117188 2.90625 -0.859375 3.75 -0.859375 C 4.40625 -0.859375 4.929688 -1.035156 5.328125 -1.390625 C 5.722656 -1.742188 5.921875 -2.195312 5.921875 -2.75 C 5.921875 -3.425781 5.710938 -3.898438 5.296875 -4.171875 C 4.878906 -4.453125 4.300781 -4.59375 3.5625 -4.59375 C 3.476562 -4.59375 3.390625 -4.585938 3.296875 -4.578125 C 3.210938 -4.578125 3.128906 -4.578125 3.046875 -4.578125 L 3.046875 -5.671875 C 3.171875 -5.660156 3.273438 -5.648438 3.359375 -5.640625 C 3.453125 -5.640625 3.550781 -5.640625 3.65625 -5.640625 C 4.125 -5.640625 4.503906 -5.710938 4.796875 -5.859375 C 5.328125 -6.117188 5.59375 -6.582031 5.59375 -7.25 C 5.59375 -7.738281 5.414062 -8.113281 5.0625 -8.375 C 4.71875 -8.644531 4.3125 -8.78125 3.84375 -8.78125 C 3.007812 -8.78125 2.4375 -8.503906 2.125 -7.953125 C 1.945312 -7.648438 1.84375 -7.21875 1.8125 -6.65625 L 0.59375 -6.65625 C 0.59375 -7.394531 0.738281 -8.023438 1.03125 -8.546875 C 1.539062 -9.460938 2.429688 -9.921875 3.703125 -9.921875 C 4.710938 -9.921875 5.492188 -9.695312 6.046875 -9.25 C 6.609375 -8.800781 6.890625 -8.148438 6.890625 -7.296875 C 6.890625 -6.679688 6.722656 -6.1875 6.390625 -5.8125 C 6.191406 -5.570312 5.929688 -5.390625 5.609375 -5.265625 C 6.128906 -5.117188 6.535156 -4.835938 6.828125 -4.421875 C 7.117188 -4.015625 7.265625 -3.519531 7.265625 -2.9375 C 7.265625 -1.988281 6.953125 -1.21875 6.328125 -0.625 C 5.703125 -0.03125 4.820312 0.265625 3.6875 0.265625 Z M 3.6875 0.265625 "/>
</symbol>
<symbol overflow="visible" id="ec024dd9-616c-480e-8c46-59221c7298fa">
<path style="stroke:none;" d="M 4.140625 -9.953125 C 5.253906 -9.953125 6.023438 -9.664062 6.453125 -9.09375 C 6.890625 -8.519531 7.109375 -7.925781 7.109375 -7.3125 L 5.875 -7.3125 C 5.800781 -7.707031 5.6875 -8.015625 5.53125 -8.234375 C 5.226562 -8.648438 4.773438 -8.859375 4.171875 -8.859375 C 3.472656 -8.859375 2.914062 -8.535156 2.5 -7.890625 C 2.09375 -7.242188 1.863281 -6.320312 1.8125 -5.125 C 2.101562 -5.539062 2.46875 -5.851562 2.90625 -6.0625 C 3.300781 -6.25 3.742188 -6.34375 4.234375 -6.34375 C 5.054688 -6.34375 5.773438 -6.078125 6.390625 -5.546875 C 7.015625 -5.015625 7.328125 -4.222656 7.328125 -3.171875 C 7.328125 -2.273438 7.035156 -1.476562 6.453125 -0.78125 C 5.867188 -0.09375 5.03125 0.25 3.9375 0.25 C 3.007812 0.25 2.207031 -0.0976562 1.53125 -0.796875 C 0.863281 -1.503906 0.53125 -2.691406 0.53125 -4.359375 C 0.53125 -5.585938 0.679688 -6.628906 0.984375 -7.484375 C 1.554688 -9.128906 2.609375 -9.953125 4.140625 -9.953125 Z M 4.0625 -0.84375 C 4.707031 -0.84375 5.191406 -1.0625 5.515625 -1.5 C 5.847656 -1.945312 6.015625 -2.472656 6.015625 -3.078125 C 6.015625 -3.578125 5.867188 -4.054688 5.578125 -4.515625 C 5.285156 -4.972656 4.757812 -5.203125 4 -5.203125 C 3.457031 -5.203125 2.984375 -5.023438 2.578125 -4.671875 C 2.179688 -4.316406 1.984375 -3.785156 1.984375 -3.078125 C 1.984375 -2.441406 2.164062 -1.910156 2.53125 -1.484375 C 2.894531 -1.054688 3.40625 -0.84375 4.0625 -0.84375 Z M 4.0625 -0.84375 "/>
</symbol>
<symbol overflow="visible" id="ee7d7ad2-544d-4959-8533-6a63f6ce2e99">
<path style="stroke:none;" d="M 3.859375 -5.75 C 4.398438 -5.75 4.828125 -5.898438 5.140625 -6.203125 C 5.453125 -6.515625 5.609375 -6.882812 5.609375 -7.3125 C 5.609375 -7.6875 5.457031 -8.023438 5.15625 -8.328125 C 4.863281 -8.640625 4.414062 -8.796875 3.8125 -8.796875 C 3.207031 -8.796875 2.769531 -8.640625 2.5 -8.328125 C 2.238281 -8.023438 2.109375 -7.664062 2.109375 -7.25 C 2.109375 -6.78125 2.28125 -6.410156 2.625 -6.140625 C 2.976562 -5.878906 3.390625 -5.75 3.859375 -5.75 Z M 3.9375 -0.84375 C 4.507812 -0.84375 4.984375 -1 5.359375 -1.3125 C 5.742188 -1.625 5.9375 -2.09375 5.9375 -2.71875 C 5.9375 -3.351562 5.738281 -3.835938 5.34375 -4.171875 C 4.957031 -4.503906 4.457031 -4.671875 3.84375 -4.671875 C 3.25 -4.671875 2.757812 -4.5 2.375 -4.15625 C 2 -3.820312 1.8125 -3.351562 1.8125 -2.75 C 1.8125 -2.238281 1.984375 -1.789062 2.328125 -1.40625 C 2.671875 -1.03125 3.207031 -0.84375 3.9375 -0.84375 Z M 2.15625 -5.28125 C 1.8125 -5.425781 1.539062 -5.597656 1.34375 -5.796875 C 0.976562 -6.171875 0.796875 -6.648438 0.796875 -7.234375 C 0.796875 -7.972656 1.0625 -8.609375 1.59375 -9.140625 C 2.132812 -9.671875 2.894531 -9.9375 3.875 -9.9375 C 4.832031 -9.9375 5.578125 -9.6875 6.109375 -9.1875 C 6.648438 -8.6875 6.921875 -8.101562 6.921875 -7.4375 C 6.921875 -6.8125 6.765625 -6.3125 6.453125 -5.9375 C 6.273438 -5.71875 6.003906 -5.503906 5.640625 -5.296875 C 6.046875 -5.109375 6.367188 -4.890625 6.609375 -4.640625 C 7.046875 -4.179688 7.265625 -3.582031 7.265625 -2.84375 C 7.265625 -1.96875 6.972656 -1.226562 6.390625 -0.625 C 5.804688 -0.0195312 4.976562 0.28125 3.90625 0.28125 C 2.9375 0.28125 2.117188 0.0195312 1.453125 -0.5 C 0.785156 -1.019531 0.453125 -1.78125 0.453125 -2.78125 C 0.453125 -3.363281 0.59375 -3.867188 0.875 -4.296875 C 1.164062 -4.722656 1.59375 -5.050781 2.15625 -5.28125 Z M 2.15625 -5.28125 "/>
</symbol>
<symbol overflow="visible" id="8101768a-8522-4f3f-a3ca-cc53921e00f5">
<path style="stroke:none;" d="M 1.875 -2.390625 C 1.914062 -1.703125 2.179688 -1.226562 2.671875 -0.96875 C 2.929688 -0.832031 3.21875 -0.765625 3.53125 -0.765625 C 4.125 -0.765625 4.628906 -1.007812 5.046875 -1.5 C 5.472656 -2 5.773438 -3.007812 5.953125 -4.53125 C 5.671875 -4.09375 5.328125 -3.78125 4.921875 -3.59375 C 4.515625 -3.414062 4.078125 -3.328125 3.609375 -3.328125 C 2.648438 -3.328125 1.890625 -3.625 1.328125 -4.21875 C 0.773438 -4.820312 0.5 -5.59375 0.5 -6.53125 C 0.5 -7.425781 0.773438 -8.210938 1.328125 -8.890625 C 1.878906 -9.578125 2.6875 -9.921875 3.75 -9.921875 C 5.195312 -9.921875 6.195312 -9.269531 6.75 -7.96875 C 7.050781 -7.257812 7.203125 -6.363281 7.203125 -5.28125 C 7.203125 -4.070312 7.019531 -3 6.65625 -2.0625 C 6.050781 -0.5 5.023438 0.28125 3.578125 0.28125 C 2.609375 0.28125 1.875 0.0234375 1.375 -0.484375 C 0.875 -0.992188 0.625 -1.628906 0.625 -2.390625 Z M 3.765625 -4.421875 C 4.265625 -4.421875 4.71875 -4.582031 5.125 -4.90625 C 5.53125 -5.238281 5.734375 -5.8125 5.734375 -6.625 C 5.734375 -7.351562 5.550781 -7.894531 5.1875 -8.25 C 4.820312 -8.601562 4.351562 -8.78125 3.78125 -8.78125 C 3.175781 -8.78125 2.691406 -8.578125 2.328125 -8.171875 C 1.972656 -7.765625 1.796875 -7.222656 1.796875 -6.546875 C 1.796875 -5.898438 1.953125 -5.382812 2.265625 -5 C 2.578125 -4.613281 3.078125 -4.421875 3.765625 -4.421875 Z M 3.765625 -4.421875 "/>
</symbol>
<symbol overflow="visible" id="90e782a3-1f30-4bcf-8f35-64c3587eeac0">
<path style="stroke:none;" d="M 1.75 -2.53125 C 1.832031 -1.8125 2.160156 -1.316406 2.734375 -1.046875 C 3.035156 -0.910156 3.378906 -0.84375 3.765625 -0.84375 C 4.503906 -0.84375 5.050781 -1.078125 5.40625 -1.546875 C 5.757812 -2.015625 5.9375 -2.535156 5.9375 -3.109375 C 5.9375 -3.804688 5.722656 -4.34375 5.296875 -4.71875 C 4.878906 -5.09375 4.375 -5.28125 3.78125 -5.28125 C 3.351562 -5.28125 2.984375 -5.195312 2.671875 -5.03125 C 2.367188 -4.863281 2.109375 -4.632812 1.890625 -4.34375 L 0.8125 -4.40625 L 1.578125 -9.75 L 6.71875 -9.75 L 6.71875 -8.546875 L 2.5 -8.546875 L 2.078125 -5.78125 C 2.304688 -5.957031 2.523438 -6.085938 2.734375 -6.171875 C 3.109375 -6.328125 3.535156 -6.40625 4.015625 -6.40625 C 4.929688 -6.40625 5.703125 -6.113281 6.328125 -5.53125 C 6.960938 -4.945312 7.28125 -4.203125 7.28125 -3.296875 C 7.28125 -2.359375 6.988281 -1.53125 6.40625 -0.8125 C 5.832031 -0.101562 4.910156 0.25 3.640625 0.25 C 2.828125 0.25 2.109375 0.0234375 1.484375 -0.421875 C 0.867188 -0.878906 0.523438 -1.582031 0.453125 -2.53125 Z M 1.75 -2.53125 "/>
</symbol>
<symbol overflow="visible" id="b5d53c6f-1306-4584-ac05-fb68f6244c78">
<path style="stroke:none;" d="M 7.40625 -9.75 L 7.40625 -8.65625 C 7.09375 -8.34375 6.671875 -7.800781 6.140625 -7.03125 C 5.609375 -6.269531 5.140625 -5.445312 4.734375 -4.5625 C 4.335938 -3.695312 4.035156 -2.910156 3.828125 -2.203125 C 3.691406 -1.742188 3.519531 -1.007812 3.3125 0 L 1.9375 0 C 2.25 -1.894531 2.9375 -3.773438 4 -5.640625 C 4.632812 -6.742188 5.300781 -7.691406 6 -8.484375 L 0.515625 -8.484375 L 0.515625 -9.75 Z M 7.40625 -9.75 "/>
</symbol>
<symbol overflow="visible" id="8f5193e2-f3b9-410b-bbc6-6a71810aa7fb">
<path style="stroke:none;" d="M 0.3125 0 L 0.3125 -6.875 L 5.765625 -6.875 L 5.765625 0 Z M 4.90625 -0.859375 L 4.90625 -6.015625 L 1.171875 -6.015625 L 1.171875 -0.859375 Z M 4.90625 -0.859375 "/>
</symbol>
<symbol overflow="visible" id="3470c6f3-0b85-4b01-860d-bdbc05dd8f6c">
<path style="stroke:none;" d="M 5.734375 -6.875 L 5.734375 -6.0625 L 3.421875 -6.0625 L 3.421875 0 L 2.46875 0 L 2.46875 -6.0625 L 0.15625 -6.0625 L 0.15625 -6.875 Z M 5.734375 -6.875 "/>
</symbol>
<symbol overflow="visible" id="b26c1a42-a043-44ec-b9e7-79133c09713f">
<path style="stroke:none;" d="M 0.625 -6.90625 L 1.46875 -6.90625 L 1.46875 -4.34375 C 1.664062 -4.59375 1.84375 -4.769531 2 -4.875 C 2.269531 -5.050781 2.609375 -5.140625 3.015625 -5.140625 C 3.742188 -5.140625 4.238281 -4.882812 4.5 -4.375 C 4.632812 -4.09375 4.703125 -3.707031 4.703125 -3.21875 L 4.703125 0 L 3.84375 0 L 3.84375 -3.15625 C 3.84375 -3.53125 3.796875 -3.800781 3.703125 -3.96875 C 3.546875 -4.25 3.257812 -4.390625 2.84375 -4.390625 C 2.488281 -4.390625 2.171875 -4.265625 1.890625 -4.015625 C 1.609375 -3.773438 1.46875 -3.320312 1.46875 -2.65625 L 1.46875 0 L 0.625 0 Z M 0.625 -6.90625 "/>
</symbol>
<symbol overflow="visible" id="da236015-4f05-4e75-b55a-e50665299475">
<path style="stroke:none;" d="M 2.703125 -5.125 C 3.054688 -5.125 3.398438 -5.039062 3.734375 -4.875 C 4.078125 -4.707031 4.332031 -4.492188 4.5 -4.234375 C 4.675781 -3.972656 4.789062 -3.675781 4.84375 -3.34375 C 4.894531 -3.113281 4.921875 -2.742188 4.921875 -2.234375 L 1.234375 -2.234375 C 1.253906 -1.722656 1.375 -1.3125 1.59375 -1 C 1.820312 -0.695312 2.171875 -0.546875 2.640625 -0.546875 C 3.085938 -0.546875 3.441406 -0.691406 3.703125 -0.984375 C 3.847656 -1.148438 3.953125 -1.347656 4.015625 -1.578125 L 4.84375 -1.578125 C 4.820312 -1.390625 4.75 -1.179688 4.625 -0.953125 C 4.507812 -0.734375 4.375 -0.550781 4.21875 -0.40625 C 3.957031 -0.15625 3.640625 0.015625 3.265625 0.109375 C 3.054688 0.148438 2.828125 0.171875 2.578125 0.171875 C 1.953125 0.171875 1.421875 -0.0507812 0.984375 -0.5 C 0.554688 -0.957031 0.34375 -1.59375 0.34375 -2.40625 C 0.34375 -3.21875 0.5625 -3.875 1 -4.375 C 1.4375 -4.875 2.003906 -5.125 2.703125 -5.125 Z M 4.046875 -2.90625 C 4.015625 -3.269531 3.9375 -3.5625 3.8125 -3.78125 C 3.582031 -4.1875 3.195312 -4.390625 2.65625 -4.390625 C 2.269531 -4.390625 1.941406 -4.25 1.671875 -3.96875 C 1.410156 -3.695312 1.273438 -3.34375 1.265625 -2.90625 Z M 2.625 -5.140625 Z M 2.625 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="cca3da42-6d2e-4ffd-9eef-e82f1a98c6aa">
<path style="stroke:none;" d=""/>
</symbol>
<symbol overflow="visible" id="23db89a6-74b4-4c07-925a-3c1008a61fcd">
<path style="stroke:none;" d="M 3.3125 -3.96875 C 3.707031 -3.96875 4.015625 -4.023438 4.234375 -4.140625 C 4.578125 -4.304688 4.75 -4.613281 4.75 -5.0625 C 4.75 -5.507812 4.566406 -5.8125 4.203125 -5.96875 C 3.992188 -6.0625 3.6875 -6.109375 3.28125 -6.109375 L 1.625 -6.109375 L 1.625 -3.96875 Z M 3.625 -0.796875 C 4.195312 -0.796875 4.609375 -0.960938 4.859375 -1.296875 C 5.003906 -1.503906 5.078125 -1.753906 5.078125 -2.046875 C 5.078125 -2.546875 4.851562 -2.890625 4.40625 -3.078125 C 4.175781 -3.171875 3.863281 -3.21875 3.46875 -3.21875 L 1.625 -3.21875 L 1.625 -0.796875 Z M 0.703125 -6.875 L 3.65625 -6.875 C 4.46875 -6.875 5.039062 -6.632812 5.375 -6.15625 C 5.582031 -5.875 5.6875 -5.546875 5.6875 -5.171875 C 5.6875 -4.742188 5.5625 -4.390625 5.3125 -4.109375 C 5.1875 -3.960938 5.003906 -3.828125 4.765625 -3.703125 C 5.109375 -3.566406 5.367188 -3.414062 5.546875 -3.25 C 5.859375 -2.945312 6.015625 -2.535156 6.015625 -2.015625 C 6.015625 -1.566406 5.875 -1.164062 5.59375 -0.8125 C 5.175781 -0.269531 4.515625 0 3.609375 0 L 0.703125 0 Z M 0.703125 -6.875 "/>
</symbol>
<symbol overflow="visible" id="472f71a9-c861-426f-a717-37a4d6cb9166">
<path style="stroke:none;" d="M 2.609375 -0.546875 C 3.171875 -0.546875 3.554688 -0.753906 3.765625 -1.171875 C 3.972656 -1.597656 4.078125 -2.070312 4.078125 -2.59375 C 4.078125 -3.0625 4 -3.441406 3.84375 -3.734375 C 3.601562 -4.191406 3.195312 -4.421875 2.625 -4.421875 C 2.101562 -4.421875 1.722656 -4.222656 1.484375 -3.828125 C 1.253906 -3.441406 1.140625 -2.96875 1.140625 -2.40625 C 1.140625 -1.875 1.253906 -1.429688 1.484375 -1.078125 C 1.722656 -0.722656 2.097656 -0.546875 2.609375 -0.546875 Z M 2.640625 -5.15625 C 3.285156 -5.15625 3.832031 -4.941406 4.28125 -4.515625 C 4.726562 -4.085938 4.953125 -3.453125 4.953125 -2.609375 C 4.953125 -1.804688 4.753906 -1.140625 4.359375 -0.609375 C 3.960938 -0.078125 3.351562 0.1875 2.53125 0.1875 C 1.84375 0.1875 1.296875 -0.046875 0.890625 -0.515625 C 0.484375 -0.984375 0.28125 -1.613281 0.28125 -2.40625 C 0.28125 -3.238281 0.492188 -3.90625 0.921875 -4.40625 C 1.347656 -4.90625 1.921875 -5.15625 2.640625 -5.15625 Z M 2.609375 -5.140625 Z M 2.609375 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="b851e519-c328-4796-9d31-4447688f082c">
<path style="stroke:none;" d="M 0.59375 -6.875 L 1.40625 -6.875 L 1.40625 -2.890625 L 3.578125 -5.015625 L 4.65625 -5.015625 L 2.734375 -3.140625 L 4.765625 0 L 3.6875 0 L 2.125 -2.53125 L 1.40625 -1.890625 L 1.40625 0 L 0.59375 0 Z M 0.59375 -6.875 "/>
</symbol>
<symbol overflow="visible" id="1783cede-06a4-4cf8-aee9-9c782355fe7c">
<path style="stroke:none;" d="M 0.828125 -5.78125 C 0.835938 -6.132812 0.898438 -6.390625 1.015625 -6.546875 C 1.210938 -6.835938 1.59375 -6.984375 2.15625 -6.984375 C 2.207031 -6.984375 2.257812 -6.976562 2.3125 -6.96875 C 2.375 -6.96875 2.4375 -6.96875 2.5 -6.96875 L 2.5 -6.1875 C 2.414062 -6.195312 2.351562 -6.203125 2.3125 -6.203125 C 2.28125 -6.203125 2.242188 -6.203125 2.203125 -6.203125 C 1.953125 -6.203125 1.800781 -6.132812 1.75 -6 C 1.695312 -5.875 1.671875 -5.539062 1.671875 -5 L 2.5 -5 L 2.5 -4.328125 L 1.65625 -4.328125 L 1.65625 0 L 0.828125 0 L 0.828125 -4.328125 L 0.125 -4.328125 L 0.125 -5 L 0.828125 -5 Z M 0.828125 -5.78125 "/>
</symbol>
<symbol overflow="visible" id="56fa9301-c699-4433-959f-2c65fb0acbf1">
<path style="stroke:none;" d="M 0.703125 -6.875 L 2.046875 -6.875 L 4.015625 -1.0625 L 5.984375 -6.875 L 7.296875 -6.875 L 7.296875 0 L 6.421875 0 L 6.421875 -4.0625 C 6.421875 -4.195312 6.421875 -4.425781 6.421875 -4.75 C 6.429688 -5.082031 6.4375 -5.429688 6.4375 -5.796875 L 4.46875 0 L 3.546875 0 L 1.578125 -5.796875 L 1.578125 -5.59375 C 1.578125 -5.425781 1.578125 -5.171875 1.578125 -4.828125 C 1.585938 -4.484375 1.59375 -4.226562 1.59375 -4.0625 L 1.59375 0 L 0.703125 0 Z M 0.703125 -6.875 "/>
</symbol>
<symbol overflow="visible" id="e8d4ce71-ad85-4797-8280-32f5a82cc7e0">
<path style="stroke:none;" d="M 0.640625 -5.015625 L 1.4375 -5.015625 L 1.4375 -4.15625 C 1.507812 -4.320312 1.671875 -4.523438 1.921875 -4.765625 C 2.179688 -5.003906 2.476562 -5.125 2.8125 -5.125 C 2.820312 -5.125 2.847656 -5.125 2.890625 -5.125 C 2.929688 -5.125 2.992188 -5.117188 3.078125 -5.109375 L 3.078125 -4.21875 C 3.023438 -4.226562 2.976562 -4.234375 2.9375 -4.234375 C 2.894531 -4.234375 2.851562 -4.234375 2.8125 -4.234375 C 2.382812 -4.234375 2.054688 -4.097656 1.828125 -3.828125 C 1.597656 -3.554688 1.484375 -3.242188 1.484375 -2.890625 L 1.484375 0 L 0.640625 0 Z M 0.640625 -5.015625 "/>
</symbol>
<symbol overflow="visible" id="921cbd9d-11a0-49bf-ad00-851e32754d05">
<path style="stroke:none;" d="M 0.625 -5.015625 L 1.453125 -5.015625 L 1.453125 -4.3125 C 1.648438 -4.550781 1.832031 -4.726562 2 -4.84375 C 2.269531 -5.03125 2.582031 -5.125 2.9375 -5.125 C 3.34375 -5.125 3.664062 -5.023438 3.90625 -4.828125 C 4.039062 -4.722656 4.164062 -4.5625 4.28125 -4.34375 C 4.46875 -4.601562 4.6875 -4.796875 4.9375 -4.921875 C 5.195312 -5.054688 5.484375 -5.125 5.796875 -5.125 C 6.472656 -5.125 6.929688 -4.882812 7.171875 -4.40625 C 7.304688 -4.132812 7.375 -3.78125 7.375 -3.34375 L 7.375 0 L 6.5 0 L 6.5 -3.484375 C 6.5 -3.816406 6.410156 -4.046875 6.234375 -4.171875 C 6.066406 -4.296875 5.863281 -4.359375 5.625 -4.359375 C 5.300781 -4.359375 5.019531 -4.25 4.78125 -4.03125 C 4.539062 -3.8125 4.421875 -3.441406 4.421875 -2.921875 L 4.421875 0 L 3.5625 0 L 3.5625 -3.28125 C 3.5625 -3.613281 3.519531 -3.859375 3.4375 -4.015625 C 3.3125 -4.253906 3.070312 -4.375 2.71875 -4.375 C 2.40625 -4.375 2.117188 -4.25 1.859375 -4 C 1.597656 -3.75 1.46875 -3.300781 1.46875 -2.65625 L 1.46875 0 L 0.625 0 Z M 0.625 -5.015625 "/>
</symbol>
<symbol overflow="visible" id="e875e0de-1db8-45c2-abea-1323413b5c3c">
<path style="stroke:none;" d="M 0.625 -5.015625 L 1.421875 -5.015625 L 1.421875 -4.3125 C 1.660156 -4.601562 1.910156 -4.8125 2.171875 -4.9375 C 2.441406 -5.0625 2.738281 -5.125 3.0625 -5.125 C 3.769531 -5.125 4.25 -4.878906 4.5 -4.390625 C 4.632812 -4.117188 4.703125 -3.726562 4.703125 -3.21875 L 4.703125 0 L 3.84375 0 L 3.84375 -3.15625 C 3.84375 -3.46875 3.800781 -3.71875 3.71875 -3.90625 C 3.5625 -4.21875 3.289062 -4.375 2.90625 -4.375 C 2.695312 -4.375 2.53125 -4.351562 2.40625 -4.3125 C 2.175781 -4.238281 1.972656 -4.097656 1.796875 -3.890625 C 1.660156 -3.734375 1.570312 -3.566406 1.53125 -3.390625 C 1.488281 -3.210938 1.46875 -2.957031 1.46875 -2.625 L 1.46875 0 L 0.625 0 Z M 2.59375 -5.140625 Z M 2.59375 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="8579ee81-b9bd-4c80-a452-d9fa6553002b">
<path style="stroke:none;" d="M 3.375 -0.796875 C 3.6875 -0.796875 3.945312 -0.828125 4.15625 -0.890625 C 4.507812 -1.015625 4.804688 -1.25 5.046875 -1.59375 C 5.222656 -1.875 5.351562 -2.234375 5.4375 -2.671875 C 5.488281 -2.921875 5.515625 -3.160156 5.515625 -3.390625 C 5.515625 -4.242188 5.34375 -4.90625 5 -5.375 C 4.664062 -5.84375 4.117188 -6.078125 3.359375 -6.078125 L 1.703125 -6.078125 L 1.703125 -0.796875 Z M 0.765625 -6.875 L 3.5625 -6.875 C 4.507812 -6.875 5.242188 -6.539062 5.765625 -5.875 C 6.222656 -5.269531 6.453125 -4.492188 6.453125 -3.546875 C 6.453125 -2.816406 6.316406 -2.15625 6.046875 -1.5625 C 5.566406 -0.519531 4.734375 0 3.546875 0 L 0.765625 0 Z M 0.765625 -6.875 "/>
</symbol>
<symbol overflow="visible" id="89c820be-4113-4fe3-ae87-8a2c4e686d38">
<path style="stroke:none;" d="M 1.265625 -1.328125 C 1.265625 -1.085938 1.351562 -0.894531 1.53125 -0.75 C 1.707031 -0.613281 1.921875 -0.546875 2.171875 -0.546875 C 2.460938 -0.546875 2.75 -0.613281 3.03125 -0.75 C 3.5 -0.976562 3.734375 -1.351562 3.734375 -1.875 L 3.734375 -2.546875 C 3.628906 -2.484375 3.492188 -2.429688 3.328125 -2.390625 C 3.171875 -2.347656 3.015625 -2.316406 2.859375 -2.296875 L 2.34375 -2.234375 C 2.039062 -2.191406 1.8125 -2.125 1.65625 -2.03125 C 1.394531 -1.882812 1.265625 -1.648438 1.265625 -1.328125 Z M 3.3125 -3.046875 C 3.5 -3.066406 3.628906 -3.144531 3.703125 -3.28125 C 3.734375 -3.351562 3.75 -3.460938 3.75 -3.609375 C 3.75 -3.890625 3.644531 -4.09375 3.4375 -4.21875 C 3.238281 -4.351562 2.945312 -4.421875 2.5625 -4.421875 C 2.125 -4.421875 1.8125 -4.304688 1.625 -4.078125 C 1.519531 -3.941406 1.453125 -3.742188 1.421875 -3.484375 L 0.640625 -3.484375 C 0.660156 -4.097656 0.863281 -4.523438 1.25 -4.765625 C 1.632812 -5.015625 2.078125 -5.140625 2.578125 -5.140625 C 3.171875 -5.140625 3.65625 -5.023438 4.03125 -4.796875 C 4.394531 -4.578125 4.578125 -4.226562 4.578125 -3.75 L 4.578125 -0.859375 C 4.578125 -0.773438 4.59375 -0.707031 4.625 -0.65625 C 4.664062 -0.601562 4.742188 -0.578125 4.859375 -0.578125 C 4.890625 -0.578125 4.925781 -0.578125 4.96875 -0.578125 C 5.019531 -0.578125 5.070312 -0.582031 5.125 -0.59375 L 5.125 0.015625 C 5 0.0546875 4.898438 0.0820312 4.828125 0.09375 C 4.765625 0.101562 4.671875 0.109375 4.546875 0.109375 C 4.253906 0.109375 4.046875 0.00390625 3.921875 -0.203125 C 3.847656 -0.304688 3.796875 -0.460938 3.765625 -0.671875 C 3.597656 -0.441406 3.351562 -0.242188 3.03125 -0.078125 C 2.707031 0.0859375 2.351562 0.171875 1.96875 0.171875 C 1.5 0.171875 1.117188 0.03125 0.828125 -0.25 C 0.535156 -0.53125 0.390625 -0.882812 0.390625 -1.3125 C 0.390625 -1.78125 0.53125 -2.140625 0.8125 -2.390625 C 1.101562 -2.648438 1.488281 -2.8125 1.96875 -2.875 Z M 2.609375 -5.140625 Z M 2.609375 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="236b8f59-b648-4fd6-9b73-e091b3b2a2df">
<path style="stroke:none;" d="M 2.734375 -0.5625 C 3.128906 -0.5625 3.457031 -0.726562 3.71875 -1.0625 C 3.976562 -1.394531 4.109375 -1.882812 4.109375 -2.53125 C 4.109375 -2.9375 4.050781 -3.28125 3.9375 -3.5625 C 3.71875 -4.125 3.316406 -4.40625 2.734375 -4.40625 C 2.148438 -4.40625 1.75 -4.109375 1.53125 -3.515625 C 1.414062 -3.203125 1.359375 -2.804688 1.359375 -2.328125 C 1.359375 -1.941406 1.414062 -1.613281 1.53125 -1.34375 C 1.75 -0.820312 2.148438 -0.5625 2.734375 -0.5625 Z M 0.546875 -5 L 1.375 -5 L 1.375 -4.328125 C 1.539062 -4.554688 1.722656 -4.734375 1.921875 -4.859375 C 2.210938 -5.046875 2.546875 -5.140625 2.921875 -5.140625 C 3.492188 -5.140625 3.976562 -4.921875 4.375 -4.484375 C 4.769531 -4.046875 4.96875 -3.425781 4.96875 -2.625 C 4.96875 -1.53125 4.679688 -0.75 4.109375 -0.28125 C 3.742188 0.0195312 3.320312 0.171875 2.84375 0.171875 C 2.46875 0.171875 2.148438 0.0859375 1.890625 -0.078125 C 1.742188 -0.171875 1.578125 -0.332031 1.390625 -0.5625 L 1.390625 2 L 0.546875 2 Z M 0.546875 -5 "/>
</symbol>
<symbol overflow="visible" id="9d55fe1a-240a-4805-a9b3-3bc9e2dbba58">
<path style="stroke:none;" d="M 1.15625 -2.453125 C 1.15625 -1.910156 1.269531 -1.457031 1.5 -1.09375 C 1.726562 -0.738281 2.09375 -0.5625 2.59375 -0.5625 C 2.976562 -0.5625 3.296875 -0.726562 3.546875 -1.0625 C 3.804688 -1.394531 3.9375 -1.875 3.9375 -2.5 C 3.9375 -3.132812 3.804688 -3.601562 3.546875 -3.90625 C 3.285156 -4.21875 2.960938 -4.375 2.578125 -4.375 C 2.148438 -4.375 1.804688 -4.207031 1.546875 -3.875 C 1.285156 -3.550781 1.15625 -3.078125 1.15625 -2.453125 Z M 2.421875 -5.109375 C 2.804688 -5.109375 3.128906 -5.023438 3.390625 -4.859375 C 3.535156 -4.765625 3.703125 -4.601562 3.890625 -4.375 L 3.890625 -6.90625 L 4.703125 -6.90625 L 4.703125 0 L 3.953125 0 L 3.953125 -0.703125 C 3.753906 -0.390625 3.519531 -0.164062 3.25 -0.03125 C 2.976562 0.101562 2.671875 0.171875 2.328125 0.171875 C 1.765625 0.171875 1.28125 -0.0625 0.875 -0.53125 C 0.46875 -1 0.265625 -1.625 0.265625 -2.40625 C 0.265625 -3.132812 0.445312 -3.765625 0.8125 -4.296875 C 1.1875 -4.835938 1.722656 -5.109375 2.421875 -5.109375 Z M 2.421875 -5.109375 "/>
</symbol>
<symbol overflow="visible" id="49bffc41-4d1a-42a9-935f-d42988bb465c">
<path style="stroke:none;" d="M 0.75 -6.875 L 1.703125 -6.875 L 1.703125 -4.03125 L 5.28125 -4.03125 L 5.28125 -6.875 L 6.21875 -6.875 L 6.21875 0 L 5.28125 0 L 5.28125 -3.21875 L 1.703125 -3.21875 L 1.703125 0 L 0.75 0 Z M 0.75 -6.875 "/>
</symbol>
<symbol overflow="visible" id="7402fd65-388e-4149-9a2c-6bfacacd29d8">
<path style="stroke:none;" d="M 0.640625 -6.875 L 1.484375 -6.875 L 1.484375 0 L 0.640625 0 Z M 0.640625 -6.875 "/>
</symbol>
<symbol overflow="visible" id="30ff51f1-6821-4cf6-8447-45d9dfce9100">
<path style="stroke:none;" d="M 3.75 -5.015625 L 4.6875 -5.015625 C 4.5625 -4.691406 4.296875 -3.957031 3.890625 -2.8125 C 3.585938 -1.945312 3.332031 -1.242188 3.125 -0.703125 C 2.632812 0.578125 2.289062 1.359375 2.09375 1.640625 C 1.894531 1.921875 1.550781 2.0625 1.0625 2.0625 C 0.945312 2.0625 0.851562 2.054688 0.78125 2.046875 C 0.71875 2.035156 0.640625 2.015625 0.546875 1.984375 L 0.546875 1.21875 C 0.691406 1.257812 0.796875 1.285156 0.859375 1.296875 C 0.929688 1.304688 0.992188 1.3125 1.046875 1.3125 C 1.203125 1.3125 1.316406 1.285156 1.390625 1.234375 C 1.460938 1.179688 1.523438 1.117188 1.578125 1.046875 C 1.585938 1.015625 1.640625 0.882812 1.734375 0.65625 C 1.835938 0.425781 1.910156 0.253906 1.953125 0.140625 L 0.09375 -5.015625 L 1.046875 -5.015625 L 2.40625 -0.9375 Z M 2.390625 -5.140625 Z M 2.390625 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="8a08c1c5-c983-4856-9fcf-495e884a6d03">
<path style="stroke:none;" d="M 0.625 -5 L 1.46875 -5 L 1.46875 0 L 0.625 0 Z M 0.625 -6.875 L 1.46875 -6.875 L 1.46875 -5.921875 L 0.625 -5.921875 Z M 0.625 -6.875 "/>
</symbol>
<symbol overflow="visible" id="3ee274fc-9233-4f41-b348-f10255625378">
<path style="stroke:none;" d="M 0.546875 -6.90625 L 1.375 -6.90625 L 1.375 -4.40625 C 1.5625 -4.644531 1.78125 -4.828125 2.03125 -4.953125 C 2.289062 -5.078125 2.566406 -5.140625 2.859375 -5.140625 C 3.484375 -5.140625 3.988281 -4.925781 4.375 -4.5 C 4.769531 -4.070312 4.96875 -3.441406 4.96875 -2.609375 C 4.96875 -1.816406 4.773438 -1.15625 4.390625 -0.625 C 4.003906 -0.101562 3.472656 0.15625 2.796875 0.15625 C 2.410156 0.15625 2.085938 0.0664062 1.828125 -0.109375 C 1.671875 -0.222656 1.503906 -0.398438 1.328125 -0.640625 L 1.328125 0 L 0.546875 0 Z M 2.75 -0.578125 C 3.195312 -0.578125 3.535156 -0.757812 3.765625 -1.125 C 3.992188 -1.488281 4.109375 -1.96875 4.109375 -2.5625 C 4.109375 -3.09375 3.992188 -3.53125 3.765625 -3.875 C 3.535156 -4.21875 3.203125 -4.390625 2.765625 -4.390625 C 2.378906 -4.390625 2.039062 -4.25 1.75 -3.96875 C 1.46875 -3.6875 1.328125 -3.21875 1.328125 -2.5625 C 1.328125 -2.09375 1.382812 -1.710938 1.5 -1.421875 C 1.726562 -0.859375 2.144531 -0.578125 2.75 -0.578125 Z M 2.75 -0.578125 "/>
</symbol>
<symbol overflow="visible" id="1319f8ef-6019-4876-bedf-98bd20a90d53">
<path style="stroke:none;" d="M 4.109375 -2.046875 C 4.109375 -1.472656 4.019531 -1.023438 3.84375 -0.703125 C 3.53125 -0.109375 2.925781 0.1875 2.03125 0.1875 C 1.519531 0.1875 1.078125 0.046875 0.703125 -0.234375 C 0.335938 -0.515625 0.15625 -1.015625 0.15625 -1.734375 L 0.15625 -2.21875 L 1.046875 -2.21875 L 1.046875 -1.734375 C 1.046875 -1.359375 1.128906 -1.070312 1.296875 -0.875 C 1.460938 -0.6875 1.722656 -0.59375 2.078125 -0.59375 C 2.566406 -0.59375 2.890625 -0.765625 3.046875 -1.109375 C 3.140625 -1.316406 3.1875 -1.710938 3.1875 -2.296875 L 3.1875 -6.875 L 4.109375 -6.875 Z M 4.109375 -2.046875 "/>
</symbol>
<symbol overflow="visible" id="e47f4888-776c-48dc-af2f-5e5d312efc20">
<path style="stroke:none;" d="M 1 -5.015625 L 1.96875 -1.0625 L 2.953125 -5.015625 L 3.890625 -5.015625 L 4.875 -1.09375 L 5.90625 -5.015625 L 6.75 -5.015625 L 5.296875 0 L 4.421875 0 L 3.390625 -3.890625 L 2.40625 0 L 1.53125 0 L 0.078125 -5.015625 Z M 1 -5.015625 "/>
</symbol>
<symbol overflow="visible" id="d0033be6-8b9b-496f-b53f-222098f35a43">
<path style="stroke:none;" d="M 1.125 -1.578125 C 1.144531 -1.296875 1.210938 -1.078125 1.328125 -0.921875 C 1.546875 -0.648438 1.914062 -0.515625 2.4375 -0.515625 C 2.75 -0.515625 3.019531 -0.582031 3.25 -0.71875 C 3.488281 -0.851562 3.609375 -1.066406 3.609375 -1.359375 C 3.609375 -1.566406 3.515625 -1.726562 3.328125 -1.84375 C 3.203125 -1.914062 2.960938 -1.992188 2.609375 -2.078125 L 1.9375 -2.25 C 1.507812 -2.351562 1.195312 -2.472656 1 -2.609375 C 0.632812 -2.835938 0.453125 -3.15625 0.453125 -3.5625 C 0.453125 -4.03125 0.617188 -4.410156 0.953125 -4.703125 C 1.296875 -4.992188 1.757812 -5.140625 2.34375 -5.140625 C 3.09375 -5.140625 3.640625 -4.921875 3.984375 -4.484375 C 4.191406 -4.203125 4.289062 -3.898438 4.28125 -3.578125 L 3.484375 -3.578125 C 3.472656 -3.765625 3.40625 -3.9375 3.28125 -4.09375 C 3.09375 -4.3125 2.757812 -4.421875 2.28125 -4.421875 C 1.957031 -4.421875 1.710938 -4.359375 1.546875 -4.234375 C 1.390625 -4.117188 1.3125 -3.960938 1.3125 -3.765625 C 1.3125 -3.546875 1.414062 -3.367188 1.625 -3.234375 C 1.75 -3.160156 1.9375 -3.09375 2.1875 -3.03125 L 2.734375 -2.890625 C 3.347656 -2.742188 3.753906 -2.601562 3.953125 -2.46875 C 4.285156 -2.25 4.453125 -1.910156 4.453125 -1.453125 C 4.453125 -1.003906 4.28125 -0.617188 3.9375 -0.296875 C 3.601562 0.0234375 3.085938 0.1875 2.390625 0.1875 C 1.648438 0.1875 1.125 0.0195312 0.8125 -0.3125 C 0.5 -0.65625 0.332031 -1.078125 0.3125 -1.578125 Z M 2.359375 -5.140625 Z M 2.359375 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="751aabe8-b3a9-4afd-8d28-c387bfc78105">
<path style="stroke:none;" d="M 1.34375 -2.21875 C 1.363281 -1.832031 1.453125 -1.515625 1.609375 -1.265625 C 1.921875 -0.804688 2.46875 -0.578125 3.25 -0.578125 C 3.601562 -0.578125 3.921875 -0.628906 4.203125 -0.734375 C 4.765625 -0.929688 5.046875 -1.28125 5.046875 -1.78125 C 5.046875 -2.15625 4.925781 -2.421875 4.6875 -2.578125 C 4.445312 -2.734375 4.078125 -2.867188 3.578125 -2.984375 L 2.640625 -3.1875 C 2.035156 -3.332031 1.601562 -3.488281 1.34375 -3.65625 C 0.90625 -3.9375 0.6875 -4.363281 0.6875 -4.9375 C 0.6875 -5.550781 0.898438 -6.054688 1.328125 -6.453125 C 1.765625 -6.859375 2.375 -7.0625 3.15625 -7.0625 C 3.875 -7.0625 4.484375 -6.882812 4.984375 -6.53125 C 5.492188 -6.1875 5.75 -5.628906 5.75 -4.859375 L 4.875 -4.859375 C 4.820312 -5.234375 4.722656 -5.515625 4.578125 -5.703125 C 4.285156 -6.066406 3.800781 -6.25 3.125 -6.25 C 2.570312 -6.25 2.175781 -6.132812 1.9375 -5.90625 C 1.695312 -5.675781 1.578125 -5.40625 1.578125 -5.09375 C 1.578125 -4.757812 1.71875 -4.515625 2 -4.359375 C 2.1875 -4.253906 2.601562 -4.128906 3.25 -3.984375 L 4.21875 -3.765625 C 4.675781 -3.660156 5.035156 -3.515625 5.296875 -3.328125 C 5.734375 -3.003906 5.953125 -2.535156 5.953125 -1.921875 C 5.953125 -1.160156 5.671875 -0.613281 5.109375 -0.28125 C 4.554688 0.0390625 3.914062 0.203125 3.1875 0.203125 C 2.332031 0.203125 1.660156 -0.015625 1.171875 -0.453125 C 0.691406 -0.890625 0.457031 -1.476562 0.46875 -2.21875 Z M 3.21875 -7.0625 Z M 3.21875 -7.0625 "/>
</symbol>
<symbol overflow="visible" id="593dfe30-034a-4e9f-ac74-4785d1ac3dab">
<path style="stroke:none;" d="M 2.546875 -5.15625 C 3.117188 -5.15625 3.582031 -5.019531 3.9375 -4.75 C 4.289062 -4.476562 4.503906 -4.003906 4.578125 -3.328125 L 3.75 -3.328125 C 3.695312 -3.640625 3.582031 -3.894531 3.40625 -4.09375 C 3.226562 -4.300781 2.941406 -4.40625 2.546875 -4.40625 C 2.015625 -4.40625 1.632812 -4.144531 1.40625 -3.625 C 1.25 -3.28125 1.171875 -2.859375 1.171875 -2.359375 C 1.171875 -1.859375 1.273438 -1.4375 1.484375 -1.09375 C 1.703125 -0.75 2.039062 -0.578125 2.5 -0.578125 C 2.84375 -0.578125 3.117188 -0.679688 3.328125 -0.890625 C 3.535156 -1.109375 3.675781 -1.40625 3.75 -1.78125 L 4.578125 -1.78125 C 4.484375 -1.113281 4.25 -0.625 3.875 -0.3125 C 3.5 -0.0078125 3.019531 0.140625 2.4375 0.140625 C 1.78125 0.140625 1.253906 -0.0976562 0.859375 -0.578125 C 0.472656 -1.054688 0.28125 -1.65625 0.28125 -2.375 C 0.28125 -3.25 0.492188 -3.929688 0.921875 -4.421875 C 1.347656 -4.910156 1.890625 -5.15625 2.546875 -5.15625 Z M 2.421875 -5.140625 Z M 2.421875 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="9ac0766c-2248-411b-ab32-ccc7416332f2">
<path style="stroke:none;" d="M 0.78125 -6.421875 L 1.640625 -6.421875 L 1.640625 -5.015625 L 2.4375 -5.015625 L 2.4375 -4.328125 L 1.640625 -4.328125 L 1.640625 -1.046875 C 1.640625 -0.878906 1.695312 -0.765625 1.8125 -0.703125 C 1.882812 -0.671875 1.992188 -0.65625 2.140625 -0.65625 C 2.179688 -0.65625 2.222656 -0.65625 2.265625 -0.65625 C 2.316406 -0.65625 2.375 -0.660156 2.4375 -0.671875 L 2.4375 0 C 2.34375 0.03125 2.242188 0.0507812 2.140625 0.0625 C 2.035156 0.0703125 1.921875 0.078125 1.796875 0.078125 C 1.398438 0.078125 1.128906 -0.0195312 0.984375 -0.21875 C 0.847656 -0.425781 0.78125 -0.6875 0.78125 -1 L 0.78125 -4.328125 L 0.109375 -4.328125 L 0.109375 -5.015625 L 0.78125 -5.015625 Z M 0.78125 -6.421875 "/>
</symbol>
<symbol overflow="visible" id="c558b5ce-0bcf-4ba7-8953-cefe3a7cdbc1">
<path style="stroke:none;" d="M 1.46875 -5.015625 L 1.46875 -1.6875 C 1.46875 -1.425781 1.503906 -1.21875 1.578125 -1.0625 C 1.734375 -0.757812 2.015625 -0.609375 2.421875 -0.609375 C 3.003906 -0.609375 3.40625 -0.867188 3.625 -1.390625 C 3.738281 -1.671875 3.796875 -2.054688 3.796875 -2.546875 L 3.796875 -5.015625 L 4.640625 -5.015625 L 4.640625 0 L 3.84375 0 L 3.84375 -0.734375 C 3.738281 -0.546875 3.601562 -0.382812 3.4375 -0.25 C 3.113281 0.0078125 2.722656 0.140625 2.265625 0.140625 C 1.554688 0.140625 1.070312 -0.0976562 0.8125 -0.578125 C 0.664062 -0.835938 0.59375 -1.179688 0.59375 -1.609375 L 0.59375 -5.015625 Z M 2.625 -5.140625 Z M 2.625 -5.140625 "/>
</symbol>
<symbol overflow="visible" id="ba8c8c17-3a09-47a6-8884-ccee5f701981">
<path style="stroke:none;" d="M 0.734375 -6.875 L 1.640625 -6.875 L 1.640625 -3.53125 L 5 -6.875 L 6.28125 -6.875 L 3.421875 -4.109375 L 6.359375 0 L 5.140625 0 L 2.734375 -3.453125 L 1.640625 -2.40625 L 1.640625 0 L 0.734375 0 Z M 0.734375 -6.875 "/>
</symbol>
<symbol overflow="visible" id="f7b4a3ba-5620-49a2-ae14-3e8aff9d8d30">
<path style="stroke:none;" d="M 1.28125 -6.875 L 3.25 -1.015625 L 5.203125 -6.875 L 6.25 -6.875 L 3.734375 0 L 2.75 0 L 0.25 -6.875 Z M 1.28125 -6.875 "/>
</symbol>
<symbol overflow="visible" id="5039ce22-22e0-4562-89e3-d11738723d58">
<path style="stroke:none;" d="M 2.59375 -6.703125 C 3.457031 -6.703125 4.085938 -6.347656 4.484375 -5.640625 C 4.773438 -5.085938 4.921875 -4.328125 4.921875 -3.359375 C 4.921875 -2.453125 4.785156 -1.695312 4.515625 -1.09375 C 4.128906 -0.238281 3.488281 0.1875 2.59375 0.1875 C 1.78125 0.1875 1.179688 -0.160156 0.796875 -0.859375 C 0.460938 -1.453125 0.296875 -2.238281 0.296875 -3.21875 C 0.296875 -3.976562 0.394531 -4.632812 0.59375 -5.1875 C 0.957031 -6.195312 1.625 -6.703125 2.59375 -6.703125 Z M 2.578125 -0.578125 C 3.015625 -0.578125 3.363281 -0.769531 3.625 -1.15625 C 3.882812 -1.550781 4.015625 -2.273438 4.015625 -3.328125 C 4.015625 -4.085938 3.921875 -4.710938 3.734375 -5.203125 C 3.546875 -5.703125 3.179688 -5.953125 2.640625 -5.953125 C 2.148438 -5.953125 1.789062 -5.71875 1.5625 -5.25 C 1.332031 -4.78125 1.21875 -4.09375 1.21875 -3.1875 C 1.21875 -2.5 1.289062 -1.945312 1.4375 -1.53125 C 1.65625 -0.894531 2.035156 -0.578125 2.578125 -0.578125 Z M 2.578125 -0.578125 "/>
</symbol>
<symbol overflow="visible" id="bcfa4c4b-5db3-4b3a-9a97-6d6ee4583166">
<path style="stroke:none;" d="M 0.8125 -1.015625 L 1.796875 -1.015625 L 1.796875 0 L 0.8125 0 Z M 0.8125 -1.015625 "/>
</symbol>
<symbol overflow="visible" id="93ce07d2-2689-42c9-9dcd-d488b0c849e9">
<path style="stroke:none;" d="M 1.1875 -1.703125 C 1.238281 -1.222656 1.460938 -0.894531 1.859375 -0.71875 C 2.054688 -0.625 2.285156 -0.578125 2.546875 -0.578125 C 3.046875 -0.578125 3.414062 -0.734375 3.65625 -1.046875 C 3.894531 -1.367188 4.015625 -1.722656 4.015625 -2.109375 C 4.015625 -2.578125 3.867188 -2.9375 3.578125 -3.1875 C 3.296875 -3.445312 2.957031 -3.578125 2.5625 -3.578125 C 2.269531 -3.578125 2.019531 -3.519531 1.8125 -3.40625 C 1.601562 -3.289062 1.425781 -3.132812 1.28125 -2.9375 L 0.546875 -2.984375 L 1.0625 -6.59375 L 4.546875 -6.59375 L 4.546875 -5.78125 L 1.703125 -5.78125 L 1.40625 -3.921875 C 1.5625 -4.035156 1.710938 -4.125 1.859375 -4.1875 C 2.109375 -4.289062 2.394531 -4.34375 2.71875 -4.34375 C 3.332031 -4.34375 3.851562 -4.140625 4.28125 -3.734375 C 4.707031 -3.335938 4.921875 -2.835938 4.921875 -2.234375 C 4.921875 -1.597656 4.722656 -1.035156 4.328125 -0.546875 C 3.941406 -0.0664062 3.320312 0.171875 2.46875 0.171875 C 1.914062 0.171875 1.429688 0.0195312 1.015625 -0.28125 C 0.597656 -0.59375 0.363281 -1.066406 0.3125 -1.703125 Z M 1.1875 -1.703125 "/>
</symbol>
<symbol overflow="visible" id="582db5fa-97c7-4c0c-b1dc-a1af63b32474">
<path style="stroke:none;" d="M 0.921875 -4.75 L 0.921875 -5.390625 C 1.523438 -5.453125 1.945312 -5.550781 2.1875 -5.6875 C 2.425781 -5.832031 2.609375 -6.164062 2.734375 -6.6875 L 3.390625 -6.6875 L 3.390625 0 L 2.5 0 L 2.5 -4.75 Z M 0.921875 -4.75 "/>
</symbol>
<symbol overflow="visible" id="7a39b0d7-7696-47ab-893a-a4dd1bb55ce0">
<path style="stroke:none;" d="M 0.296875 0 C 0.328125 -0.570312 0.445312 -1.070312 0.65625 -1.5 C 0.863281 -1.9375 1.269531 -2.328125 1.875 -2.671875 L 2.765625 -3.1875 C 3.171875 -3.425781 3.457031 -3.628906 3.625 -3.796875 C 3.875 -4.054688 4 -4.351562 4 -4.6875 C 4 -5.070312 3.878906 -5.378906 3.640625 -5.609375 C 3.410156 -5.835938 3.101562 -5.953125 2.71875 -5.953125 C 2.132812 -5.953125 1.734375 -5.734375 1.515625 -5.296875 C 1.398438 -5.066406 1.335938 -4.742188 1.328125 -4.328125 L 0.46875 -4.328125 C 0.476562 -4.910156 0.582031 -5.382812 0.78125 -5.75 C 1.144531 -6.40625 1.789062 -6.734375 2.71875 -6.734375 C 3.488281 -6.734375 4.050781 -6.523438 4.40625 -6.109375 C 4.757812 -5.691406 4.9375 -5.226562 4.9375 -4.71875 C 4.9375 -4.1875 4.75 -3.726562 4.375 -3.34375 C 4.15625 -3.125 3.757812 -2.851562 3.1875 -2.53125 L 2.546875 -2.1875 C 2.242188 -2.019531 2.003906 -1.859375 1.828125 -1.703125 C 1.515625 -1.429688 1.316406 -1.128906 1.234375 -0.796875 L 4.90625 -0.796875 L 4.90625 0 Z M 0.296875 0 "/>
</symbol>
<symbol overflow="visible" id="7062d94c-a6ce-4b90-bc9a-101a3892adae">
<path style="stroke:none;" d="M 0.46875 0 L 0.46875 -10.328125 L 8.671875 -10.328125 L 8.671875 0 Z M 7.375 -1.296875 L 7.375 -9.046875 L 1.765625 -9.046875 L 1.765625 -1.296875 Z M 7.375 -1.296875 "/>
</symbol>
<symbol overflow="visible" id="e03234d3-5563-4c9e-9e94-ab651a0ff896">
<path style="stroke:none;" d="M 2.015625 -3.328125 C 2.046875 -2.742188 2.179688 -2.269531 2.421875 -1.90625 C 2.890625 -1.21875 3.707031 -0.875 4.875 -0.875 C 5.40625 -0.875 5.882812 -0.953125 6.3125 -1.109375 C 7.144531 -1.398438 7.5625 -1.921875 7.5625 -2.671875 C 7.5625 -3.234375 7.390625 -3.632812 7.046875 -3.875 C 6.679688 -4.101562 6.117188 -4.304688 5.359375 -4.484375 L 3.96875 -4.796875 C 3.050781 -5.003906 2.40625 -5.234375 2.03125 -5.484375 C 1.375 -5.910156 1.046875 -6.554688 1.046875 -7.421875 C 1.046875 -8.347656 1.363281 -9.109375 2 -9.703125 C 2.644531 -10.296875 3.554688 -10.59375 4.734375 -10.59375 C 5.816406 -10.59375 6.734375 -10.332031 7.484375 -9.8125 C 8.242188 -9.289062 8.625 -8.453125 8.625 -7.296875 L 7.3125 -7.296875 C 7.238281 -7.847656 7.085938 -8.273438 6.859375 -8.578125 C 6.429688 -9.117188 5.707031 -9.390625 4.6875 -9.390625 C 3.863281 -9.390625 3.269531 -9.210938 2.90625 -8.859375 C 2.550781 -8.515625 2.375 -8.113281 2.375 -7.65625 C 2.375 -7.144531 2.582031 -6.773438 3 -6.546875 C 3.28125 -6.390625 3.90625 -6.203125 4.875 -5.984375 L 6.328125 -5.65625 C 7.023438 -5.488281 7.566406 -5.269531 7.953125 -5 C 8.609375 -4.507812 8.9375 -3.804688 8.9375 -2.890625 C 8.9375 -1.742188 8.519531 -0.925781 7.6875 -0.4375 C 6.851562 0.0507812 5.882812 0.296875 4.78125 0.296875 C 3.5 0.296875 2.492188 -0.03125 1.765625 -0.6875 C 1.035156 -1.332031 0.679688 -2.210938 0.703125 -3.328125 Z M 4.84375 -10.609375 Z M 4.84375 -10.609375 "/>
</symbol>
<symbol overflow="visible" id="447692ed-73a1-4243-8986-dbebf6acf672">
<path style="stroke:none;" d="M 4.0625 -7.703125 C 4.601562 -7.703125 5.125 -7.578125 5.625 -7.328125 C 6.125 -7.078125 6.503906 -6.753906 6.765625 -6.359375 C 7.015625 -5.972656 7.1875 -5.523438 7.28125 -5.015625 C 7.351562 -4.671875 7.390625 -4.117188 7.390625 -3.359375 L 1.859375 -3.359375 C 1.890625 -2.597656 2.070312 -1.984375 2.40625 -1.515625 C 2.738281 -1.054688 3.257812 -0.828125 3.96875 -0.828125 C 4.632812 -0.828125 5.164062 -1.046875 5.5625 -1.484375 C 5.78125 -1.734375 5.9375 -2.023438 6.03125 -2.359375 L 7.28125 -2.359375 C 7.25 -2.085938 7.140625 -1.78125 6.953125 -1.4375 C 6.765625 -1.09375 6.554688 -0.816406 6.328125 -0.609375 C 5.941406 -0.234375 5.46875 0.0195312 4.90625 0.15625 C 4.601562 0.226562 4.257812 0.265625 3.875 0.265625 C 2.9375 0.265625 2.140625 -0.0703125 1.484375 -0.75 C 0.828125 -1.4375 0.5 -2.394531 0.5 -3.625 C 0.5 -4.832031 0.828125 -5.8125 1.484375 -6.5625 C 2.140625 -7.320312 3 -7.703125 4.0625 -7.703125 Z M 6.078125 -4.375 C 6.023438 -4.914062 5.90625 -5.351562 5.71875 -5.6875 C 5.375 -6.289062 4.796875 -6.59375 3.984375 -6.59375 C 3.398438 -6.59375 2.910156 -6.382812 2.515625 -5.96875 C 2.128906 -5.550781 1.925781 -5.019531 1.90625 -4.375 Z M 3.953125 -7.71875 Z M 3.953125 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="ccab89b2-6678-4318-abfb-2408b8ac0cb3">
<path style="stroke:none;" d="M 0.921875 -7.53125 L 2.125 -7.53125 L 2.125 -6.46875 C 2.488281 -6.90625 2.867188 -7.21875 3.265625 -7.40625 C 3.660156 -7.601562 4.101562 -7.703125 4.59375 -7.703125 C 5.664062 -7.703125 6.390625 -7.328125 6.765625 -6.578125 C 6.960938 -6.171875 7.0625 -5.585938 7.0625 -4.828125 L 7.0625 0 L 5.78125 0 L 5.78125 -4.75 C 5.78125 -5.207031 5.710938 -5.578125 5.578125 -5.859375 C 5.347656 -6.328125 4.941406 -6.5625 4.359375 -6.5625 C 4.054688 -6.5625 3.804688 -6.53125 3.609375 -6.46875 C 3.265625 -6.363281 2.960938 -6.160156 2.703125 -5.859375 C 2.492188 -5.609375 2.351562 -5.347656 2.28125 -5.078125 C 2.21875 -4.816406 2.1875 -4.441406 2.1875 -3.953125 L 2.1875 0 L 0.921875 0 Z M 3.90625 -7.71875 Z M 3.90625 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="0feb9142-88a3-4b62-b8ad-3fbb86e81a1f">
<path style="stroke:none;" d="M 1.1875 -9.640625 L 2.46875 -9.640625 L 2.46875 -7.53125 L 3.671875 -7.53125 L 3.671875 -6.5 L 2.46875 -6.5 L 2.46875 -1.578125 C 2.46875 -1.316406 2.554688 -1.144531 2.734375 -1.0625 C 2.828125 -1.007812 2.988281 -0.984375 3.21875 -0.984375 C 3.28125 -0.984375 3.347656 -0.984375 3.421875 -0.984375 C 3.492188 -0.984375 3.578125 -0.988281 3.671875 -1 L 3.671875 0 C 3.523438 0.0390625 3.367188 0.0703125 3.203125 0.09375 C 3.046875 0.113281 2.878906 0.125 2.703125 0.125 C 2.109375 0.125 1.707031 -0.0234375 1.5 -0.328125 C 1.289062 -0.628906 1.1875 -1.023438 1.1875 -1.515625 L 1.1875 -6.5 L 0.15625 -6.5 L 0.15625 -7.53125 L 1.1875 -7.53125 Z M 1.1875 -9.640625 "/>
</symbol>
<symbol overflow="visible" id="879fe346-e42a-4f0d-a051-861200576f06">
<path style="stroke:none;" d="M 3.828125 -7.75 C 4.679688 -7.75 5.375 -7.539062 5.90625 -7.125 C 6.4375 -6.71875 6.753906 -6.007812 6.859375 -5 L 5.640625 -5 C 5.554688 -5.46875 5.378906 -5.851562 5.109375 -6.15625 C 4.847656 -6.46875 4.421875 -6.625 3.828125 -6.625 C 3.023438 -6.625 2.453125 -6.226562 2.109375 -5.4375 C 1.878906 -4.925781 1.765625 -4.296875 1.765625 -3.546875 C 1.765625 -2.785156 1.921875 -2.144531 2.234375 -1.625 C 2.554688 -1.113281 3.0625 -0.859375 3.75 -0.859375 C 4.269531 -0.859375 4.679688 -1.019531 4.984375 -1.34375 C 5.296875 -1.664062 5.515625 -2.109375 5.640625 -2.671875 L 6.859375 -2.671875 C 6.722656 -1.671875 6.375 -0.9375 5.8125 -0.46875 C 5.25 -0.0078125 4.53125 0.21875 3.65625 0.21875 C 2.664062 0.21875 1.878906 -0.140625 1.296875 -0.859375 C 0.710938 -1.578125 0.421875 -2.476562 0.421875 -3.5625 C 0.421875 -4.882812 0.738281 -5.910156 1.375 -6.640625 C 2.019531 -7.378906 2.835938 -7.75 3.828125 -7.75 Z M 3.640625 -7.71875 Z M 3.640625 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="1faf4588-b445-4fac-84ef-d1460e0581be">
<path style="stroke:none;" d="M 1.6875 -2.359375 C 1.71875 -1.941406 1.820312 -1.617188 2 -1.390625 C 2.3125 -0.984375 2.863281 -0.78125 3.65625 -0.78125 C 4.125 -0.78125 4.535156 -0.878906 4.890625 -1.078125 C 5.253906 -1.285156 5.4375 -1.601562 5.4375 -2.03125 C 5.4375 -2.351562 5.289062 -2.597656 5 -2.765625 C 4.820312 -2.867188 4.460938 -2.988281 3.921875 -3.125 L 2.90625 -3.390625 C 2.269531 -3.546875 1.796875 -3.722656 1.484375 -3.921875 C 0.941406 -4.265625 0.671875 -4.738281 0.671875 -5.34375 C 0.671875 -6.050781 0.925781 -6.625 1.4375 -7.0625 C 1.957031 -7.507812 2.648438 -7.734375 3.515625 -7.734375 C 4.648438 -7.734375 5.46875 -7.398438 5.96875 -6.734375 C 6.28125 -6.304688 6.429688 -5.847656 6.421875 -5.359375 L 5.234375 -5.359375 C 5.210938 -5.648438 5.113281 -5.910156 4.9375 -6.140625 C 4.644531 -6.472656 4.140625 -6.640625 3.421875 -6.640625 C 2.941406 -6.640625 2.578125 -6.546875 2.328125 -6.359375 C 2.085938 -6.179688 1.96875 -5.945312 1.96875 -5.65625 C 1.96875 -5.320312 2.128906 -5.054688 2.453125 -4.859375 C 2.640625 -4.742188 2.914062 -4.640625 3.28125 -4.546875 L 4.109375 -4.34375 C 5.023438 -4.125 5.632812 -3.910156 5.9375 -3.703125 C 6.4375 -3.378906 6.6875 -2.875 6.6875 -2.1875 C 6.6875 -1.507812 6.429688 -0.925781 5.921875 -0.4375 C 5.410156 0.0390625 4.632812 0.28125 3.59375 0.28125 C 2.46875 0.28125 1.671875 0.03125 1.203125 -0.46875 C 0.742188 -0.976562 0.5 -1.609375 0.46875 -2.359375 Z M 3.546875 -7.71875 Z M 3.546875 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="0f0f1cb2-459e-45de-bbcf-831f62b4cd8b">
<path style="stroke:none;" d=""/>
</symbol>
<symbol overflow="visible" id="805fb74a-270c-442a-8e0d-becc58351575">
<path style="stroke:none;" d="M 1.515625 -7.53125 L 2.96875 -1.59375 L 4.4375 -7.53125 L 5.859375 -7.53125 L 7.328125 -1.625 L 8.875 -7.53125 L 10.140625 -7.53125 L 7.953125 0 L 6.640625 0 L 5.09375 -5.828125 L 3.609375 0 L 2.296875 0 L 0.125 -7.53125 Z M 1.515625 -7.53125 "/>
</symbol>
<symbol overflow="visible" id="2b4932ec-c76d-4093-9e07-a78a6b763e4c">
<path style="stroke:none;" d="M 0.921875 -7.5 L 2.21875 -7.5 L 2.21875 0 L 0.921875 0 Z M 0.921875 -10.328125 L 2.21875 -10.328125 L 2.21875 -8.890625 L 0.921875 -8.890625 Z M 0.921875 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="279ab554-4d29-4bae-bd0d-a141f399a5aa">
<path style="stroke:none;" d="M 0.921875 -10.375 L 2.1875 -10.375 L 2.1875 -6.515625 C 2.488281 -6.890625 2.757812 -7.15625 3 -7.3125 C 3.40625 -7.582031 3.914062 -7.71875 4.53125 -7.71875 C 5.625 -7.71875 6.363281 -7.332031 6.75 -6.5625 C 6.957031 -6.144531 7.0625 -5.566406 7.0625 -4.828125 L 7.0625 0 L 5.765625 0 L 5.765625 -4.75 C 5.765625 -5.300781 5.695312 -5.707031 5.5625 -5.96875 C 5.332031 -6.375 4.898438 -6.578125 4.265625 -6.578125 C 3.734375 -6.578125 3.253906 -6.394531 2.828125 -6.03125 C 2.398438 -5.675781 2.1875 -5 2.1875 -4 L 2.1875 0 L 0.921875 0 Z M 0.921875 -10.375 "/>
</symbol>
<symbol overflow="visible" id="2115e1d0-b396-4868-9323-65f817d761a7">
<path style="stroke:none;" d="M 1.90625 -10.328125 L 4.875 -1.53125 L 7.8125 -10.328125 L 9.390625 -10.328125 L 5.609375 0 L 4.125 0 L 0.359375 -10.328125 Z M 1.90625 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="916c655f-f46c-4a6e-9996-613dceded33d">
<path style="stroke:none;" d="M 3.921875 -0.8125 C 4.753906 -0.8125 5.328125 -1.128906 5.640625 -1.765625 C 5.953125 -2.398438 6.109375 -3.109375 6.109375 -3.890625 C 6.109375 -4.585938 6 -5.160156 5.78125 -5.609375 C 5.414062 -6.296875 4.800781 -6.640625 3.9375 -6.640625 C 3.15625 -6.640625 2.585938 -6.34375 2.234375 -5.75 C 1.890625 -5.164062 1.71875 -4.457031 1.71875 -3.625 C 1.71875 -2.820312 1.890625 -2.148438 2.234375 -1.609375 C 2.585938 -1.078125 3.148438 -0.8125 3.921875 -0.8125 Z M 3.96875 -7.75 C 4.9375 -7.75 5.753906 -7.425781 6.421875 -6.78125 C 7.097656 -6.132812 7.4375 -5.179688 7.4375 -3.921875 C 7.4375 -2.710938 7.140625 -1.707031 6.546875 -0.90625 C 5.953125 -0.113281 5.035156 0.28125 3.796875 0.28125 C 2.765625 0.28125 1.941406 -0.0664062 1.328125 -0.765625 C 0.722656 -1.472656 0.421875 -2.421875 0.421875 -3.609375 C 0.421875 -4.867188 0.738281 -5.875 1.375 -6.625 C 2.019531 -7.375 2.882812 -7.75 3.96875 -7.75 Z M 3.921875 -7.71875 Z M 3.921875 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="707767d8-31a8-4231-b194-4f39f3e20acb">
<path style="stroke:none;" d="M 0.96875 -10.328125 L 2.234375 -10.328125 L 2.234375 0 L 0.96875 0 Z M 0.96875 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="12f3b784-13f3-486c-935a-920840bdf033">
<path style="stroke:none;" d="M 1.78125 -10.328125 L 3.734375 -1.921875 L 6.0625 -10.328125 L 7.578125 -10.328125 L 9.921875 -1.921875 L 11.859375 -10.328125 L 13.40625 -10.328125 L 10.6875 0 L 9.21875 0 L 6.828125 -8.5625 L 4.4375 0 L 2.96875 0 L 0.265625 -10.328125 Z M 1.78125 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="339f1cb6-5154-45ab-8870-ac939b3c1394">
<path style="stroke:none;" d="M 0.96875 -7.53125 L 2.171875 -7.53125 L 2.171875 -6.234375 C 2.265625 -6.484375 2.503906 -6.789062 2.890625 -7.15625 C 3.273438 -7.519531 3.71875 -7.703125 4.21875 -7.703125 C 4.238281 -7.703125 4.273438 -7.695312 4.328125 -7.6875 C 4.390625 -7.6875 4.488281 -7.679688 4.625 -7.671875 L 4.625 -6.328125 C 4.550781 -6.347656 4.484375 -6.359375 4.421875 -6.359375 C 4.359375 -6.359375 4.289062 -6.359375 4.21875 -6.359375 C 3.570312 -6.359375 3.078125 -6.15625 2.734375 -5.75 C 2.398438 -5.34375 2.234375 -4.867188 2.234375 -4.328125 L 2.234375 0 L 0.96875 0 Z M 0.96875 -7.53125 "/>
</symbol>
<symbol overflow="visible" id="3d0bee46-6355-40d4-86aa-ff9c51e3d752">
<path style="stroke:none;" d="M 1.734375 -3.671875 C 1.734375 -2.867188 1.898438 -2.195312 2.234375 -1.65625 C 2.578125 -1.113281 3.128906 -0.84375 3.890625 -0.84375 C 4.472656 -0.84375 4.953125 -1.09375 5.328125 -1.59375 C 5.710938 -2.09375 5.90625 -2.816406 5.90625 -3.765625 C 5.90625 -4.710938 5.707031 -5.414062 5.3125 -5.875 C 4.925781 -6.332031 4.445312 -6.5625 3.875 -6.5625 C 3.238281 -6.5625 2.722656 -6.316406 2.328125 -5.828125 C 1.929688 -5.335938 1.734375 -4.617188 1.734375 -3.671875 Z M 3.640625 -7.671875 C 4.210938 -7.671875 4.691406 -7.546875 5.078125 -7.296875 C 5.304688 -7.160156 5.566406 -6.914062 5.859375 -6.5625 L 5.859375 -10.375 L 7.0625 -10.375 L 7.0625 0 L 5.9375 0 L 5.9375 -1.046875 C 5.632812 -0.585938 5.28125 -0.253906 4.875 -0.046875 C 4.476562 0.160156 4.019531 0.265625 3.5 0.265625 C 2.65625 0.265625 1.925781 -0.0820312 1.3125 -0.78125 C 0.695312 -1.488281 0.390625 -2.429688 0.390625 -3.609375 C 0.390625 -4.703125 0.671875 -5.648438 1.234375 -6.453125 C 1.796875 -7.265625 2.597656 -7.671875 3.640625 -7.671875 Z M 3.640625 -7.671875 "/>
</symbol>
<symbol overflow="visible" id="1cbab97f-a7af-4e19-9f62-2173300c28a4">
<path style="stroke:none;" d="M 4.265625 -10.5 C 3.523438 -9.070312 3.046875 -8.019531 2.828125 -7.34375 C 2.492188 -6.3125 2.328125 -5.125 2.328125 -3.78125 C 2.328125 -2.425781 2.515625 -1.1875 2.890625 -0.0625 C 3.128906 0.632812 3.59375 1.632812 4.28125 2.9375 L 3.4375 2.9375 C 2.75 1.875 2.320312 1.191406 2.15625 0.890625 C 1.988281 0.597656 1.8125 0.195312 1.625 -0.3125 C 1.363281 -1 1.179688 -1.738281 1.078125 -2.53125 C 1.023438 -2.9375 1 -3.328125 1 -3.703125 C 1 -5.085938 1.21875 -6.320312 1.65625 -7.40625 C 1.925781 -8.09375 2.503906 -9.125 3.390625 -10.5 Z M 4.265625 -10.5 "/>
</symbol>
<symbol overflow="visible" id="0188e40b-b574-4512-a899-8742f6ffae65">
<path style="stroke:none;" d="M 1.09375 -10.328125 L 2.75 -10.328125 L 7.96875 -1.96875 L 7.96875 -10.328125 L 9.296875 -10.328125 L 9.296875 0 L 7.734375 0 L 2.4375 -8.359375 L 2.4375 0 L 1.09375 0 Z M 5.109375 -10.328125 Z M 5.109375 -10.328125 "/>
</symbol>
<symbol overflow="visible" id="555fcadc-1bc9-4ba9-86e7-db861cb5b6b7">
<path style="stroke:none;" d="M 0.921875 -7.53125 L 2.1875 -7.53125 L 2.1875 -6.46875 C 2.476562 -6.832031 2.75 -7.101562 3 -7.28125 C 3.414062 -7.5625 3.890625 -7.703125 4.421875 -7.703125 C 5.015625 -7.703125 5.492188 -7.554688 5.859375 -7.265625 C 6.066406 -7.085938 6.253906 -6.835938 6.421875 -6.515625 C 6.703125 -6.921875 7.03125 -7.21875 7.40625 -7.40625 C 7.789062 -7.601562 8.222656 -7.703125 8.703125 -7.703125 C 9.710938 -7.703125 10.398438 -7.335938 10.765625 -6.609375 C 10.960938 -6.210938 11.0625 -5.679688 11.0625 -5.015625 L 11.0625 0 L 9.75 0 L 9.75 -5.234375 C 9.75 -5.734375 9.625 -6.078125 9.375 -6.265625 C 9.125 -6.453125 8.816406 -6.546875 8.453125 -6.546875 C 7.953125 -6.546875 7.523438 -6.378906 7.171875 -6.046875 C 6.816406 -5.710938 6.640625 -5.15625 6.640625 -4.375 L 6.640625 0 L 5.34375 0 L 5.34375 -4.921875 C 5.34375 -5.429688 5.28125 -5.800781 5.15625 -6.03125 C 4.96875 -6.382812 4.613281 -6.5625 4.09375 -6.5625 C 3.613281 -6.5625 3.175781 -6.375 2.78125 -6 C 2.382812 -5.632812 2.1875 -4.96875 2.1875 -4 L 2.1875 0 L 0.921875 0 Z M 0.921875 -7.53125 "/>
</symbol>
<symbol overflow="visible" id="58112033-5b69-4795-ac41-ff2a9176af8e">
<path style="stroke:none;" d="M 1.90625 -2 C 1.90625 -1.632812 2.035156 -1.347656 2.296875 -1.140625 C 2.566406 -0.929688 2.882812 -0.828125 3.25 -0.828125 C 3.695312 -0.828125 4.128906 -0.925781 4.546875 -1.125 C 5.242188 -1.46875 5.59375 -2.03125 5.59375 -2.8125 L 5.59375 -3.828125 C 5.4375 -3.734375 5.238281 -3.648438 5 -3.578125 C 4.757812 -3.515625 4.519531 -3.472656 4.28125 -3.453125 L 3.515625 -3.34375 C 3.054688 -3.28125 2.710938 -3.1875 2.484375 -3.0625 C 2.097656 -2.84375 1.90625 -2.488281 1.90625 -2 Z M 4.96875 -4.5625 C 5.257812 -4.601562 5.453125 -4.726562 5.546875 -4.9375 C 5.609375 -5.039062 5.640625 -5.203125 5.640625 -5.421875 C 5.640625 -5.847656 5.484375 -6.15625 5.171875 -6.34375 C 4.867188 -6.539062 4.429688 -6.640625 3.859375 -6.640625 C 3.191406 -6.640625 2.722656 -6.460938 2.453125 -6.109375 C 2.296875 -5.910156 2.191406 -5.617188 2.140625 -5.234375 L 0.96875 -5.234375 C 0.988281 -6.160156 1.285156 -6.804688 1.859375 -7.171875 C 2.441406 -7.535156 3.117188 -7.71875 3.890625 -7.71875 C 4.773438 -7.71875 5.492188 -7.546875 6.046875 -7.203125 C 6.585938 -6.867188 6.859375 -6.347656 6.859375 -5.640625 L 6.859375 -1.296875 C 6.859375 -1.160156 6.882812 -1.050781 6.9375 -0.96875 C 7 -0.894531 7.113281 -0.859375 7.28125 -0.859375 C 7.34375 -0.859375 7.40625 -0.859375 7.46875 -0.859375 C 7.539062 -0.867188 7.617188 -0.882812 7.703125 -0.90625 L 7.703125 0.03125 C 7.503906 0.09375 7.351562 0.128906 7.25 0.140625 C 7.144531 0.148438 7.003906 0.15625 6.828125 0.15625 C 6.390625 0.15625 6.070312 0.00390625 5.875 -0.296875 C 5.769531 -0.460938 5.695312 -0.695312 5.65625 -1 C 5.40625 -0.664062 5.035156 -0.375 4.546875 -0.125 C 4.066406 0.125 3.535156 0.25 2.953125 0.25 C 2.253906 0.25 1.679688 0.0390625 1.234375 -0.375 C 0.796875 -0.800781 0.578125 -1.335938 0.578125 -1.984375 C 0.578125 -2.679688 0.796875 -3.21875 1.234375 -3.59375 C 1.671875 -3.976562 2.242188 -4.21875 2.953125 -4.3125 Z M 3.921875 -7.71875 Z M 3.921875 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="92a6fdb2-a760-4aa3-831e-38cf2e9005fb">
<path style="stroke:none;" d="M 0.359375 -1 L 4.828125 -6.40625 L 0.703125 -6.40625 L 0.703125 -7.53125 L 6.53125 -7.53125 L 6.53125 -6.5 L 2.09375 -1.125 L 6.671875 -1.125 L 6.671875 0 L 0.359375 0 Z M 3.625 -7.71875 Z M 3.625 -7.71875 "/>
</symbol>
<symbol overflow="visible" id="03e566f6-f9d5-4f1f-8257-7ad48c187f68">
<path style="stroke:none;" d="M 0.5 2.9375 C 1.25 1.488281 1.726562 0.429688 1.9375 -0.234375 C 2.269531 -1.242188 2.4375 -2.425781 2.4375 -3.78125 C 2.4375 -5.132812 2.242188 -6.375 1.859375 -7.5 C 1.628906 -8.195312 1.171875 -9.195312 0.484375 -10.5 L 1.328125 -10.5 C 2.046875 -9.34375 2.484375 -8.628906 2.640625 -8.359375 C 2.796875 -8.097656 2.960938 -7.726562 3.140625 -7.25 C 3.359375 -6.664062 3.515625 -6.085938 3.609375 -5.515625 C 3.710938 -4.941406 3.765625 -4.390625 3.765625 -3.859375 C 3.765625 -2.472656 3.546875 -1.234375 3.109375 -0.140625 C 2.828125 0.554688 2.25 1.582031 1.375 2.9375 Z M 0.5 2.9375 "/>
</symbol>
</g>
<clipPath id="13b8bc19-7be4-443b-8935-2220b2225e26">
  <path d="M 124.46875 31.929688 L 454 31.929688 L 454 253 L 124.46875 253 Z M 124.46875 31.929688 "/>
</clipPath>
<clipPath id="5c9484ad-4807-4976-bd4d-9bcb58f4996d">
  <path d="M 176 31.929688 L 178 31.929688 L 178 253 L 176 253 Z M 176 31.929688 "/>
</clipPath>
<clipPath id="b3ad2914-cf6f-426b-a210-a53130420504">
  <path d="M 251 31.929688 L 252 31.929688 L 252 253 L 251 253 Z M 251 31.929688 "/>
</clipPath>
<clipPath id="59f3745d-8f44-4947-8898-266afbdc7df9">
  <path d="M 326 31.929688 L 327 31.929688 L 327 253 L 326 253 Z M 326 31.929688 "/>
</clipPath>
<clipPath id="395dc89b-3323-4974-a8b4-d06a8a45de4c">
  <path d="M 400 31.929688 L 402 31.929688 L 402 253 L 400 253 Z M 400 31.929688 "/>
</clipPath>
<clipPath id="a2e2da81-e037-437a-96f7-04cbb8e9050f">
  <path d="M 124.46875 230 L 454.601562 230 L 454.601562 232 L 124.46875 232 Z M 124.46875 230 "/>
</clipPath>
<clipPath id="165fa11d-2361-400d-9fcf-11e20239d06b">
  <path d="M 124.46875 194 L 454.601562 194 L 454.601562 196 L 124.46875 196 Z M 124.46875 194 "/>
</clipPath>
<clipPath id="2b832ae7-c580-47a0-9425-0abca564d81e">
  <path d="M 124.46875 159 L 454.601562 159 L 454.601562 161 L 124.46875 161 Z M 124.46875 159 "/>
</clipPath>
<clipPath id="db7452c3-6af5-4618-be65-7087cbbe0022">
  <path d="M 124.46875 123 L 454.601562 123 L 454.601562 125 L 124.46875 125 Z M 124.46875 123 "/>
</clipPath>
<clipPath id="005a267d-dad5-4437-a01c-c237feb649c4">
  <path d="M 124.46875 88 L 454.601562 88 L 454.601562 90 L 124.46875 90 Z M 124.46875 88 "/>
</clipPath>
<clipPath id="ec0c5434-2e87-430a-8b43-b8726cadc4d7">
  <path d="M 124.46875 52 L 454.601562 52 L 454.601562 54 L 124.46875 54 Z M 124.46875 52 "/>
</clipPath>
<clipPath id="ad8590f9-2996-4e1d-858f-65de1f1e25be">
  <path d="M 138 31.929688 L 140 31.929688 L 140 253 L 138 253 Z M 138 31.929688 "/>
</clipPath>
<clipPath id="c38640f2-8f25-4a49-9d13-c31416a06b4c">
  <path d="M 213 31.929688 L 215 31.929688 L 215 253 L 213 253 Z M 213 31.929688 "/>
</clipPath>
<clipPath id="152dbc07-0d4d-4862-93cb-3906103e8625">
  <path d="M 288 31.929688 L 290 31.929688 L 290 253 L 288 253 Z M 288 31.929688 "/>
</clipPath>
<clipPath id="c8f3a120-e8b2-49cb-b401-187008b10c8d">
  <path d="M 363 31.929688 L 365 31.929688 L 365 253 L 363 253 Z M 363 31.929688 "/>
</clipPath>
<clipPath id="e177f3c1-b209-4b40-845f-76f73dbe8e2b">
  <path d="M 438 31.929688 L 440 31.929688 L 440 253 L 438 253 Z M 438 31.929688 "/>
</clipPath>
</defs>
<g id="9750c70c-9e9d-4381-9d7a-2c509a929375">
<rect x="0" y="0" width="468" height="289" style="fill:rgb(100%,100%,100%);fill-opacity:1;stroke:none;"/>
<rect x="0" y="0" width="468" height="289" style="fill:rgb(100%,100%,100%);fill-opacity:1;stroke:none;"/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:round;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 0 289 L 468 289 L 468 0 L 0 0 Z M 0 289 "/>
<g clip-path="url(#13b8bc19-7be4-443b-8935-2220b2225e26)" clip-rule="nonzero">
<path style=" stroke:none;fill-rule:nonzero;fill:rgb(89.803922%,89.803922%,89.803922%);fill-opacity:1;" d="M 124.46875 252.027344 L 453.601562 252.027344 L 453.601562 31.925781 L 124.46875 31.925781 Z M 124.46875 252.027344 "/>
</g>
<g clip-path="url(#5c9484ad-4807-4976-bd4d-9bcb58f4996d)" clip-rule="nonzero">
<path style="fill:none;stroke-width:0.531496;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(94.901961%,94.901961%,94.901961%);stroke-opacity:1;stroke-miterlimit:10;" d="M 176.832031 252.027344 L 176.832031 31.929688 "/>
</g>
<g clip-path="url(#b3ad2914-cf6f-426b-a210-a53130420504)" clip-rule="nonzero">
<path style="fill:none;stroke-width:0.531496;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(94.901961%,94.901961%,94.901961%);stroke-opacity:1;stroke-miterlimit:10;" d="M 251.632812 252.027344 L 251.632812 31.929688 "/>
</g>
<g clip-path="url(#59f3745d-8f44-4947-8898-266afbdc7df9)" clip-rule="nonzero">
<path style="fill:none;stroke-width:0.531496;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(94.901961%,94.901961%,94.901961%);stroke-opacity:1;stroke-miterlimit:10;" d="M 326.4375 252.027344 L 326.4375 31.929688 "/>
</g>
<g clip-path="url(#395dc89b-3323-4974-a8b4-d06a8a45de4c)" clip-rule="nonzero">
<path style="fill:none;stroke-width:0.531496;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(94.901961%,94.901961%,94.901961%);stroke-opacity:1;stroke-miterlimit:10;" d="M 401.238281 252.027344 L 401.238281 31.929688 "/>
</g>
<g clip-path="url(#a2e2da81-e037-437a-96f7-04cbb8e9050f)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 230.730469 L 453.601562 230.730469 "/>
</g>
<g clip-path="url(#165fa11d-2361-400d-9fcf-11e20239d06b)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 195.230469 L 453.601562 195.230469 "/>
</g>
<g clip-path="url(#2b832ae7-c580-47a0-9425-0abca564d81e)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 159.730469 L 453.601562 159.730469 "/>
</g>
<g clip-path="url(#db7452c3-6af5-4618-be65-7087cbbe0022)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 124.226562 L 453.601562 124.226562 "/>
</g>
<g clip-path="url(#005a267d-dad5-4437-a01c-c237feb649c4)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 88.726562 L 453.601562 88.726562 "/>
</g>
<g clip-path="url(#ec0c5434-2e87-430a-8b43-b8726cadc4d7)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 124.46875 53.226562 L 453.601562 53.226562 "/>
</g>
<g clip-path="url(#ad8590f9-2996-4e1d-858f-65de1f1e25be)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 252.027344 L 139.429688 31.929688 "/>
</g>
<g clip-path="url(#c38640f2-8f25-4a49-9d13-c31416a06b4c)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 214.230469 252.027344 L 214.230469 31.929688 "/>
</g>
<g clip-path="url(#152dbc07-0d4d-4862-93cb-3906103e8625)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 289.035156 252.027344 L 289.035156 31.929688 "/>
</g>
<g clip-path="url(#c8f3a120-e8b2-49cb-b401-187008b10c8d)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 363.835938 252.027344 L 363.835938 31.929688 "/>
</g>
<g clip-path="url(#e177f3c1-b209-4b40-845f-76f73dbe8e2b)" clip-rule="nonzero">
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(100%,100%,100%);stroke-opacity:1;stroke-miterlimit:10;" d="M 438.640625 252.027344 L 438.640625 31.929688 "/>
</g>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 246.703125 L 342.246094 246.703125 L 342.246094 214.753906 L 139.429688 214.753906 Z M 139.429688 246.703125 "/>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 211.203125 L 376.941406 211.203125 L 376.941406 179.253906 L 139.429688 179.253906 Z M 139.429688 211.203125 "/>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 175.703125 L 245.632812 175.703125 L 245.632812 143.753906 L 139.429688 143.753906 Z M 139.429688 175.703125 "/>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 140.203125 L 268.757812 140.203125 L 268.757812 108.253906 L 139.429688 108.253906 Z M 139.429688 140.203125 "/>
<path style="fill-rule:nonzero;fill:rgb(27.45098%,50.980392%,70.588235%);fill-opacity:1;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 104.703125 L 286.421875 104.703125 L 286.421875 72.753906 L 139.429688 72.753906 Z M 139.429688 104.703125 "/>
<path style="fill:none;stroke-width:1.062992;stroke-linecap:butt;stroke-linejoin:round;stroke:rgb(27.45098%,50.980392%,70.588235%);stroke-opacity:1;stroke-miterlimit:10;" d="M 139.429688 69.203125 L 139.429688 37.253906 Z M 139.429688 69.203125 "/>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#d97e28b4-f27c-44b3-86d7-3210c0db3593" x="345.792969" y="235.816406"/>
  <use xlink:href="#beb9c497-3c67-49bb-868f-d968624281fb" x="353.674698" y="235.816406"/>
  <use xlink:href="#72f708c7-6d7c-458b-9136-e1c55a9152be" x="357.612106" y="235.816406"/>
  <use xlink:href="#7e149f0d-5e7e-424b-b4af-edd3fa97266a" x="365.493835" y="235.816406"/>
  <use xlink:href="#ec024dd9-616c-480e-8c46-59221c7298fa" x="373.375565" y="235.816406"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#d97e28b4-f27c-44b3-86d7-3210c0db3593" x="272.304688" y="129.3125"/>
  <use xlink:href="#beb9c497-3c67-49bb-868f-d968624281fb" x="280.186417" y="129.3125"/>
  <use xlink:href="#d97e28b4-f27c-44b3-86d7-3210c0db3593" x="284.123825" y="129.3125"/>
  <use xlink:href="#ee7d7ad2-544d-4959-8533-6a63f6ce2e99" x="292.005554" y="129.3125"/>
  <use xlink:href="#ec024dd9-616c-480e-8c46-59221c7298fa" x="299.887283" y="129.3125"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#d97e28b4-f27c-44b3-86d7-3210c0db3593" x="289.96875" y="93.8125"/>
  <use xlink:href="#beb9c497-3c67-49bb-868f-d968624281fb" x="297.850479" y="93.8125"/>
  <use xlink:href="#d97e28b4-f27c-44b3-86d7-3210c0db3593" x="301.787888" y="93.8125"/>
  <use xlink:href="#8101768a-8522-4f3f-a3ca-cc53921e00f5" x="309.669617" y="93.8125"/>
  <use xlink:href="#ee7d7ad2-544d-4959-8533-6a63f6ce2e99" x="317.551346" y="93.8125"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#d97e28b4-f27c-44b3-86d7-3210c0db3593" x="380.488281" y="200.316406"/>
  <use xlink:href="#beb9c497-3c67-49bb-868f-d968624281fb" x="388.37001" y="200.316406"/>
  <use xlink:href="#72f708c7-6d7c-458b-9136-e1c55a9152be" x="392.307419" y="200.316406"/>
  <use xlink:href="#90e782a3-1f30-4bcf-8f35-64c3587eeac0" x="400.189148" y="200.316406"/>
  <use xlink:href="#8101768a-8522-4f3f-a3ca-cc53921e00f5" x="408.070877" y="200.316406"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#d97e28b4-f27c-44b3-86d7-3210c0db3593" x="142.976562" y="58.3125"/>
  <use xlink:href="#beb9c497-3c67-49bb-868f-d968624281fb" x="150.858292" y="58.3125"/>
  <use xlink:href="#d97e28b4-f27c-44b3-86d7-3210c0db3593" x="154.7957" y="58.3125"/>
  <use xlink:href="#d97e28b4-f27c-44b3-86d7-3210c0db3593" x="162.677429" y="58.3125"/>
  <use xlink:href="#d97e28b4-f27c-44b3-86d7-3210c0db3593" x="170.559158" y="58.3125"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#d97e28b4-f27c-44b3-86d7-3210c0db3593" x="249.179688" y="164.816406"/>
  <use xlink:href="#beb9c497-3c67-49bb-868f-d968624281fb" x="257.061417" y="164.816406"/>
  <use xlink:href="#d97e28b4-f27c-44b3-86d7-3210c0db3593" x="260.998825" y="164.816406"/>
  <use xlink:href="#b5d53c6f-1306-4584-ac05-fb68f6244c78" x="268.880554" y="164.816406"/>
  <use xlink:href="#72f708c7-6d7c-458b-9136-e1c55a9152be" x="276.762283" y="164.816406"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#3470c6f3-0b85-4b01-860d-bdbc05dd8f6c" x="27.800781" y="234.167969"/>
  <use xlink:href="#b26c1a42-a043-44ec-b9e7-79133c09713f" x="33.661026" y="234.167969"/>
  <use xlink:href="#da236015-4f05-4e75-b55a-e50665299475" x="38.996613" y="234.167969"/>
  <use xlink:href="#cca3da42-6d2e-4ffd-9eef-e82f1a98c6aa" x="44.332199" y="234.167969"/>
  <use xlink:href="#23db89a6-74b4-4c07-925a-3c1008a61fcd" x="46.99765" y="234.167969"/>
  <use xlink:href="#472f71a9-c861-426f-a717-37a4d6cb9166" x="53.396606" y="234.167969"/>
  <use xlink:href="#472f71a9-c861-426f-a717-37a4d6cb9166" x="58.732193" y="234.167969"/>
  <use xlink:href="#b851e519-c328-4796-9d31-4447688f082c" x="64.06778" y="234.167969"/>
  <use xlink:href="#cca3da42-6d2e-4ffd-9eef-e82f1a98c6aa" x="68.864655" y="234.167969"/>
  <use xlink:href="#472f71a9-c861-426f-a717-37a4d6cb9166" x="71.530106" y="234.167969"/>
  <use xlink:href="#1783cede-06a4-4cf8-aee9-9c782355fe7c" x="76.865692" y="234.167969"/>
  <use xlink:href="#cca3da42-6d2e-4ffd-9eef-e82f1a98c6aa" x="79.531143" y="234.167969"/>
  <use xlink:href="#56fa9301-c699-4433-959f-2c65fb0acbf1" x="82.196594" y="234.167969"/>
  <use xlink:href="#472f71a9-c861-426f-a717-37a4d6cb9166" x="90.188263" y="234.167969"/>
  <use xlink:href="#e8d4ce71-ad85-4797-8280-32f5a82cc7e0" x="95.523849" y="234.167969"/>
  <use xlink:href="#921cbd9d-11a0-49bf-ad00-851e32754d05" x="98.718643" y="234.167969"/>
  <use xlink:href="#472f71a9-c861-426f-a717-37a4d6cb9166" x="106.710312" y="234.167969"/>
  <use xlink:href="#e875e0de-1db8-45c2-abea-1323413b5c3c" x="112.045898" y="234.167969"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#3470c6f3-0b85-4b01-860d-bdbc05dd8f6c" x="37.925781" y="198.667969"/>
  <use xlink:href="#b26c1a42-a043-44ec-b9e7-79133c09713f" x="43.786026" y="198.667969"/>
  <use xlink:href="#da236015-4f05-4e75-b55a-e50665299475" x="49.121613" y="198.667969"/>
  <use xlink:href="#cca3da42-6d2e-4ffd-9eef-e82f1a98c6aa" x="54.457199" y="198.667969"/>
  <use xlink:href="#8579ee81-b9bd-4c80-a452-d9fa6553002b" x="57.12265" y="198.667969"/>
  <use xlink:href="#b26c1a42-a043-44ec-b9e7-79133c09713f" x="64.050949" y="198.667969"/>
  <use xlink:href="#89c820be-4113-4fe3-ae87-8a2c4e686d38" x="69.386536" y="198.667969"/>
  <use xlink:href="#921cbd9d-11a0-49bf-ad00-851e32754d05" x="74.722122" y="198.667969"/>
  <use xlink:href="#921cbd9d-11a0-49bf-ad00-851e32754d05" x="82.713791" y="198.667969"/>
  <use xlink:href="#89c820be-4113-4fe3-ae87-8a2c4e686d38" x="90.70546" y="198.667969"/>
  <use xlink:href="#236b8f59-b648-4fd6-9b73-e091b3b2a2df" x="96.041046" y="198.667969"/>
  <use xlink:href="#89c820be-4113-4fe3-ae87-8a2c4e686d38" x="101.376633" y="198.667969"/>
  <use xlink:href="#9d55fe1a-240a-4805-a9b3-3bc9e2dbba58" x="106.712219" y="198.667969"/>
  <use xlink:href="#89c820be-4113-4fe3-ae87-8a2c4e686d38" x="112.047806" y="198.667969"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#3470c6f3-0b85-4b01-860d-bdbc05dd8f6c" x="54.996094" y="163.167969"/>
  <use xlink:href="#b26c1a42-a043-44ec-b9e7-79133c09713f" x="60.856339" y="163.167969"/>
  <use xlink:href="#da236015-4f05-4e75-b55a-e50665299475" x="66.191925" y="163.167969"/>
  <use xlink:href="#cca3da42-6d2e-4ffd-9eef-e82f1a98c6aa" x="71.527512" y="163.167969"/>
  <use xlink:href="#49bffc41-4d1a-42a9-935f-d42988bb465c" x="74.192963" y="163.167969"/>
  <use xlink:href="#472f71a9-c861-426f-a717-37a4d6cb9166" x="81.121262" y="163.167969"/>
  <use xlink:href="#7402fd65-388e-4149-9a2c-6bfacacd29d8" x="86.456848" y="163.167969"/>
  <use xlink:href="#30ff51f1-6821-4cf6-8447-45d9dfce9100" x="88.588272" y="163.167969"/>
  <use xlink:href="#cca3da42-6d2e-4ffd-9eef-e82f1a98c6aa" x="93.385147" y="163.167969"/>
  <use xlink:href="#23db89a6-74b4-4c07-925a-3c1008a61fcd" x="96.050598" y="163.167969"/>
  <use xlink:href="#8a08c1c5-c983-4856-9fcf-495e884a6d03" x="102.449554" y="163.167969"/>
  <use xlink:href="#3ee274fc-9233-4f41-b348-f10255625378" x="104.580978" y="163.167969"/>
  <use xlink:href="#7402fd65-388e-4149-9a2c-6bfacacd29d8" x="109.916565" y="163.167969"/>
  <use xlink:href="#da236015-4f05-4e75-b55a-e50665299475" x="112.047989" y="163.167969"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#3470c6f3-0b85-4b01-860d-bdbc05dd8f6c" x="23.011719" y="127.664062"/>
  <use xlink:href="#b26c1a42-a043-44ec-b9e7-79133c09713f" x="28.871964" y="127.664062"/>
  <use xlink:href="#da236015-4f05-4e75-b55a-e50665299475" x="34.20755" y="127.664062"/>
  <use xlink:href="#cca3da42-6d2e-4ffd-9eef-e82f1a98c6aa" x="39.543137" y="127.664062"/>
  <use xlink:href="#1319f8ef-6019-4876-bedf-98bd20a90d53" x="42.208588" y="127.664062"/>
  <use xlink:href="#da236015-4f05-4e75-b55a-e50665299475" x="47.005463" y="127.664062"/>
  <use xlink:href="#e47f4888-776c-48dc-af2f-5e5d312efc20" x="52.341049" y="127.664062"/>
  <use xlink:href="#8a08c1c5-c983-4856-9fcf-495e884a6d03" x="59.269348" y="127.664062"/>
  <use xlink:href="#d0033be6-8b9b-496f-b53f-222098f35a43" x="61.400772" y="127.664062"/>
  <use xlink:href="#b26c1a42-a043-44ec-b9e7-79133c09713f" x="66.197647" y="127.664062"/>
  <use xlink:href="#cca3da42-6d2e-4ffd-9eef-e82f1a98c6aa" x="71.533234" y="127.664062"/>
  <use xlink:href="#751aabe8-b3a9-4afd-8d28-c387bfc78105" x="74.198685" y="127.664062"/>
  <use xlink:href="#593dfe30-034a-4e9f-ac74-4785d1ac3dab" x="80.597641" y="127.664062"/>
  <use xlink:href="#e8d4ce71-ad85-4797-8280-32f5a82cc7e0" x="85.394516" y="127.664062"/>
  <use xlink:href="#8a08c1c5-c983-4856-9fcf-495e884a6d03" x="88.58931" y="127.664062"/>
  <use xlink:href="#236b8f59-b648-4fd6-9b73-e091b3b2a2df" x="90.720734" y="127.664062"/>
  <use xlink:href="#9ac0766c-2248-411b-ab32-ccc7416332f2" x="96.05632" y="127.664062"/>
  <use xlink:href="#c558b5ce-0bcf-4ba7-8953-cefe3a7cdbc1" x="98.721771" y="127.664062"/>
  <use xlink:href="#e8d4ce71-ad85-4797-8280-32f5a82cc7e0" x="104.057358" y="127.664062"/>
  <use xlink:href="#da236015-4f05-4e75-b55a-e50665299475" x="107.252151" y="127.664062"/>
  <use xlink:href="#d0033be6-8b9b-496f-b53f-222098f35a43" x="112.587738" y="127.664062"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#3470c6f3-0b85-4b01-860d-bdbc05dd8f6c" x="72.585938" y="92.164062"/>
  <use xlink:href="#b26c1a42-a043-44ec-b9e7-79133c09713f" x="78.446182" y="92.164062"/>
  <use xlink:href="#da236015-4f05-4e75-b55a-e50665299475" x="83.781769" y="92.164062"/>
  <use xlink:href="#cca3da42-6d2e-4ffd-9eef-e82f1a98c6aa" x="89.117355" y="92.164062"/>
  <use xlink:href="#ba8c8c17-3a09-47a6-8884-ccee5f701981" x="91.782806" y="92.164062"/>
  <use xlink:href="#472f71a9-c861-426f-a717-37a4d6cb9166" x="98.181763" y="92.164062"/>
  <use xlink:href="#e8d4ce71-ad85-4797-8280-32f5a82cc7e0" x="103.517349" y="92.164062"/>
  <use xlink:href="#89c820be-4113-4fe3-ae87-8a2c4e686d38" x="106.712143" y="92.164062"/>
  <use xlink:href="#e875e0de-1db8-45c2-abea-1323413b5c3c" x="112.047729" y="92.164062"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#3470c6f3-0b85-4b01-860d-bdbc05dd8f6c" x="70.984375" y="56.664062"/>
  <use xlink:href="#b26c1a42-a043-44ec-b9e7-79133c09713f" x="76.84462" y="56.664062"/>
  <use xlink:href="#da236015-4f05-4e75-b55a-e50665299475" x="82.180206" y="56.664062"/>
  <use xlink:href="#cca3da42-6d2e-4ffd-9eef-e82f1a98c6aa" x="87.515793" y="56.664062"/>
  <use xlink:href="#f7b4a3ba-5620-49a2-ae14-3e8aff9d8d30" x="90.181244" y="56.664062"/>
  <use xlink:href="#da236015-4f05-4e75-b55a-e50665299475" x="96.5802" y="56.664062"/>
  <use xlink:href="#9d55fe1a-240a-4805-a9b3-3bc9e2dbba58" x="101.915787" y="56.664062"/>
  <use xlink:href="#89c820be-4113-4fe3-ae87-8a2c4e686d38" x="107.251373" y="56.664062"/>
  <use xlink:href="#d0033be6-8b9b-496f-b53f-222098f35a43" x="112.58696" y="56.664062"/>
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
  <use xlink:href="#5039ce22-22e0-4562-89e3-d11738723d58" x="130.09375" y="265.992188"/>
  <use xlink:href="#bcfa4c4b-5db3-4b3a-9a97-6d6ee4583166" x="135.429337" y="265.992188"/>
  <use xlink:href="#5039ce22-22e0-4562-89e3-d11738723d58" x="138.094788" y="265.992188"/>
  <use xlink:href="#5039ce22-22e0-4562-89e3-d11738723d58" x="143.430374" y="265.992188"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#5039ce22-22e0-4562-89e3-d11738723d58" x="204.894531" y="265.992188"/>
  <use xlink:href="#bcfa4c4b-5db3-4b3a-9a97-6d6ee4583166" x="210.230118" y="265.992188"/>
  <use xlink:href="#5039ce22-22e0-4562-89e3-d11738723d58" x="212.895569" y="265.992188"/>
  <use xlink:href="#93ce07d2-2689-42c9-9dcd-d488b0c849e9" x="218.231155" y="265.992188"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#5039ce22-22e0-4562-89e3-d11738723d58" x="279.699219" y="265.992188"/>
  <use xlink:href="#bcfa4c4b-5db3-4b3a-9a97-6d6ee4583166" x="285.034805" y="265.992188"/>
  <use xlink:href="#582db5fa-97c7-4c0c-b1dc-a1af63b32474" x="287.700256" y="265.992188"/>
  <use xlink:href="#5039ce22-22e0-4562-89e3-d11738723d58" x="293.035843" y="265.992188"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#5039ce22-22e0-4562-89e3-d11738723d58" x="354.5" y="265.992188"/>
  <use xlink:href="#bcfa4c4b-5db3-4b3a-9a97-6d6ee4583166" x="359.835587" y="265.992188"/>
  <use xlink:href="#582db5fa-97c7-4c0c-b1dc-a1af63b32474" x="362.501038" y="265.992188"/>
  <use xlink:href="#93ce07d2-2689-42c9-9dcd-d488b0c849e9" x="367.836624" y="265.992188"/>
</g>
<g style="fill:rgb(49.803922%,49.803922%,49.803922%);fill-opacity:1;">
  <use xlink:href="#5039ce22-22e0-4562-89e3-d11738723d58" x="429.304688" y="265.992188"/>
  <use xlink:href="#bcfa4c4b-5db3-4b3a-9a97-6d6ee4583166" x="434.640274" y="265.992188"/>
  <use xlink:href="#7a39b0d7-7696-47ab-893a-a4dd1bb55ce0" x="437.305725" y="265.992188"/>
  <use xlink:href="#5039ce22-22e0-4562-89e3-d11738723d58" x="442.641312" y="265.992188"/>
</g>
<g style="fill:rgb(0%,0%,0%);fill-opacity:1;">
  <use xlink:href="#e03234d3-5563-4c9e-9e94-ab651a0ff896" x="150.121094" y="28.328125"/>
  <use xlink:href="#447692ed-73a1-4243-8986-dbebf6acf672" x="159.72995" y="28.328125"/>
  <use xlink:href="#ccab89b2-6678-4318-abfb-2408b8ac0cb3" x="167.74202" y="28.328125"/>
  <use xlink:href="#0feb9142-88a3-4b62-b8ad-3fbb86e81a1f" x="175.754089" y="28.328125"/>
  <use xlink:href="#447692ed-73a1-4243-8986-dbebf6acf672" x="179.756607" y="28.328125"/>
  <use xlink:href="#ccab89b2-6678-4318-abfb-2408b8ac0cb3" x="187.768677" y="28.328125"/>
  <use xlink:href="#879fe346-e42a-4f0d-a051-861200576f06" x="195.780746" y="28.328125"/>
  <use xlink:href="#447692ed-73a1-4243-8986-dbebf6acf672" x="202.983871" y="28.328125"/>
  <use xlink:href="#1faf4588-b445-4fac-84ef-d1460e0581be" x="210.995941" y="28.328125"/>
  <use xlink:href="#0f0f1cb2-459e-45de-bbcf-831f62b4cd8b" x="218.199066" y="28.328125"/>
  <use xlink:href="#805fb74a-270c-442a-8e0d-becc58351575" x="222.201584" y="28.328125"/>
  <use xlink:href="#2b4932ec-c76d-4093-9e07-a78a6b763e4c" x="232.605316" y="28.328125"/>
  <use xlink:href="#0feb9142-88a3-4b62-b8ad-3fbb86e81a1f" x="235.805923" y="28.328125"/>
  <use xlink:href="#279ab554-4d29-4bae-bd0d-a141f399a5aa" x="239.808441" y="28.328125"/>
  <use xlink:href="#0f0f1cb2-459e-45de-bbcf-831f62b4cd8b" x="247.820511" y="28.328125"/>
  <use xlink:href="#2115e1d0-b396-4868-9323-65f817d761a7" x="251.823029" y="28.328125"/>
  <use xlink:href="#2b4932ec-c76d-4093-9e07-a78a6b763e4c" x="261.431885" y="28.328125"/>
  <use xlink:href="#916c655f-f46c-4a6e-9996-613dceded33d" x="264.632492" y="28.328125"/>
  <use xlink:href="#707767d8-31a8-4231-b194-4f39f3e20acb" x="272.644562" y="28.328125"/>
  <use xlink:href="#447692ed-73a1-4243-8986-dbebf6acf672" x="275.845169" y="28.328125"/>
  <use xlink:href="#ccab89b2-6678-4318-abfb-2408b8ac0cb3" x="283.857239" y="28.328125"/>
  <use xlink:href="#0feb9142-88a3-4b62-b8ad-3fbb86e81a1f" x="291.869308" y="28.328125"/>
  <use xlink:href="#0f0f1cb2-459e-45de-bbcf-831f62b4cd8b" x="295.871826" y="28.328125"/>
  <use xlink:href="#12f3b784-13f3-486c-935a-920840bdf033" x="299.874344" y="28.328125"/>
  <use xlink:href="#916c655f-f46c-4a6e-9996-613dceded33d" x="313.471649" y="28.328125"/>
  <use xlink:href="#339f1cb6-5154-45ab-8870-ac939b3c1394" x="321.483719" y="28.328125"/>
  <use xlink:href="#3d0bee46-6355-40d4-86aa-ff9c51e3d752" x="326.281113" y="28.328125"/>
  <use xlink:href="#1faf4588-b445-4fac-84ef-d1460e0581be" x="334.293182" y="28.328125"/>
  <use xlink:href="#0f0f1cb2-459e-45de-bbcf-831f62b4cd8b" x="341.496307" y="28.328125"/>
  <use xlink:href="#1cbab97f-a7af-4e19-9f62-2173300c28a4" x="345.498825" y="28.328125"/>
  <use xlink:href="#0188e40b-b574-4512-a899-8742f6ffae65" x="350.296219" y="28.328125"/>
  <use xlink:href="#916c655f-f46c-4a6e-9996-613dceded33d" x="360.699951" y="28.328125"/>
  <use xlink:href="#339f1cb6-5154-45ab-8870-ac939b3c1394" x="368.712021" y="28.328125"/>
  <use xlink:href="#555fcadc-1bc9-4ba9-86e7-db861cb5b6b7" x="373.509415" y="28.328125"/>
  <use xlink:href="#58112033-5b69-4795-ac41-ff2a9176af8e" x="385.509933" y="28.328125"/>
  <use xlink:href="#707767d8-31a8-4231-b194-4f39f3e20acb" x="393.522003" y="28.328125"/>
  <use xlink:href="#2b4932ec-c76d-4093-9e07-a78a6b763e4c" x="396.72261" y="28.328125"/>
  <use xlink:href="#92a6fdb2-a760-4aa3-831e-38cf2e9005fb" x="399.923218" y="28.328125"/>
  <use xlink:href="#447692ed-73a1-4243-8986-dbebf6acf672" x="407.126343" y="28.328125"/>
  <use xlink:href="#3d0bee46-6355-40d4-86aa-ff9c51e3d752" x="415.138412" y="28.328125"/>
  <use xlink:href="#03e566f6-f9d5-4f1f-8257-7ad48c187f68" x="423.150482" y="28.328125"/>
</g>
</g>
</svg>


Now things are more interesting.
Far ahead of the others we have Dhammapada and the Book of Mormon.
To tease out a little more context and chase down this surprising (at least to me) result we need to look at which of the violent words in the list were in the most sentences.
We could visualize this with just a series of barcharts, but since we're specifically producing counts of words I think word clouds will be a lot more fun.

```clojure
(def violent-word-counts
  (->> (map #(select-keys % [:book :word]) violent-sentences)
       frequencies
       (map (fn [[bw c]] (into bw {:count c})))))

(defn word-cloud [book]
  (let [fname (str "images/" 
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

<span class='clj-var'>#&#x27;violence-in-religious-text-nb/word-cloud</span>

### The Holy Bible

```clojure
(word-cloud "The Holy Bible")
```

<img src="data:image/png;base64,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" width="450" height="225" alt="" />

### The Jewish Scriptures

```clojure
(word-cloud "The Jewish Scriptures")
```

<img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAcIAAADhCAYAAABbV7VpAABI50lEQVR42u2dBbwUVRvGD93deenu5tLdICXdSHenIEoZgIKAoiCi30cIoqKiqIiEHZ8JiqIogigGqEh5vnnmzOzOzs7W5cbGM/f3/927M7Ozs2f3zjPve94QQghJCCGExDAcBEIIIRRCQgghhEJICCGEUAiDIEeOHDJPnjwyU6ZMsmbNmrJIkSL6+lSpUslSpUrJOnXqyHz58rn2x/rChQvLChUqyOzZs7vWZ8yYUVatWlXWqFFD5sqVix8GIYSQyBDCNm3ayHnz5smFCxfK+fPny7Fjx8o0adLIgQMHysWLF8vp06fr2yB+mTNnliNGjNDXz5gxQ86dO1cWKFBAF8GJEyfqx8F6AGHlB0IIISTshbB9+/by9ttv1y28MmXKyOHDh8uGDRvqYlexYkVZrFgx/W8IYefOneXs2bNloUKFZLp06eSiRYtkvXr1dEtw1qxZ+m88f86cOTJr1qz8QAghhESGEMIKtK7r2bOnnDBhgv53wYIFdSGE+I0bN0726NFDXw9hNMWyV69euihCULE9b968/DAIIYRErhDCyoNVly1bNhkXF6cLHixGCN6wYcN0a2/AgAH6+qJFi8ouXbroblVYlJhzxDwhhJMfCCGEkLAXwrZt28pRo0Z5rEMQDMQRVh7AXOAtt9yii96UKVN0AcR8IH5D+CCYQ4cO1S1CrIOIQhT5gRBCCAl7IYR1lz9/fsdtiBbNmTOnHviCyFIzahTWXpMmTXThS506tUfkKJ5jXUcIIYRETR5h5cqV9XSK+Ph4OW3aNDlo0CAOOiGEkNgRwtatW8vJkyfLqVOnyt69e8ssWbJw0AlJYfr1E1JKIf/5R8gyZYQcOlT9nT49x4ZQCAkhMcADDwh56JCQH38s5ObNQpYqpYSxenWOTVSRSeOERk+OBYWQEOLBkiVC/vCDkGPGCHnlipC9e6vfWbNybKKKshr4uY1jQSEkhHgQFyfk+fNC/v67sgSvXhVy/36OS7KSSqO65W9f+6WxPSfY4+fUqGMI4a0cbwohIcSL3LmFHDxYyO3blas0b16OSbIyXOOGxiKNPzS2auw2/tY+E5FaY4DGWeNvPGeHxiqN7hpPakzWOKJxSqOIsQ+sv18MATR/tzG2tdZ4R+OcxgGNesZ6f8ejEBJCopFly4SsUMH9uH9/IY8coRgmK0sMkcLPt8bvnzVeNP5urrFF40/Lc341BOsuYx+I5k7j794aTY2/X9aYoPGx8biuRgGNaxrHNJZrXDZeS/g5HoWQEBKNpE0r5NdfC3lWszRq1BByyxblHn31VSHTpeP4JBvzDcF50AhmMcXPnNcbrbFL4wfLcyBkGzWWGfvEG65TWIklNTZoXNIwo38HGPtV0Rip8Y/GCsPKvKLR3tjP1/EohISQaKV0aSHPnBHy33+FvH5dyDlzhEydmuOSrMw1xKe8RjPj786GiGmfi7hPY71hBZrPuW4I5zLjb/sxIZwnLY9NIaxuuFWlYWGu1Shu2c/X8SiEhJBoIk0alTNYubKQ5coJ2bWrihQ9dkzIbNnUdo5TCghhGcNiw88wY9uPGk9rLDTWd9DoYYjVekO4bjgcc6qxPyy6lsY8IH4aaiw1BHaSRlXjtSC2af0cj0JICIkm7rtPuUB9cekSSiRynJKNQYb4YMwLGYI129j2hhHUUlDjgmUuET8PaSzW+NvhmOkMq9D8+V3jqkYTjazGtj+Nbf8aj4Wf41EICSHRRPHiQvbti5ZpQt56q6ouM0i7GI8eLeSMGUKOG0f3aLJjdU/eb6Q74O84jRaWpPjGhuXY1Pid15jP83XcEhoNNLIYaRTWbRk0amtkt6wLdDwKYRiDsONPNMppaP/UegUF/nMR4pP585VbtGhRIXv1ErJDByGbNROyVSu0UuP4EAphZJHJ8Jd/qLHHcCFI406KHyohXiAi9Nw5ZQmuWuXsHs2Vi+NEKISRQ25D+PoagljP8LcX4IdKiC9M1ydSKJA6Ub++kE2aCBkfryxFjhGhEEYaTxsJqH8YE8rv8AMlJBgmTVJl1ZBEz/EgJJJPPqNGL41tQlVbKMsPlJBgQGWZb78Vsiz/ZwiJYCFElNMSfoCEJAS4RZFMjyAZjgchkXribY05wtyGKBaxhQMTQnzSpYsKjrl2TcjvvhPy9Gkhv/pKCSTHh1AII4HtRmCM/QcJojX5oRISiNq1hbz7bpVkb7J8uZD583NsCIUwMmiucY8hfqil185Yh3p9LBNFSEB27RJy9WqOAyGR7Rotb1iGqfghhkRFoYr2ZuFYxCIZMgjZqJGQJ04IefiwyikcMULIsWOF7NOH3ScIhTDysJaDQuHYjrzAB2SdYUm35ljEatqEv3qjDRtyjAiFMLJAwVh0YD4tVJ+tK0yh8Ek2w328X7ir06PfGarVz9FYoNFfuPuYkagkc2aVQP+//wn58suqG0XhwqrYNpvyEgphpGFWlkFzy/EGEMM8/FAdOegQXOT004djFQusXKkiRzkWhESyEKY3LMDxluR6tBGpzg/VkVEaz2ocNwTvgGEV3mlYgwAtYHJwrGKB6dOFfO019TeKb6Nj/T33cFwIhTDyuMu4qB/V+EzjK36gAVkv3I0+OR4xC9yijzyiOtWj1Np776k5QrpHCYUwEhlqRI/ComHR4MC0MeYJafnFNFu3Crljh5DPPSfkxx8LmSePEsI2bTg2hEIY/iBdIp0PmEoRnEt5mlC1WYdzPGIVNOM1K8tgrrBOHfW4cmWODaEQhj9v+gn0uKRRiB+qX/pZxusUxyNWQTsmdKuvWFE9RkUZFOLm2BAKYSSg3bmKW33Q2ZZbSLzpb4jgQ0LVaOWYEEJIBJ98fY3nNH40XH1MBg5MESPN5HPB4gOEEBLxQvieEfhxm8ZejYvMIwwI0kyOGVbhR0YyPZirMUmwgwchhEIYMWTSuG7kx+FxYePiXpcfql9aBEioH8oxIoRQCCMHpE38qfGUxqca3zByNCCoydpTqPJqY20M0cjKMSKEUAgjyyocagghKqQU4wcaFJkNQeRYEEJIBAohLuKtjL8LaBQ05rVyMPgjKHoJd1NjlKT7XeMPI/UEj3tzjAgJN15YLaR8yzfXjwmZg96cGBLCNoY7NJ9QHemtP1cZ7BGQlkb+4EWH8ZPGdo4TIWFFdu0mv2lNIbs2EfLz7UKe3y/k3CFCzh4k5LFNQl54WcgM7CUZY67RbMbvKoZ12MXIIYznBxoSmE9NY/y90xBClqkjJKz55SUhl49zP25SQ1mFdSpybGJvjjCnYdmsFexBmBjcYwhhN44FIeHM+1uFfGezkGmNm9j5Q4W8+JqQWTJxbGIzWGaCUB0nbhgBM0ydCC6hfqFQ7av6GcFGs42oW2lY2RwnQsIWuEdvHBPy91eE/GibsgYfmsNxiV0hNBPEN1vmCFk2zD/z/OQQ7mP6CSGRQN1KQq6fJeSeFWqekPODsSqEuGAv1/hF45rGbo2m/EADktuwpFcKVW8UhZYna7Tn2BASCeTPJeSDM4WMr6oez9KEcPUUjktsCiEsv/c1FhvuPn6QwZPLcIvCJYrmxo9qvGG4mWtyfAgJZx6YrtIlrh4RskQhIYd2Uo8zZ+TYxK5rlIRGViN1wukHaSkVOEaEhDOHHxJy8wIhTz0t5EbtZrZCnJonrFWeY0MhJMEx1BC9V4yAGRTaHmS4lZmDSUjYs2uZkB8/KeQ87X/570NCjuiqfmfKwLGhEJLgGGQI4SGOBSGRSO0KQv71ugKWICJI997NcaEQkuBBKbrvDTG824i65bgQEjEgf7B1ayEnTRJyx1Ih75nI8moUQhKYfYb4/aBxUuOCZV7wghEk86EhjBwvQsKaffcpS3DnUo4FhZAEzw0hg/q5aliMHDNCwhYI4JGHhcyWmWNBISTBk1+4u3Mg7QRdOwoJ1cy4oJF+ghqjJThWhIQ7vVoKefkNVWOU40EhJISQmGPRSHfrpT9eVfy8X8h6lTg2FEJCCIkB+vYV8u67hVw53s2do4XMl5NjQyEkhHiQKlUq2a1bN7l48WKdCRMmBLWNhDew/mYO5DhQCAkhAalUqZIucrfffrvs0aOHLFmyZFDbSHhSrICQgzoIee2okC/dr2qMwk26bKyQU/uxxBqFkBDihWnx1alTJ6RtJDzZvULNDTqB+cKGVTlGFEJCoozUqVPrllvz5s1l27ZtZaNGjfR1mTJlkk2aNJHZs2d37QuLDvuabs+8efPKsWPH6mJXr149fVvWrFn9bjOPlTFjRlmmTBlZuXJlmTt3bv055vngddKmTau/dtWqVXXSpEnDzysZQNJ8tTKqogy6T+TJocqqpUvLNkwUQkKiEIjLmDFjXHN4JhUqVNDFC383a9bMtf+UKVPkokWLZLp06WSLFi28ngd69erldxuOExcXJ2fNmuWxbcCAAboYmu7UESNGyAULFri2N2zYkJ9ZMoJqMjXKea5jnVEKISFRR/HixXWRGTdunC5OhQsXll26dJF58uSRbdq0cVlz5v5z587V18FqK1u2rC5s8+bN09fBeqxdu7bMlSuX322wBOfMmSMXLlyov0Z8fLxrP1iRcKOa4jd+/Hj9fPB3z549+ZklIxVLCPmfJWpu8N5JqjnvlcNCNq7OsaEQEhJFZMmSRRclCE2fPn1kwYIFXdtMAYJb0nSFwhqcP3++xzFgJWI/07UZaFv9+vX1dd27d5dNmzZ1WYajRo3SLdS6devqj2Gppk+fXmbOnFk/Ts2aNfmZJSNowfTPYRU0gwjS8xoXXobrmmNDISQkyoAVeNttt+niA6Fr3769LlytWrXS19WoUcM1d4fHEE4nsXM6ttO2zp07e7hEp0+frs9Lwt2K7aYQNm7cmJ9PCoLyak/c4X5cnv0IKYSERDulSpWSkyZN0kWoWrVqskGDBl6ChMdwjzqJHYTSlxBat0FozZzC6tWru4JgEBxDIQwf1kxVrtA29eANELJ5LSWEJQtzbCiEJFHp2LGjfOmll+TZs2fljRs3JJZz587JtWvX6hGFHKOkzwG89dZbddGpVauWyzLEvCDG35w/RDRp79699ceYz7MeY8aMGbol6SSETtsguFgHyxJzhLAG+/btqx8X7k9TCDGvyM8o5cibU8jv9irx+/WA+n10E8eFQkgSFQRC+Ft27drFcUpiYPkh2d3qqhw5cqSeOoHt/fv394r8tM4RwoWK59vdpYG2QezMuUkTzBEijQKuWDzGXCI/o5QlVzYhbx+h5gv7txMyZzaOCYWQJGrY/l9//eVXCK9cuSIzZMjA8UrqvLEcOfR0iSpVqsicOXN6iVn+/Pn17YgkRX4f8v6sOYjTpk3TA1+c8hN9bQMIhClatKgsXbq0HrRjXQ8x5GcfXqAdU+VSyk3K8aAQkkQAF9ZAywcffMCxigAwt+cUMRpoGwlvGlUT8uUHhDz1tNs1Cth9gkJIEokiRYr4FEDMFR4+fFh26NCBY0VICvHqOiF/eE7IR+cLuWmekEvHCNm9OceFQkgSlT/++MNDAE+ePKkLpNVNRghJGd58RFmDKK3G8aAQkiTi7bff9rIGUX2EY0NIyoBeg63rKmABwhV6cL2Q7RoIGV9VyAK5OUYUQpKoPProo15CuHHjRo4NISnEsU2+O08AdKlPSL3R3NlZp5RCSBxBZOC///7rIYRwl2bLlo3jQ0gKUL2skK00a7BFbSHba1Zgx3ghOzVSv29pJmS3pqGnX8C9am3j9Psrau7x691CfrlLyC92CPnJk0J+tE3I9x4T8oPHhTyxU8gX16jnUwhJ1PPf//7Xyyp877339LlCjg8hkU39yv4tzEB0aUwhJDEAqpdcu3bNSwx//PFHj64HhJDIZNQtQj48V9UthcVnitzfh1RADtbBGvx8u5BfPaWsRFiFSOLPkolCSGIAFHaG6Dktly9flnv27JG7d+/Wf4Onn35aZ+/evTrPPPOMvh6969ClgGNKSPiSPYu7bBsY1plzhCTGCVRiLdTlwIEDHFdCwpjBHZUlaArh1SMqIpVCSGKW119/PVGFEIE3aDTLsSUkvMicUcgNs5znASGM6GxBISQxyZYtWxJVCFGbNF++fBxbQsIsYAZRoKbwodkv5g3nDXWvu3RQyLqVKIQkBqlTp47ecimxlvXr13NcCQkjCuVV6RKm4H27V/u/r+jevmike9vzqyiEJEZBF4qWLVvKfv36ySFDhugtgMaMGRMS6KFXrlw5jichYUaDKm6he3yRcxun+ZplePE1ISf0phASQgiJQlrW8bQCnUidmnOEhNwUaPXDcSCEUAgjmMKFC8t27drJrl27ejVGTQhodtqzZ0+9wWk0j1uxYsXk3XffLS9cuCA//PBD2b59e36fCAkzRnZVbZweWyjkk3cIuf0ulSy/drqQy8epoJkqpd37ly0mZFxBCmGSgG7ZmI/C3wULFvTYBsHInTu36zE6cqOqCdbbu2Rjv0yZMiXaOS1fvlzvv2cuTz31lL4NzUw7d+4s27ZtG/Jx33//ff1Y6A4eCV+OUqVK6YEuq1ev1m8I4uLiZLp06bzGKmvWrHoX9QEDBuil2ewVab7//nt9P158CAkPIHDBlFI7v1/INKmFrFFOyBvHVE3SUkUohInOnXfeKd944w296zmWWbNm6evvuusuPewey65du/QL6apVq/Tiz3/99Zf8559/5IQJE2TGjBn1iiZYrl+/LhctWnTzd0ojR3pcyM+ePasnmWMbrBtzqV+/fkjHPXHihP68xx57LOjnoAnu4MGDk11IcGNx/vx5xyhQCN3Fixf1CjPBLpUqVeIFiJAwASXSju/wFL1/3/QWwrceVfujsLe5LkpzC1P2BGBxYNm2bZt84okn5MKFC+XQoUP1dRCf+fPny19++UXf95577tFFcOzYsXLTpk26QPXq1Uv+/PPPsm/fvrob7pNPPrnpc8JxTEsGx7WKEC7oly5dkr/99pvuAgzluEePHtWPC+EOZn9YWhB8LIjgTM7P5ZFHHkm09Anc5PDiQ0h4kSqVEsT06dTfWJcxvZB5cghZvKCQ5ePcwTJoAjxnsJBT+7n3pRAmIhs2bJCvvfaa/jfcnRCd7du366KBdRDD06dP638vXrxY75aOvxs2bOhyWcJKhDWI2pY3G64P8cGxsPiyLrFPQtyw6N6AZceOHUHtj3lJc5k6dWqyfSYlSpTwasOU0AX5iHCp8sJDCKEQ+hHCF1980WPdggUL5Geffaa7PSGEsIoQgThnzhz9wooqJStWrJA//PCD/M9//qMHZWDuEHOMyHvD8xJyLhA3q+tz5syZskePHrJBgwauCEiINQJefFmD2K98+fK6mNjdmXhPWLZu3aofp0uXLvp7Qp5elixZXPvVrFlTbt682cMqg4jiva5cuTLJozFxTom14LPkPxkhhELohwceeEA+++yzHutg1X311Vfy77//1kUQc4UdO3aUs2fPdl1g4ZocNWqULFu2rHznnXdcgS2YhytQoEDI5zF69GiP4Bj7MnnyZH2/6dOn64/fffddj+fXqlVLHjlyxDWviQXvoVOnTq59jh8/rq9HLc9PP/3Up/sQlq2/BcKZlJ+J6cI1F4yL9X3Zl19//VV3Se/fv99lTZvLF198wX8yQsIMdKW/b7KQL6wOnTVT2YYp0SlUqJAsWbKk47bq1avLPHny6GIHi8m0CJs1a+ZhQQGI38244O644w7HYtEICkG0J1yx2O/hhx/Wt3333Xeu5w4bNsxDAM6cOaPPZZoL0i+w35dffun1GuYcIJaqVavq++H94eYA4mi6KHHMV199VY/KTOrmuJhztS44F1i3sHLr1q2rjwWiRDHe9hZLDz74oNd7bNGiBS8+JKLo0kVo/29CvvaakN26Rd/769rk5hrz9mxBIUwx5s6d65ovTPyJ41Sydu3auuVnLk6W5ZNPPqlv+/zzz105cxBLLIcPH3Z1WShdurTrOOjJh3WY37RaUUjBwHyj+XxEwdpfD/thue+++5JpAj2V1/wggpiCfT5uXBBMZF1CiZIlJMUjKrMIeeGCdsGXiqtXhcyXL7reY46sQr64RqVHoOZoKLy/Vcgi+SiEKQbco+iMkJSvgRxBc3HqmICGs1jgjjXTP8ylRo0arv3i4+Nd682cQ6sQmlai1QJDhKz99RC5imXjxo3JNs6I0rUucDeH8nykw1gXzI3yAksihZ493SJoMnw4x4VzhDGEVQjz5s3rtf2VV17Rtx08eNBjPu/rr7/22G/48OGu48CNaHWNYo7Quu/vv/+ur0cAkK/cw8cffzzZxuDjjz++qX6CZkqMucBtzHJrJFKYOtVbCG+7LbLfU/bsQrshh8eGny+FMEQhdHKN2oXQDCyxd2E/dOiQ6zhmdRwEzziJ2rfffuvThfi///0vpJSLxApgsi8Q72CT+vE+rAuievndIpHC8uXeQjhoUOS+n7Fjhfz7b/U+4PKtUoXBMhTCQJPIXbu6LuBOQSlI9cCCCFE8RpoDlj///FPmyJFDX9enTx+PYBi7mxMBN9Zjws1qFVcrZlm2YJPwEwO4cp0WlJ3DHKK/5yItBNaxdTl27Bi/WyRiePhhbyHsHaGtiNKnF/LsWc/38t//MliGQhgAs6oNFlPYrCCCEsvbb7+tP0b+opl2gcR+iJk1DePUqVOuIBQzknTp0qUexzTFFCXNfLkpd+/enYwVJ1K5quvYF0SuInrUcQJeGy9EtdqXpJ7XJSQx2bHDWwjhVozE91K6tPd7+flnBstQCANg5ipevXrVb5oFxMtchwa01nQJzImZgTGIJDXFBW5O5D8ikMZ6TORDYkG0pX0uDdaUvyo3SYW1sIB9wXtFNaB7771XL3iwZMkSvUSePVrUXJB7ye8WiRT27/cWj7p1I/O9FCzo/V7+/ReFP/g5Uwj9MG7cOI/0CCdrCcEvZscME7RsQt1TiCLqkZppFqgGY+6Dzg1IL/B2X6SXM2bMkM2bN/fahtxCCImTdZrUQOhudkG6S7Zs2fjdIhHD0aPe4lGmTGS+l2zZvN8LQPAMP2sKoR+fenrZu3fvm6pZihJvP/30ky4ESK+I1LGA2O/du/emhNBaWYeQcAGFoyFuSJXo3l3I2rVV/iC2ffSRt3CkVLQlLLcKFYRs0EDI1q2FvOUWIfv2RVca9Dd13h/vZ+ZMdNFRfzsJ4ZgxKKUo5Pz5Qk6aJLSb8ISfIwpz581LIYxp0GwXbZJatWqli2ejRo3k888/7xKCypUrR/T7gxiuW7cuQSKI6FN+R0g4AQGAxXfpkrc4/PGHkCtWIKjN25WYJk2g6wDKL6pqNF99JeRvvwl56pSQb7+tglP69VPWWTDnWLw4Ou0giE7Iy5edhcw8r9Wr3c/LlEnI777zvX8gECRkPY/4eCG3bkUnGsRCCItnC9M56n3hpsGMSP3lFzUGwQTwdO2KNCshn3tOyDffRKQ5ylzCA+bvWoRARCFfegmFTVCVLDSxXrZMjenSpY43EfznuBlefvnlmBACFDOAuziY5c0333SVpCMkHMifX8h9+xImEL//7t9ig1X1zz+Bj/Pnn8oC9XeesOSuXQvt/KpXV88dPDjhIgiuX3e7THPm9HxPP/yghHbAACG//tr/cfr39/3+RozwrNrjxNq1zjceQ4d67vfpp/C+Bff533ef53NffJFCmMhVKHrqAmGWJUNJNFRWQUJ9tL1XWIewfCHwCAJCQBByIBEp++ijj+qVcSCY/F6QcAKWzU8/JVwgYNk5HReW0fPPh3YsWHGzZjkfDxZRQs4P1iaeDwG6GSFENGmGDOpYAwd6bw92DI8dcx6rp58O/lyefdbtqjaB5Wjfb9WqwJ8/XOBONxeFC1MIk0QkGBRCiALF9Dt06KBHHyMv1QQ3Srh5HDRokJw0aZL+d1KeBy7scFXejEB88IHzsR9/PGHHg6WVKZO3ZXnuXMKOhwLhOAaE4623EnYMCIVVoJ2q6wQLXMz2+Vi4UUM9zpYtnseBi9bpxqJ+ff/fAafXhqibTYcphISQJMEsEhHM0rhx4yQ7D7gtfVkc06YhTUhZUg89pOa4nPbFvJ/9uLj4Ou17/ry6YM+bp+axTp503g/BLnarxb4P5jEXLlSBLNgOC6ZkSSEnTvR0Z8Ltaz0WXKVwk86ejchv5dq1HxtzoUOGKFcnquYUKOB5DIhiMGL1xhvKkrSvt1pzt96acHEuVcp9nJo11fu17/fhh77ncGvUUGJpf86CBXSNkiQC6SEoUYdUEDQn5pjELrD8Vq9eLdesWeOKPP7oo4/0daipizxUs8UXqhElxTkg6OSvv7zn6RB16bQ/BMRJNCCa3rEB3vvBTWqPLq1TB2USvfdFRKf9tZ0u8L7y/RCs8p//qOCRQOPwySe+5xV9AaHwJVCY40MwT/nyat933/XeJ0cOd5DK5587W3IIWsH4QDQREXvmjPd+uKnwN9dnMmGC8/vAXKCTxYo5UAohCRr0G4SLCy2wcOduz5/0vIuc5brLRyPfW2+9lWNINKtjgP6dwA2SuQ75tNeuXdMrJyXV6zpdzO+80/9znKyXnTs992na1Huf48c9o0KrVVMVapysEVCsmHdUo1MQyeHDgV1/gXBylzZs6P85t9/ufN6wUvHerPvu2eO5z5dfWj97ZxHEfKj9NRGZ6iS6ntWrnC1QvVpODs998R6d3gMEmOkTJGhQNcbaOBgLSq+ZRcTtWNNGsFy+fNln02USO6AxMxYUrDetPxSIQElCe9eWxOSJJzwvgBcvel8s7TRu7H3hxFygdR+4Ue37oHkv5sLatQscQAO3pNNrI1XB13MQaFKrVsLGAa5d+/FggYUqhBAw5DEGiua03mwcOuR9nN27fb8uRNS+v916Gz/eeYxWrvQv0AAeAh85j/xHJd60a9dOLxXntKAQuFOlG7i87Aub8hIUmPjkk09c5fnQjgxF6rGsWrUqyV73vfc8L4LIIQv0nCZNvC+emzd77oOu9fbAC8zbffFF4IhRfxZp1qzOkZF292uogohcPSfhDlUIfZ078gJhSSMPsUcPFSFqbnNyd8Ki9vW6CJCx749iB/bAIid3K4KQzPzAsmWFvHEjpChT/qMSe1mmbK4eiaHkSBYrVkx3iVoXXPBwIeS4xjb4buzcuVOvtYsUI+Tfzpkzx6+r/WaxJ83bXZxOOLk9kext3ceecB8MsMowHxbo9WH9IADF37Fwgd+wIfgEfaci4r7mSf0JoZM1GChi1+4aRgCMvzqnDzzg/bpIvrfv17mz89hgXLAdv52E0pYyQSEkvunbt2/ASD/M75QpU8bBHfWE174IoOG4xi7Zs2eXY8eOlVOnTvVg4sSJSVZ4IVcu7wvhxo2Bn9esmX/XKNIefM37OQkW3HPWqizBgiAYzDv6O/6RI965dsFaWcOHhy6EKNcWynsoVy74nEwTuDeDLXhut8zB1atqf7PajRVUsmGJNRI0uHO3L2btVOti76uo7qibeu2HEm0c19ilX79+Pm+o0Ng6qV7Xng4B4UiIECIPzdyONINAAghLFNVRbrZQN+YcYYUhQd3Xax08qFyT/o7jZB2hWW9SC2GJEt7HQAk4f8/Zvj3wHKEJXKZONyW//uoskHFxFEISAk6l1BDwYHd7IpAGidOe0W+p9RZW1gUh8rEwbrgJ2L59u+MNQiyDQvbwCiCKGAXtIYzHjx/XvxvwPiTV69otBrjGMHcU6hwhAlWs+9gb3ZqcPq1KpPm6cN/cnL1zGkQwvRLXrPF+DhLmQxVCzP/d7M0IhMuXFQvhh8VojwYNJSDKF/Z5XgohCeLL+4uHkJ05c0Zfjxww+4IAGfvzUXbNurz77rtRO1YIGpowYYL89NNP9fd68eJFvWIKv0f+yZcvnx5VfOTIkSR7DRSktl8QETCTObPvOS2nyE0Uebbuh7xC+z7LlwfX4w9u0ttuCyzIztWrnCNAIXT+noekevtzINjJIYRO+ZboeuG0L7pgBBp7OygwcOVK4BqqQYw3/ymJNSIrratuqrns2bPHVTbLnk6BwIcsWbJ4HAMNiK3LiRMnonKscubMqd8kYEE+HOrLZs2ald+jgC6/VHL06NHym2++0b0MSRUwg1QIJ9cZQvTbtHG7FBHliDZHH3/sfCFFBwnrcVHtxcn1BvGwRky6C02oYtvICbRaRkj4d+frCtmpk6r2grZLsI6CnUOzJ53bQaSk/Tlz54YuhL4KhuM7j2Ao3Nzgs7Vuu+su7+Ogo4b1WHivcNU6ddoIpj2Uk8VrBRV+gvi+8B+TeF6k7K7Nffv2ubavX7/eyypEM2OraxQh8tblwIEDUTlWmTJlkg899JB+c4ALOgKF2HUjMAULFnR9N5Iyod6XCJjAkoArLlC3h2++sZ+/coP6OiYEFXNdsC5hwaHsmtO+1hSG/fs9t6GNE6yhJUuUGxOCgvlAf0W3ffHgg97PgdDdjBBC/Lp27aoX2l+8eLGLefPmyaFDh8oiRYoYlr/vYt0oP/f6677H55VXgvUuOLfVMm84KlemEJIEgK7y1uXs2bP6PI+KBCvnZTHC4jPvBEuVKhVUUE20XdiXL1+upwZg2bRpE79HAahWrZps0qRJkqfWoE1PoPy+YGpe2t2eqHtpL98WCqiYYrYQQlUZlP1K6LnZa43acSpWnRDXaK9eahtEDoJnFUA7t99+uz5njhtjBPyE+r4QcQsrPdjP2cnyDJS8TyEkARJwn/MSM3y5ze0vvPCCzxSJUaNGeW1DvlgsjBtcxHj/Q4YM4ffIR/Toli1b5LZt2/R5VFjUyfG6qIkJ92ZCRQvWmVNkJubM/DXO9QUCSOwRmJs2hX4cCLG9eLcTTl0y0GYp1PJ0sAjhxoYHyJ8IWkGQHY6HZPtQ3pu9Fmvg75bzcYLJ3aQQEkcGDhzoJWawApcsWaLf4aG1jn1BEWXcKZpWkXXp0qVL1I5VXFyc9s9WR9avX19Wr15dli5dOsmKSEcyqDBkXxA5mitXrmR5fVhdcDGi6La/CzDmDxcv9rT20NnctzdAFYIOxjpE1CqiHJ0sOBTrRuJ+sEKBii2B6oW6/5+9rUict/+yeN7njtJklSpV8hA6zIvXrVtXL7KP/wNEBVu3L1y4UPeY4JjDhqmbikA9EVGlJtTP1+lG58CBkI7Bf1Libdl89913jnlfCIS57bbbHLd/8MEHXutQTispq4ekJKi5ancTY0HVFFwYEH1bq1atmP8+1a5dWx+Xzz77THbr1k126tRJ7t+/X1+Hhs7JeS6FCgntYi3kHXeoZHd0TsBvdFNAsIoZpILC0mhjhKa+wRwX4oaamzjutm0qZxHFrpF6gTk6vGYwlWDgDoQ1htxFzDWaEZFwncLFi/UdO/puO+Qr2hSdKmC9IqgnUOqEeeOA18L+yMvDWKg8y2YukcMNsz04BqDQhtV1am1SjvFHwA9EypwbxPwePge0fvJRBzRgUJSTqLZsSSEkN4mT1ZeQJdrdhBgn3AX36NFD9u/fX3eNNmjQQLd0MDdq3g3HMn369NG/C7Nnz/ZIn7h06ZL86quv+P/mN4o7uOoxwVX48W4IHIzAm13rlTu4h0vgYAH6el7NmjU95gsRje4ccHbz7ws3G4EifSmEJMFMnjzZ0eIJdjl58qTPf4BoAcEegwcPlk899ZQ+XnCV8rvjSYUKFfTvAwo1oJVXgQIF9OR6fLdQjJtjFDkgFsAUOAQ7+dsXbdnMfQsXLpxEUxPOxbWD6dNIISRBM2jQIPnzzz+HLIIXLlyQVatWjfrxOXbsmEv0b9y4oVs5mCvkd8eTe+65x/F7gu8XxydywHfbFLcRI0b43Rc3iOa+FStWTJLzwfytXQR//NE5n5NCSG4K5AwhX8gpEMbXPGIsiAGEHgvcRWZqCaqlbN68md8bB9C1/vHHH9fnB9HouUqVKhyXCCNdunR6FLgpcGja3bZtW9mrVy99GgTxAyNHjtSnC6z7ofhE4pfuc85RDJQjSSEkN11ODF0DEODwxhtvaHdeP7rED01WX3zxRTls2LCod4ea1KhRwzX3haABpAOgWgrGgd+XQKXG6umRpCjwzvGIPC9RsOkTYMqUKUlyHmjP5FROLVBELIWQJEmEaebMmWO2Cg/mBrEgQhStqbAgOITfDW/wPYE7DY2drU2ekZLD8YmcCOBQRBC0bt06Sc4FkbCh1ialEBKSRCBFAjmWM2fO1ANDOCZOCdoLPJo9Hzp0SLcsYjLncq3GDQ3tQi58tVBCM9pzGuZPy/A4d7g/7SXV4ApFxDTcoQiCQloFek0uWrRIT743q1IlNk5dQAYNohCSAMBtefDgQfnMM8/od3b+9kWUF/z/RYsW5dgFEMHmzZv7pGTJkjE/RmXLlnUJIGqyouB2zI4HSrX9bRG4vRrWwA6kKmy0bDd/+obH+SPoBVMBmAvEHLm/HOFs2bI55hkmFih+YBXBixdvKtWEF7NYAGHr1uXcuXM+78ZROPrPP//U90PwBy7oHENvnOqqOqWQcKxUviXmlc0FqRRoX4V555gbj002kXvKEEg0jn3Htu26xmKNVOFz/uHiykYBA2t3kVDLsqW4EKbX7oB+eUnI1x5Mhuof2YX863Uhl42N7QvRvffe63WRjo+Pd9x37969Hvuhtigv5s4gqRiWsy+SKmw8UkGxAcyrXr9+Xf9uffTRR7E3DrD6XrIJHh7/Ylt3XKN+eL4HiCHmfHEjgziBlAqQQyurceOCLzcXVkKYK5um4G9ppu2upH+tArnVa30coCdVt6ZC3jPRdx+wSActguwL/PpO+8KKsS5otMuLuO9IWuTI7dixQy8lhYopHJdgEqHj9I4dq1atis0xQEWV1x1coObPVmOfMDtvpEGgzRLm/+xBMUixmjZtmhw7dqzuOgWYI8T/RQRMEUS3EGbPol7rix3+93tlrdqvbLEwDzuvJOSwzomT0Ix1TvvaE+iRP8iLt/BpPSORHgv6MIJA86+xSkp1nwhbsjq4Qq9qjAvfc27ZsmXIUaPA2rM0JoWwTkUhB3UQcmwPIds3CCyE2Fa/snanndVzfeu6Qma13CHVriBk5VL2cHZlAaZJ7W0RfrRNyAzphGxaU4mJq1JCWSGn9RPykyfVfutnCTnpVs/Xx3ErllD7prP1JcuX00g6186tUTUhq5VJ2Dg1ryXk9rsC77d0jJBPr0xYoIx9QXkrp3lCe0FtzBfyQu4NLuR///23nhKA34igQ6PZXbt2cXxspHT3ibClgMYpixD+plE0vHNnEyKEgarQRK0QQoz2r1HiYgXi5SSEeXII+eQd7v1uHBPyv3eq40BIsW5ib/f+v78i5Ld71d9F8gn51HIh/zyo9ruk/Z41SIkWtmHd6WeE/Oy/7uO/+Yh67oG13ucIZgxQ2yFsp552r//nsJALhhkJnU2EvKI9fnCmkBdedu/TPkBDycwZhawQpwQf51g+Trll8Z7b1hcyb07P+VTMc7pKCo1VQliznLoZsIp+oLkZpwUXbXtzVMzbWBdYPMz1co4YxYKUiY0bN8pTp07pVWUOHz7M8bHlnoVL94lkBakRQzUQC/G4xk4jZWKrETCzXuMBjedtVuFhwyrEc9GSKH94zQ2iIlCbNm1ko0aN9PZjCKZDZRkU13Zq0NuuXTuZN2/e2BTCxtXdllif1kJ2by7kS/crYbILIS7mhx9S637cJ+SSUSrA5d831b4rx6ttw7sYd+IZ1OMfnlPbzz6vHn/1lBIKU7hgZRUr4ClwHzzuFsQampiUKCTkbd2EfH+rWvfAdCEHthcyZzZl5Z3fL+TVI0I+Ol+JFf6+dlTIjOmVlWseF0K77z71911jfI8LxA8ifv2YAq8FS9B6jnj/2BfHgdBi3a5lqjUK3h+eZ+4Ll24w85rZs2fXQ9edFrhCV69erbuu8CW3RveZS8zfuftIEEfQB+ZMSpQoIa9evao/fuCBBzg+FmK2+8QIP3OAofxc0Sge/u8Xbcm6d+/uJYgRMlWQRNE8BZX1hAv5mqlCFsrre47w1lbq8Td7lAWEdbCOzr2g/t40T22HBYbH+XOpx59vd4vkhlluQTDn++CWxXmYonHvJLUP3It43LOF+5wenqvWQbDNdeN6qnXYNrmPkGf2qccH16vtE3qrx3CrZsssZJXSQl58TchefpJfYd1efkOJdFxB5brFOQ1op45lWn9DO6nH43sJOX+oirI1LcJPtbvKkoWVVYgxhks2mM8Etf8Sunz99dfy008/1a1FgHqicAN+8cUX+sUM1tDp06fl999/rwfbwO361ltv6UEky5Yt09uyROPFDjcQqLKPvxGF++CDD8r8+fNTAC3EbPeJ8YkkhPjpEDnvG9YfUmOsQTQQyZidI8S8Hqw0XND/PqSExUkIYWnh8ZzB7udif1iH+NsUO1NgCudVjz/UrLvXN6i/G1Z1P/f7Z9U6iG/R/OpvWI0ZDJFdYRwPYmM+B2KNdRAkc93G2WodLED8hjBjPjFjek8hnNDbcz7Rv2tBWZc45s6lQqY1Gmy2a6COZbpFYSUe3aT+hhjC4jSFcM8K9/EwBtZxC+TWgNsuJRZc9FBoOdrKyy1dulRu2rRJrlmzRq8ug/eInoQUwMDBWlHffUK7ORYzNTYbLtB7NFZp3K+xzkicxxTNFsN1qt3Yi+cM1+gnGt9pfG3skzay3juiS1FNyBRD5CbHdLAM3J792ioRwYU+vqq3EN5lWGhzh7ifB+sQYgHhmN5fbTeFtGAe9fh/Twj53mPqb8y5YVubeuox3Id47biCbpeoeWy8jl14V9jcr2DdDHfEKdabQmqKl5MQBhPFCquvVnllLQ/p5A6WwbHgOtZLUg1TLlyILoQQlh9ed/k4Id96VM0r9m2jLOfyccG/PsKYcReeEgusxWi60KHbBCJqgbVv4/nz5/Uq/RRAT9h9IvYqWZlCiDnEmBTCW5oJ+fgiFeAyuKPK48OFHvl6diHs1Eg9RvALBGfeUDVHiHUICIFLFH+/u0W5NREYY+YGrpqs/sb83NR+ak4Pj88Y1iSiPfEYc5DmufU33JCwzOxCOLq7e12Hhm5rctFIJcgIVPnjVRV9agrhxBCEEEE8ZtDNrweUaxTrm9VUc6LZjRJB5YoraxqWseli7hgv5OKRaj/T3bt6SgJuTtKk0XN8zp49m6xCiG4V0Rp0U6hQIbl27Vr9fcIVxAuhJy1atNDnCiGGTZs2lXXr1tWDjapVqyYrV66sFx6A+zwma4/6S6+YYATXlA2Pc0LJNFRUgou7S5cuel1ReEDQlBqdaeASxWOU0bPmGrZq1So2hRCWzo1jnkEgL6xWFg4sK0R4vvyAJbx6oee+CITBb7hNfUV3wmJCmsOxTe51P+93iyaeV7qIsiwXWcrvlCmqgl5wPua6O0er57W1VXKAFQrL0/q6qIiDtAmINh6P6BrKF0nIqqWFbFDFbWGaljPm/Oz7I2UDEbXIb8ySSVmF+I33lS/nzX1GqAaByijI6dq+fbs+t4eAj6Ra7r777qi9aKGwMC4SL7/8sjxz5gwv4iGWojOXrVu3xs7YrDSqxzxqlFGbpTFPY5nGLiOVwvx5PDzO2dqhPhQQTBazrlG4JWEZIiilcF7PbRAjWIbWdQg26d1KiYQ5x4ioT1NAkMrQubH2j1VEWYmt6rpFBNGY2GbOte1e4emO9ArprqDExJq+0aO573QEzDXCfZnTcs4IkEHwS5ZM0fPPCYtt5cqVXheodevW6XfygcAdP1wiiBBE1ZAnn3xSPvvss3pEarT1KURh8nfeecdVl9VcXnvtNQqgzYpAtZEVK1bIV199VR8jRIyi2hFuwPbs2aMXgsd3BQnbMTEunUIMlgmTJHt8jqEIIOYJUZw7ZqNGUwKkO8DdCSFEpKVXAnQG5YIMRrgQbZo9iErmCMhJSBI9LDwIPdy0cBfDSgw2JxCWJNI+fG2HaGOuFIUB7p4g5MiubhdsMKBMkn1BrVJe1D0pX768XlnmkUcekXfddZcelduzZ089VYXj46MyUr16ru+UGW0bk2wPQQQPh9fnt3DhQleOIK4VaMMEt3ePHj10YDXCFQq3NwLKIuQziewvFKIrfzug3JdIXYAI/vSitwW6cLia28N2pC/0aO58PATiINke+8F9CyHx9dpmMI3ppjUtVF/Aur1/mpoLdUrix1wk8hgr+Al+wTwlUinMYCHTPQpxnNJXvXdfx0aATjBjOmDAAC8hRGQkL+TBVd5AygjHwhvUtoUliMVsYhyzEbY/GiJ3WmONBkpAoh7yBo2FGtr1Svxk7POnRo7wyp9Fi6Uom++P7DcA1ytSKCAuSCXAXKPpTnVF9xX3Fgbsn84hJNla3cYE+YhOc3f2/cwAHTt4HQjqP4edRcoO5i8RFAQL1m7xmkFE1mAZ1B79bm/g4x7aENyYlilTxtUdwFzYdd2HdZ4hgx4hinJrefLk0fMIUW8Ud8Mx2WLIh2sU9UWxYA4aARUItoBLGdG2bdu2ja0xyW6x9g742W+iZb/hyZ/+gEhPVI/xtQ+KbCAtAtWC+vbtq3eir169eqQW34j+L17LOs7CgDlJ635wcTrthyhVr+T0wd77IWUjfTrP/eCSPPJwcAJoxxrVao1ivRlKFQluzKzzhOgmjrtAXtQ9wUXAfsNgXXbv3s1xMlzIWE6cOKGX5DLXN2vWTI8kRnJ9zI3LGUPgfhWqLZPTPrUsQrguec8PwmbO8xUsWNDrxgYCiehop3lBuE4RJRxhMQHR/6WrVNJZFBCYY93PzGe0A9erPVEe5eLs+5kJ8K67qmye9U1DBSkT1vlMBB3drBDipiDYcUOkF0LbeTH37SJCyDgq5yBUHHNeL730ku72Q6J4sWLFOE4G+B7BNYr51A0bNugBVTGda2mtL3q/j31GWfZZlrznZxW54sWLe2xD3eJgAmXGjx/vVceYQpiSVQ6yOYuCPaDm+A7fAmKdt4PVZ3dRgoXDPSvIvLrO9/FQ/WbzAlWvFAEzZgUeK6jTaj0/FCYIRuxwblsWqpqm9m3Wyjkk8Zk5c6Zeu5Vj4QkS6J26T0RAMebkiRrF/KBZTxSR6e2MqjLmz5DkPT+roFmDv+AytVuCiAwdM2aMfiNoF8OuXbtSCMMJJK/bRQH1SV13rKX8i4tVQJrUcN6nrqW9EyI1fR1r6+0q9cJ6fk7u0/WzPPdBYQJ/5wjhHNPDHe16crf3PoM68KJMkp8vv/xSr0+LurS//vqrXpIOy86dO2N3XLY5RIiiS/1127pLItlbM1m7R8AVaq5H0QOrACJH1OoCRTF1WP5WMYyApryxI4RHN3mLwnP3urcj4d6fyFijR532RSJ/aiP9AUUDzI4YdlCr1On8ftznvS8KfVv3MQtxOwEhtc9PHtrgvR8q7fDCTJITBF9hQXNWBMpgQSDG0aNHY9t6zqWxP4j0iUnJf26miFk7hoD27dsHtPYgnJgaMPdDShGFMEx4ZJ63KKCLg7ndbMzri5fu9y8wiDY1t3dp7HwMVMfxdX5OEaV2N6YvIUQELDpyOKVaWPdDugUvzCS5MXthTpw4UWbNmlVeuHBBr3eLdl/oWBLT45PKELrzDgL4pcYtKXNe1lZK1kA5qxCigIav56PbhLkfkvAphGGCXRTMYBSULAvkFjXz8Myk/CuH/bsczZZO9ohSVNrxdX7IbbQ/p3vzwEKIXEdfCf0o7o3ScUgrWTtdBQ3xwkySG6SWIIAIFWTs84XmOmLMEXbWQLu5woZIptC5INDFFDI03nWljVWv7lqPZHrkE/o6BqxJ04UaATmHsfEla9/AWeCQY+grWtQOyrChGox9PYpgWyu3vLHRex9YnP7OzywWbgXpEoGEEN03eBEh4c5DDz2k967Ug83Sp9cfv/DCC7J06dIcHyeQUtFNo1DKvD7cmabgISIaXeaRF4uC/QiMMbfB1Y2C807HgAfAyaqkEKYgZl9CO6hZ+vVub2Ezq8tYQa1RtEGyr39ns+drOUWAohOHv/NzCmxBsQAKIYkW4BblOFjAje5jxjyh3QU6TLi701dP/nODa9O06KwgMhQpQ9Z1EEqUWYPbFEX8EUCD1Cuz+8S8efPoGg0nnNIJzJZOVtAVY+ZA7/Vot/T2Zu/182xpGF/s8B9sE2wwTzBzhO9v5QWFkIgDEeZ/WOYDb2j0smxvZNl2NGXOEda6da4woQwePDhZzxutoRLQBDx2vnxOQS7W3n4mqENq9ki0smuZmuuzr7fPvT2/ynufbYv9n9uOpd7PGXULhZCQqORBh+AY1BbNatnne4tIZkmZ80QyPdyk1m7zoYAqM0ipSJbCKZUq6YUaIIJ4bfRIRF4vHuP80W6uQIECFEIUvA40D4g0BgTQlCzsvQ19De3rzObCVlA4277f25v9n5vZYNifFekkhB88zosKIRHHMYsAvmv5e6plnycs6xunfMATKiVVqVJFxsfHyw4dOsjevXvL4cOH64LjSyiTM6G+TZs2erK/2R0D54WIVTQhHzFihH6ufgo4xM6Xb2D7wEKIwBmVC+PuVhFsfqEJukA47euvXZNT7dKX7qcQEhJ1aDfa4i9LikQDi+ChG4XZDMBaYm1KZEQH58+fX88brV27tl5jNiXK6KH3KYSZrlEfoIefP1GDm9RalBpzhYGEML6q9+s0qua8L0TL3h4KHSWWjXXOI0RKRd6c/oXwQwohIZFFDps1iHXPWtb1NdYNsaybFZ7vBVGkRYoU0ZvvxsXF+U2nSC7q1q2rW60UQj987Cdx/lVbhffFAarNoO1Sah/NdPfd5/wcWJmw9FBmDdGmTjVLfdUvpRASEiWctIhcRSN30Px5x9jnPsu63uEngJ06dXK5Ia0gvxBihH1S+hwRvRpko+zY+gLecZtv0elvi9L01ZbJGkXq63VQysxpTjFUkMhvlk5zEkJ7YW5CSARgnf/7nwZSpT6wrOuvcdHyuEL4nDt6cA4dOjRgoMyUKVP0NIzkPj+UeEMqhxk0gzQOzBMirYNCaMkndHJDXnhZ1Qi17w93ppNAnXrau7anHVhzTlGpodKrpToeqtcwj5CQKKCUxs+2qNHztnQK82dfeJ07+kgGGzWKhPvkbsWEuUm8NsQPPUNRCm7GjBm6MPpJ7I+9L+G6Gd6Cgi7vTvs6pVEAdIII5rUaV/ff3slatBuBNwfXe2+rXlYdq1gBb2FFAXBeWAiJQOAO/Uf4L7j9u0aN8LIGrYn2Q4YM8YgYhSU2evRoDzFM7sbLED+8LgTRXIdEf6wrV64chdAELZA+3+4WE6RV+Nt/rS0d4umVvucGHb88muU4obfqOI8uET8b5dROPyPkM/eoaNYMhnUJKxN9CiF4qGmK17Yea81Ut0WLlIxCeXlBISRiKaHxqMY14Vx0u0J4nW/ZsmVdAgcLC8ExXbp0ca2DyxSuybZt27rWIaUhOWuNImgHrztq1Cg9kAel4fr06aMXB/ATzBObX0BEY97WzTnq04luTYVcOkbIPq1DE0FfOLlhPao6FFFFs32du7VRMCEkwkGt4niNwQaoPJM6/M6zXr16XhVjkDBvllMDSFrPkiWLRyCNr3qkSUW3bt283LRo/cU5QkIICRfqaKAt28dG7uAmjfThf95WS69jx47uHO2BA13rkWiPdag/aq6rXLlysgfM4DW7d++uu2br168fKIqVX0pCCElWPnZwhXYO//NGb0lT3NCJ3uXBKl3aowh34cKF5Zw5czysRKZPEEIIUVR0EEGkSpQO/3M3598AhA6Wl7nN2p7J2s8wufsRMn2CEELCnWwWATxnRIVmiIxzRy9J63ygde6vYsWKjikUCFph+gQhhBBPTlvEsFFknTsExhS51q1be2yDu9QuhCi/xvQJQgghnsyxCOELkREo4ypKUrSoyypERwfrtrRp08oePXroKRNIpkeQSkq5b5k+QQgh4UxmjbMWMTyigYL/mYxtIItBmvA7f8y3IUUBQTK+5ulS8vyYPkEIIeHOCo3rQgb1g9JrnThmTJ8ghJBky5UT8romUlIToRs3hLxwQciTJ4V8/30hjx4V8tgxId97T8hvvhHys8+EzJEjAa9zIUgRNH9283MJRQQxP4jI0VtuuUWftxw3bpweLAO3LoWQEEICULmykEeOCPn990JevCjkv/8qUXTixx+FzJw5Aa8zX3h2l/D3A9HsHh5jU7JkST1NAqkT/kA9UkRqTpo0SbZs2TJZzxGNge1uUZwTkv5RK5VCSAghITJ5shK9XbuErFABaQJC1q+vLMXLl4XMk+cmjo/5v4zG3KAvwqjUGopsB9t5wkqePHmS7Rw7dOigvyZEG+kS6dKlY0I9IYTcDNu2KSFs2NBz/bp1aj1cqYnyWhkNwngs2rVrF7IIIqE+OVsxVatWTX/dnj17hpLIzy86IYT4YtkyJXizZ3uuh/vUSSBDAn0J0Vz7a6F6ECKA5jONDRph2FkGASeVKlWSjRs3dtGoUSMXCEyxiiDSKJKjOS+iWFEHFUKN3MZ58+bpr4+WUHjcvHlz3WVKISSEkATQoIEKmvnpJyHvvFPI9u2FXL9eiSDWJWiOEEwV/vsRIlq0S+SNV5MmTTyqz1SvXj3JX3PmzJkBLVN0w0BlnEQVwqefVl8E60Ty/v1CZsnCfxxCSHQxfbqQ1655Bsr8/beQXRIqVGjs/a9N+P51SKnQXkOUj7zx6tSpk0uApkyZEih14abJlSuXXtg7b968smDBgnrpNyTTo/g3KF68uL+I0YQJYa1abuErWFDI+Hgh//MftQ6/+Y9DCIk2ECQzZ46QDz8sNOsCTWoTeKx0tvSJdzRGaxQSquZoG40Ttu2pImusIEZWawyPk+u1IXydO3d2BejAZYsUikQvuj1okBK93r0917/yipCXLiGPg/80hJDoI2tWIfPnF7JSJSHbaILVq1cCXKO1LCKHucGsDvsU1/jDsl+ZyBkjCFGNGjX0FApTCKtUqZJsr48gGcwRopyaWWMUYJ0fyzThFiHuiqzrjx9XCab8hyGERAtIj9izR+UMOuUSjhsX4jFHWgRunZ/9nrDs1zsyxio+Pt5xfg5WWXIKIdyxqHs6ffp0veYoCn/jPBI1oR4WH0Tvww/d6+Li1Jdi8WL+4xBCoocVK9S17bffhPzyS5Vo/8UXQh48KOSGDUIWLx7iMQdbBG67n/3WWPabHBlj1bVrV0ch9FWTNClo06aNHhhj1htFlRmzEDdaRSVqsAwSS1u08FzXrJmQ6dLxH4cQEj089JASwmnTEumY1sa8v2rk8rHfu5b9WkfO3KDZpgmRo7DM0NU+Oc8hf/78uhDiHAYNGqSvM1sz+UnlCP2FUFtv/HgVSWUFFRjgNuU/DyEkWmjaVKVPwCLs2jUBx0jrsO6gReTWOmzPbosgzRdZY4ZqLkkdKerfnZ1HM9Yq6O5RPIYAQgwTNVimTx/ftfd2szgsISSKQN6gdX7wXc1SW7pUyNtvF3L+fD/BMtmNYBikRVy2cc2WNnHZSJUwseYXXhFhX3EmCgj9SWnTqogp3B2Bnj2FPHFCfUlat+agEkKih1df9X3jD1q18vHcKkIm2k8zfg5hJ4ROFCmiEk6ffZaDSgiJHooVU16wHj2EHDxYyAkThJw7V8gFC4QcOFAZBj6fP0rjdY3TCUCzQsVvGnuN3EN+FuEthOnTqy8Gyg2dO8dBJYREF4iKL1RIVc7C9S5DBiEzZRIyWzaODYXQoFQpt5tg714OKiEkepg507dbFCUmhw3jGFEILXdMCS45RAghYUqHDkJ+8omQP/wg5B9/qOpZ+G12sW/UiGMUs0IIvzj846gus3atkFu2qMRT1B3loBJCoh1EyEMIUYOU4xGDQpgmjZDvvefsKsAcIWuNEkKindWr1TWve3eORUwKYZ067pzBdu2ErFtXyNq10XpDyPr1OaiEkOihfHnVnHfqVCGHDhVyzBhVShLl1nAdrFyZYxSTQoi8GXwBZs3iABJCopv77/cdLIO+rPSAxagQFi6svgQvvqjya/r1Uzk2SKZHpXYOKiEkWkDP1UmThLz7blVkG13qJ05k8ZCYF8IRI3zfIaH8EAeVEBKNIG8QU0G84acQ6lVkzDp78+apZPolS4Rcs0bIUaM4qISQ6ALW34EDQl696r7pR49CJNlzfGI4fQITxmjFZD5u2FAF0XBACSHRBHKk//pLiR8CZPbvF/LXX9VjRM+jygzHKQaFEJVkUFFh5Ur1GJaheZc0ZQoHlRASPQwZ4g6MMdehvBqmgbC+GQtix6YQIjAGX4BevYQsWlTIK1dU5YVvvhHy2285qISQ6AFpYbjePfmk5/p169R6FOLmOMWgEKIFkymEqCpj3hVt2qT+RsI9B5YQEg3kzq2a8ppWIeIg4A27eFF5xlhZJkaFMHt21bH5l1/Ub/TrwvqdO9WXBW4DDiwhJJqCZS5f9o6Sv+MOjk1MB8ugxihcosePq8lkiN/Jk0Lu28dBJYREH8ifRrT8E0+o8motW3JMYl4IQcaMHEBCSHSDvEHEP+zapQJn8ufnmFAIDapVU/VF4R9HGoX5G62YSpRQd0+owZc6NQeZEBK51KjhmT+I6aC33lI51Eysj2EhRHUFsxdXIObM4SATQiIbCF7PnkI++KCQn3/uvr6hLyHzp2PYIkSZta1blbvgiy/cXwzUH0VkFeYKH3uMbgRCSHTMD/buLeTy5UI+/7yQv//uvuaNHs3xiek5QpN06dSdEr4UmzdzUAkh0QOCYqyuUfDzz+qmHylknP6hELrInFnICxdUSgUHlRASLdSsqa5tpgh+953qPpEzJ8cm5oVw/HhVaeGNN4T84AMh339ffUkOHuSgEkKiiyxZVMs5NCM38wnx+9FHmTcds0LYtq2nmwDVFS5dEvL114Vs2pSDSgiJLho0UHOB6FSPLhTW6x+61nOMYlAIUW0dvnF8OfLlY4dmQkj00qKFSpmwR8SfOiXkggVCpk/PMeIcISGERDGlS6veg4iCRzpYly5CFi/OcaEQEkIIIRRCQgghJPL5P/D8o4wsaqFdAAAAAElFTkSuQmCC" width="450" height="225" alt="" />

### The Koran

```clojure
(word-cloud "The Koran")
```

<img src="data:image/png;base64,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36QQEhJ2zJgxQ06dOqWthl36i/MOr+Poc7d792759ttvJTIy0sev6Aayb98+OXz4sNXNILbnQoeDt956y3qsCxcuCC7//POPvPfee9KiRQuf98mTJ4988MEH8ueff8qzzz5rf4mnkN69e8s333wjZ86cEedy5coVq58eP1cSV6KijBBiTZVCSEgY0bhxY3G9LF261O148eLFowULlz179nhZj9u3b48+fv36dZ9iitqXy5YtkxtdcA46Jbjed/z48W7nYPuHH37w+xiXL1/2egxC/IG1PzTidbpMIKH+l19Mugka9cLFfOaM0j/eQjqylh8kIfFl4MCBbiJy8uRJt+OvvPKKl9C4diRAux6In7/j5oumnf4y+U9u9gLr0PX+H374ocT10r9/f36+5KbAmmlskaKusB8hIcmQVq1aeYlI7ty5rWPoSnDo0CGv42jR49y/S5cubsdgPTpdDkwYf1HL7ep5OXfunGzcuFHmzZtnuUZdL2vWrHF7jZ9++mmsogfX6N9//+22D62E+PmSmwERtsjBnDMnphhBp04mCKlhQyW1ayupV8+cR9coIckQdCz3vFSvXt06huATf+5L5/4zZ850O7Zy5Uq3YBiInS+LD01hnfPWrl0bq4h9/vnnPl/Hjh07JCoqymopNGfOHLdjkydP5udL4oRTYACJ81wjJCSMQM85BJi4Xjp06GAde/75530KEKxE5/4IonG9jB492iUvbqjXffGYnq9h//79bue4Np0FEFfPy5YtWyRt2rTR50ycONHteOvWrfn5kjhRoICSzz5zr9FKISRhARqSIpLxqaeeslxwsGCw7hVOY+ApZuhuDreop0C5XrJnz24FpHheypQpE/24iPR0vfz8889u4uVw/Phxt/M8o0c9hfCPP/6QvHnzeqQMlIl+HESSxqepLCFIRfFsawWBTAJpJfzwSPyoUKFCdOj977//LitWrLAEEaH54TQOS5YscROat99+W+rWrRvrulyjRo2s9AXXy4EDB9wed+/evW7HR40a5fP5Dx486HZew4YNYxVCuEN9PU6qVKmsKFfObRK/XFQlv/2m5NQpI37Yh4o7cJdevapkxAgKIUmGpEuXzlrjOn36tJWjNm3aNKlYsWLYjcMTTzzhJjRff/21lVvoetm5c6fb9ogRIyzBdL289tpr0Y+ZJUsWr2hS5Bv6en7kL7pe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" width="450" height="225" alt="" />

### The Book of Mormon

```clojure
(word-cloud "The Book of Mormon")
```

<img src="data:image/png;base64,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RX/6Scm//7rWffgh249QCAkhsR4UPcNd8EqV0kH2t96qvUKrV1dStKiSNGn0/gsWuO+P/KNsR0IhJITELLNnuwtb/vzx758zp5IrV1z7P/QQ25BQCAkhMUzXru5C2KZNwq85etS1/44dbENCISSExHQSB3ch/OQTJenTx/8ahFPY++/axTYkFEJCSAyTKpWS335zF8PnnlOSIYPv/UuWVHL5smvfJUvYhoRCSAiJcbp39/YS/fJLJR07KilWTIdIFCqkpHdvHVjv3G/YMLYfoRASQlIAW7b4D5347z//hXmLF2fbEQohISQFgBEfRoHBBNTffz/bjVAICSEpiEyZ9JxfQgKIoPrHH2dWGUIhJISkUFq2VLJqlZIvvnAl2r54UclXXylZs0ZJ+fJsI0IhJIRcIyAXaZEi2ruU7UEohISQFEnz5kpWrtRzfhUqsD0IhZAQcg1RqZL3HOC33ypZvlxJhw66ViHbiVAICSEpFowC43OMQfD8G28oGTxYe5ayzQiFkBCSoqhVS8mlS4GFSyCe8L33lIwcqSRjRrYdoRASQlIIZcsqmTZNyZ49gYvijz8qGTTIVZ6JEAohISTFxBKiFuGcOUref9+95JIvNm1imxEKISEkBZM9u5IBA3RMoT8xRD5SthWhEBJCYp60aXVF+rvvVrJokZLdu70rU3gCUyrEku1HKISEkJildm0lr76q5Pz54HKNnjunpFMnth+hEBJCYpgyZYITP3D8uJIZMxhOQSiEhJAUQP/+8Yver7/qvKMTJypp105JgQJsM0IhJISkIIoWVXLhQvxiuHOnLtKLOUS2GaEQEkJSHCVKKFm/XleYiE8Qf/5Zydy52pzKdiMUQkJIiiNrViXduinZsEFXn49PFBF837gx24xQCAmJCjJnzixTp06VadOmxUv79u3ZXkEE1kMUt2zxn20GKdeGD2dbEQohIREnQ4YMptB17NhRcubMaVKvXj1zXf369SVXrlwmadOmZXslAKrOFy6s5JZbtCPNI48o2b/f/8gQ84sZMrDdCIUwhLil2l536/fccw87AAmJwoULm33opptuiluXKlUqmTRpkimObKP4ad9exxEiLCKhlGqenDxJJxpCIQyJIUOGeAnh6NGj2QFISGTJksXsQz179oxbV7BgQXNd69at2Ubx0LJl8HGE9kgQYRUFC7INCYUwJO699944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" width="450" height="225" alt="" />

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
We can get an even better sense of context by just sampling the sentences that match our queries.

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
A true Brahmana goes scatheless, though he have killed father and mother, and two valiant kings, though he has destroyed a kingdom with all its subjects.

Not to blame, not to strike, to live restrained under the law, to be moderate in eating, to sleep and sit alone, and to dwell on the highest thoughts,--this is the teaching of the Awakened.

If a man&#x27;s thoughts are not dissipated, if his mind is not perplexed, if he has ceased to think of good or evil, then there is no fear for him while he is watchful.

The evil done by oneself, self-begotten, self-bred, crushes the foolish, as a diamond breaks a precious stone.

Let us live happily then, not hating those who hate us! among men who hate us let us dwell free from hatred!  

 &quot;He abused me, he beat me, he defeated me, he robbed me,&quot;--in those who harbour such thoughts hatred will never cease.

Or lightning-fire will burn his houses; and when his body is destroyed, the fool will go to hell.

A true Brahmana goes scatheless, though he have killed father and mother, and two holy kings, and an eminent man besides.

All men tremble at punishment, all men fear death; remember that you are like unto them, and do not kill, nor cause slaughter.

Pleasures destroy the foolish, if they look not for the other shore; the foolish by his thirst for pleasures destroys himself, as if he were his own enemy.


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
Moroni :   For behold, their wars are exceedingly fierce among themselves; and because of their hatred they put to death every Nephite that will not deny the Christ.

Alma :   But the law requireth the life of him who hath murdered; therefore there can be nothing which is short of an infinite atonement which will suffice for the sins of the world.

Mormon :   And it came to pass that I did speak unto my people, and did urge them with great energy, that they would stand boldly before the Lamanites and fight for their wives, and their children, and their houses, and their homes.

Mosiah :   Yea, they went again even the third time, and suffered in the like manner; and those that were not slain returned again to the city of Nephi.

And behold it shall come to pass that after the Messiah hath risen from the dead, and hath manifested himself unto his people, unto as many as will believe on his name, behold, Jerusalem shall be destroyed again; for wo unto them that fight against God and the people of his church.

Alma :   Then, my brethren, ye shall reap the rewards of your faith, and your diligence, and patience, and long-suffering, waiting for the tree to bring forth fruit unto you.

Ether :   For so great had been the spreading of this wicked and secret society that it had corrupted the hearts of all the people; therefore Jared was murdered upon his throne, and Akish reigned in his stead.

Alma :   Therefore, whosoever suffered himself to be led away by the Lamanites was called under that head, and there was a mark set upon him.

Helaman :   And it came to pass that Helaman did send forth to take this band of robbers and secret murderers, that they might be executed according to the law.

Jacob :   And it came to pass that many means were devised to reclaim and restore the Lamanites to the knowledge of the truth; but it all was vain, for they delighted in wars and bloodshed, and they had an eternal hatred against us, their brethren.


```

<span class='clj-nil'>nil</span>

The Book or Mormon, on the other hand,contains a mixture of sayings and warnings against violence with actual violence in the form of a story.
This is very much in line with the style seen in the Jewish Scriptures and the Bible, which are presented as a mixture of narrative, dialogue, and proverb.

Going into this, I thought the Bible and Jewish Scriptures were going to have the most violence by a longshot.
In some sense they did, but only because they're the longest.
When accounting for the relative lengths of the texts, Dhammapada and Book of Mormon came out way _way_ ahead of the others.

What really surprised me was the number of violent sentences in the Book of Mormon.
Members of the LDS church have a well-deserved reputation for being nice.
Actually, the kindest people I know belong to the LDS church.
 
In the end, I think this analysis told us exactly what we expected about violence and religion: pretty much nothing :).
At least we got some morbid word clouds out of it.
