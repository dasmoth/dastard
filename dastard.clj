(ns dastard (:use compojure clojure.inspector))

;;
;; Config. management (must be a better way to do this!)
;;

(def db-url  "jdbc:mysql://localhost/dastard_test")
(def db-user "root")
(def db-pass "")

;;
;; DB library (factor out?)
;;

(def *db* (org.biojava.utils.JDBCPooledDataSource/getDataSource 
             "org.gjt.mm.mysql.Driver"
	     db-url db-user db-pass))

(defmacro with-db-statements [statement-defs & body]
  (let [con (gensym)]
   (letfn [(make-body-recur [sd body]
   	    (cond
              (zero? (count sd)) `(do ~@body)
              :else (let [[st sql & rest] sd]
	        `(with-open [~st (.prepareStatement ~con ~sql)]
		  ~(make-body-recur rest body)))))]
     `(with-open [~con (.getConnection *db*)]
       ~(make-body-recur statement-defs body)))))

(defn db-query-atom [sql]
  (with-db-statements [st sql]
    (let [rs (.executeQuery st)
          val (if (.next rs) (.getString rs 1))]
      (.close rs)
      val)))

(defn- row-from-resultset [rs rsmd]
  (let [cols (.getColumnCount rsmd)]
    (reverse (loop [idx 1 vals 'nil]
    	       (if (<= idx cols)
	           (recur (+ idx 1)
		          (cons (.getString rs idx) vals))
                   vals)))))

(defn db-query-table [sql]
  (with-db-statements [st sql]
    (let [rs (.executeQuery st)
    	  rsm (.getMetaData rs)
	  vals (loop [rows 'nil]
               	 (if (.next rs)
	    	 (recur (cons (row-from-resultset rs rsm) rows))
	    	 rows))]
      (.close rs)
      (reverse vals))))

(defn db-update [sql]
  (with-db-statements [st sql]
    (.executeUpdate st)))

(defn generated-key [st]
  (with-open [rs (.getGeneratedKeys st)]
    (.next rs)
    (.getInt rs 1)))

;;
;; GFF suppport
;;

(defn gff2-test [file]
  (with-open [r (new java.io.BufferedReader (new java.io.FileReader file))]
    (try (.parse (new org.biojava.bio.program.gff.GFFParser) r
                 (proxy [org.biojava.bio.program.gff.GFFDocumentHandler] []
                   (startDocument [loc])
                   (endDocument [])
                   (commentLine [comment])
                   (recordLine [record]
                     (throw (.new RuntimeException)))))
         (catch RuntimeException ex 'true)
         (catch Exception ex 'false))))

(defn gff2-parse [file record-handler]
  (with-open [r (new java.io.BufferedReader (new java.io.FileReader file))]
    (.parse (new org.biojava.bio.program.gff.GFFParser) r
                 (proxy [org.biojava.bio.program.gff.GFFDocumentHandler] []
                   (startDocument [loc])
                   (endDocument [])
                   (commentLine [comment])
                   (recordLine [record] (record-handler record))))))

;;
;; WIG support
;;

;;
;; Utilities
;;

(defn sane-string [x]
  (and x (> (.length x) 0)))

;;
;; Core Dastard stuff
;;


(def *coord-systems* (db-query-table "select id, name, map_master from das_reference"))

(defn- front-page [request]
  (html [:head [:title "WebDastard"]
	       [:link {:rel "stylesheet" :href "/style.css" :type "text/css"}]]
      [:body
        [:div#sidebar
	 [:img {:src "/dastard.png"}]]
        [:div#content-holder [:div#main
          [:h1 "WebDastard"]
          [:p [:a {:href "/upload"} "Add new datasource..."]]
  	  [:table {:border 1} [:tr [:th "Name" [:br] "(Click to manage)"] [:th "Reference"] [:th "Description"] [:th "Activate"]]
	  (map (fn [x]
	         (let [[name desc csid] x]
	           (html [:tr [:td name] 
	  	       	    [:td (second (some (fn [x]
			    	       	         (if (= (first x) csid) x))
					       *coord-systems*))] 
			    [:td desc] 
			    [:td "E! Daliance UCSC"]])))
               (db-query-table "select name, description, reference from das_source"))]]]]))

(defn- upload-page [request]
  (let [session (get request :session)
        name (get-in request '(:params :name))
	desc (get-in request '(:params :desc))
        csid (get-in request '(:params :ref))
	file (get-in request '(:multipart-params :file :tempfile))
	file-exists (when file (.exists file))
	file-format (if file-exists (if (gff2-test file) :gff2 :other)
	                            :missing)
	name-okay (and (sane-string name)
		       (with-db-statements [st "select id from das_source where name = ?"]
		         (.setString st 1 name)
			 (with-open [rs (.executeQuery st)]
			   (not (.next rs)))))
        feedback (if file 'true 'false)]
    (if (and (sane-string name) (sane-string csid) (= file-format :gff2) name-okay)
        {:body (html [:h1 "Creating " name]
	       	     [:p "Data format is " file-format]
		     [:p "Note that large files may take some time to process..."]
	             [:form {:id 'conf :action "/confirm" :method "POST"}
                       [:input {:type 'submit :value "Confirm"}]])
             :session (assoc session :name name 
			             :csid csid 
				     :desc desc 
				     :file file 
				     :file-format file-format 
				     :disk-file-item (get-in request '(:multipart-params :file :disk-file-item)))}
	{:body (html  [:h1 "New Datasource"]
                [:form {:id 'add :action "/upload" :method "POST" :enctype "multipart/form-data"}
       	   	[:p "Name:" [:input {:name "name" :size 20 :value name}]
                    (when (and feedback (not (sane-string name))) [:span "Required"])]
		    (when (and feedback (sane-string name) (not name-okay))
		      [:span "Sorry, name already in use"])
		[:p "Description:" [:input {:name "desc" :size 100 :value (if (sane-string desc) desc "Exciting genomic stuff")}]]
           	[:p "Coordinate system:" [:select {:name "ref"} (map (fn [cs]
                                         (html [:option {:value (first cs)} (second cs)]))
                                       *coord-systems*)]]
                [:p "Feature file:" [:input {:name "file" :size 20 :type 'file}]
		    ; [:span "file=" file "; file-exists=" file-exists " ;file-format=" file-format]
                    (when (and feedback (= file-format :missing))
		      [:span "Required"])
		    (when (and feedback (= file-format :other))
		      [:span "Not a supported format"])]
                [:input {:type 'submit}]])})))

(defn- confirm-page [request]
  (let [session (get request :session)
        name    (get session :name)
	desc    (get session :desc)
	csid    (get session :csid)
	file    (get session :file)
	file-format (get session :file-format)]
    (with-db-statements [st "insert into das_source (name, owner, description, reference) values (?, 1, ?, ?)"]
      (.setString st 1 name)
      (.setString st 2 desc)
      (.setString st 3 csid)      ; VALIDATE
      (.executeUpdate st)
      (let [id (generated-key st)
            type-cache (transient {})
	    min-score (atom Double/POSITIVE_INFINITY)
	    max-score (atom Double/NEGATIVE_INFINITY)
	    max-feature-length (atom 0)]
        (with-db-statements [fst "insert into feature (das_source, seq_name, seq_min, seq_max, seq_strand, type, score) values (?, ?, ?, ?, ?, ?, ?)"
                             tst "insert into feature_type (das_source, type, source) values (?, ?, ?)"
			     mst "insert into das_source_meta_cache (source, min_score, max_score, longest_feature) values (?, ?, ?, ?)"]
	  (gff2-parse file
            (fn [record]
              (let [type-baton (list (.getSource record) (.getFeature record))
                    type-handle (or (get type-cache type-baton)
		    		    (do (.setInt tst 1 id)
				        (.setString tst 2 (.getFeature record))
					(.setString tst 3 (.getSource record))
					(.executeUpdate tst)
					(let [tid (generated-key tst)]
					  (assoc! type-cache type-baton tid)
					  tid)))]
		(swap! min-score min (.getScore record))
		(swap! max-score max (.getScore record))
		(swap! max-feature-length max (- (.getEnd record) (.getStart record) -1))
	        (.setInt    fst 1 id)
	        (.setString fst 2 (.getSeqName record))
	        (.setInt    fst 3 (.getStart record))
	        (.setInt    fst 4 (.getEnd record))
	        (.setInt    fst 5 (.getValue (.getStrand record)))
	        (.setInt    fst 6 type-handle)
	        (.setDouble fst 7 (.getScore record))
	        (.executeUpdate fst))))
	  (.setInt    mst 1 id)
	  (.setDouble mst 2 @min-score)
	  (.setDouble mst 3 @max-score)
	  (.setInt    mst 4 @max-feature-length)
	  (.executeUpdate mst))
	(html [:h1 "Added " id]
	      [:p [:a {:href "/"} "Home"]])))))

(decorate upload-page (with-multipart)
	  	      (with-session :memory))

(decorate confirm-page (with-session :memory))

;;
;; Publish webapp
;;

(defroutes dastard-app
      (GET "/" front-page)
      (ANY "/upload" upload-page)
      (ANY "/confirm" confirm-page)
      (GET "/*"
        (or (serve-file (params :*)) :next))
      (ANY "*"
        (page-not-found)))
