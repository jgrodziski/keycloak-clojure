(ns keycloak.cookies
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.logging :as log]))

;(set! *warn-on-reflection* true)

(def HTAB [(char 0x09)])

(def SP  [(char 0x20)])

(def WSP (distinct (concat SP HTAB)))

(def ALPHA
  (map char (concat
             (range 0x41 (inc 0x5A))
             (range 0x61 (inc 0x7A)))))

(def DIGIT
  (map char (range 0x30 (inc 0x39))))

(def DQUOTE [(char 0x22)])

(def tchar (distinct (concat
                      ALPHA DIGIT
                      [\! \# \$ \% \& \' \* \+ \- \. \^ \_ \` \| \~])))


(defn expand-with-character-classes
  "Take a collection of characters and return a string representing the
  concatenation of the Java regex characters, including the use
  character classes wherever possible without conformance loss. This
  function is not designed for performance and should be called to
  prepare systems prior to the handling of HTTP requests."
  [s]
  (let [{:keys [classes remaining]}
        (reduce
         (fn [{:keys [remaining] :as acc} {:keys [class set]}]
           (cond-> acc
             (set/subset? set remaining) (-> (update :classes conj class)
                                             (update :remaining set/difference set))))
         {:remaining (set s) :classes []}

         [{:class "Alnum" :set (set (concat ALPHA DIGIT))}
          {:class "Alpha" :set (set ALPHA)}
          {:class "Digit" :set (set DIGIT)}
          {:class "Blank" :set (set WSP)}])]

    (str/join "" (concat
                  (map #(format "\\p{%s}" %) classes)
                  (map #(str "\\x" %) (map #(Integer/toHexString (int %)) remaining))))))



(defprotocol RegExpressable
  (as-regex-str [_] "Return a string that represents the Java regex"))

(extend-protocol RegExpressable
  clojure.lang.ISeq
  (as-regex-str [s] (expand-with-character-classes s))
  clojure.lang.PersistentVector
  (as-regex-str [s] (expand-with-character-classes s))
  String
  (as-regex-str [s] s)
  java.util.regex.Pattern
  (as-regex-str [re] (str re))
  clojure.lang.PersistentHashSet
  (as-regex-str [s] (as-regex-str (seq s))))
(def token (re-pattern (format "[%s]+" (as-regex-str tchar))))

(def cookie-name token)

(def cookie-octet
  (map char
       (concat [0x21]
               (range 0x23 (inc 0x2B))
               (range 0x2D (inc 0x3A))
               (range 0x3C (inc 0x5B))
               (range 0x5D (inc 0x7E)))))

(def cookie-value (re-pattern (apply format "(?:%s([%s]*)%s|([%s]*))" (map as-regex-str [DQUOTE cookie-octet DQUOTE cookie-octet]))))

(def cookie-pair (re-pattern (apply format "(%s)=%s" (map as-regex-str [cookie-name cookie-value]))))

(def semicolon-then-space (re-pattern (str ";" (as-regex-str SP))))

(defn- ^java.util.regex.Matcher advance
  [^java.util.regex.Matcher matcher next-pattern]
  (doto matcher
    (.usePattern next-pattern)
    (.region (.end matcher) (.regionEnd matcher))))

(defn- looking-at
  [^java.util.regex.Matcher matcher]
  (.lookingAt matcher))

(defn parse-cookie-header [input]
  (when input
    (let [matcher (re-matcher cookie-pair input)]
      (loop [matcher matcher
             matches []]
        (if (looking-at matcher)
          (let [matches
                (conj matches
                      (let [mr (.toMatchResult matcher)]
                        {::type ::cookie
                         ::name (.group mr 1)
                         ::value (or (.group mr 2) (.group mr 3))
                         ::quotes? (if (.group mr 2) true false)}))]
            (if (looking-at (advance matcher semicolon-then-space))
              (recur (advance matcher cookie-pair) matches)
              (when (not-empty matches) matches)))
          (when (not-empty matches) matches))))))

(defn parse-cookies [cookie-header-value]
  (->>
   cookie-header-value
   parse-cookie-header
   (map (juxt ::name ::value))
   (into {})))


(comment
  (parse-cookies  "X-Authorization-Token=eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJQMG1PV3I5QndQMHN4VVZwWXR6czJIcnJsUkk1M20yQTJKZnlndFVXZmE0In0.eyJqdGkiOiJjYWUyNzhmMC0yMzkzLTRjMDctYTc5Yy1lNDE2MjU3MTYxNmQiLCJleHAiOjE2MDU3MTM1MDQsIm5iZiI6MCwiaWF0IjoxNjA1NzEzMjA0LCJpc3MiOiJodHRwczovL2xvZ2luLnN0YWdpbmcuZWxlY3QucmUvYXV0aC9yZWFsbXMvZWxlY3RyZSIsImF1ZCI6ImFjY291bnQiLCJzdWIiOiI4MmIwYWM1Mi02ODhlLTQzYTgtODg4OS0yOGJkNTEzMGU4NTkiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJkaWZmdXNpb24tZnJvbnRlbmQiLCJub25jZSI6ImNhYWMxYmUzLTg1ZGEtNGY2Mi05M2IyLW…hbWUiOiJqZ3JvZHppc2tpIiwiZ2l2ZW5fbmFtZSI6IkplcmVtaWUiLCJmYW1pbHlfbmFtZSI6IkdST0RaSVNLSSIsImVtYWlsIjoiamdyb2R6aXNraUBlbGVjdHJlLmNvbSIsImdyb3VwIjpbIi9lbGVjdHJlTkcvaXQiXX0.P-K1UFiBsx90V4XTWw9Bx6gCt4TtBrGijbLzLuVhXS3BrvjvisI4No5C-zQ5JTsPhmMAzwDXy5P18WnmwIFoydDONRAaGFozIAIatzET500dPrykkOmcw8aNKbciviSn4JY9omBX71uQwzgVJklLQLk2KVqL1dmjsJ_vEu3qUSeXc8clowSkS-poPUiZ8ZZvohrQlDkWs8mCgyfkMooOIxQgbS_l7Lkddh7S0Db_DaSBGyhBWCluk1bkwFcWPbioL5Be8EDRA1yKBonE0jkMadVJv2I6eMgZozv0vjOrxo1Fasn-Yg0Ec_SoieYpj8l4XGRTA_Xg1P614dIqlO71Tw")

  )
