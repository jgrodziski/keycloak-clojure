(ns myapp.front.db)

(def default-db
  {:name "myapp"
   :employees [{:id "123456"
                :first-name "Clemence"
                :last-name "Gaudet"
                :picture-url "http://abcdef"
                :roles [:vendeur]}
               {:id "234567"
                :first-name "Benoit"
                :last-name "Dupont"
                :picture-url "http://xxx"
                :roles [:vendeur]}
               {:id "345678"
                :first-name "Sandrine"
                :last-name "Tuet"
                :picture-url "http://xxx"
                :roles [:vendeur]}
               {:id "456789"
                :first-name "Marine"
                :last-name "Moussard"
                :picture-url "http://xxx"
                :roles [:vendeur]}]
   :referentials {:vendeur {:recevoir
                            [{:q "Accueille les clients et leur pose une question ouverte" :t :yes-no}
                             {:q "dit \"Bonjour !\"  \"Que puis-je faire pour vous ? \"" :t :yes-no}
                             {:q "Le gilet et le badge sont propres" :t :yes-no}
                             {:q "Arrête ce qu’il fait, sourit, regarde le client dans les yeux ; au plot : se lève ; en rayon : se déplace vers le client" :t :yes-no}
                             {:q "Du regard, lui montre spontanément sa disponibilité" :t :yes-no}]}}
   :observations {:made []
                  :received []}})
