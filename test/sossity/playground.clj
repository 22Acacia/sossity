(ns sossity.playground)


;parse graph
(def fields
  [["event"]
   ["owts"]
   ["owts_tmp_v2"]
   ["page_id"]
   ["page_action_id"]
   ["date"]
   ["timestamp"]
   ["orion_writetime"]
   ["client_timestamp"]
   ["protocol"]
   ["domain"]
   ["path"] ["source_ip"]])

(def lvl2-fields
  [["screen" "height"]
   ["screen" "width"]
   ["data" "step"]
   ["data" "test_name"]
   ["data" "variant"]
   ["data" "booking_ref"]])

(def lvl2-fields-b
  [{"screen" ["height" "width"]}
   {"data" ["step" "test_name" "variant" "booking_ref"]}])

(def lvl3-fields
  [["data" "resource_params" "bkg_ref"]
   ["data" "resource_params" "bref"]
   ["data" "resource_params" "bkref"]
   ["data" "action" "name"]])

(defn mm
  [coll]
  (mapv (fn [v] [(apply merge v)]) coll))

(defn combine-maps [& maps]
  (apply merge-with combine-maps maps))

(defn extract
  [raw-string]
  (let [data (decode raw-string)]
    (if (= (get data "service") "webapp")
      (let [lv1 (apply merge (mapv #(select-keys data %) fields))
            lv2 {"screen" {"height" (get-in data ["screen" "height"])
                           "width" (get-in data ["screen" "height"])}
                 "data" {"step" (get-in data ["data" "step"])
                         "test_name" (get-in data ["data" "test_name"])
                         "variant" (get-in data ["data" "variant"])
                         "booking_ref" (get-in data ["data" "booking_ref"])
                         "resource_params" {"bkg_ref" (get-in data ["data" "resource_params" "bkg_ref"])
                                            "bref" (get-in data ["data" "resource_params" "bref"])
                                            "bkref" (get-in data ["data" "resource_params" "bkref"])}
                         "action" {"name" (get-in data ["data" "action" "name"])}}}]

        (apply merge lv1 lv2)))))

(defn extract-clean [raw-string]
  (apply merge (extract raw-string)))

(def test-string
  "{\"browser\": {\"cookie_enabled\": \"True\",\n              \"inner_height\": 714,\n              \"inner_width\": 1440,\n              \"page_height\": 714,\n              \"user_agent\": \"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.122 Safari/537.36\"},\n \"client_timestamp\": 1412942308097,\n \"date\": \"2014-10-10\",\n \"domain\": \"v2staging.holidayextras.co.uk\",\n \"email\": \"andrew.castle+testtest@holidayextras.com\",\n \"event\": \"Loaded a Page\",\n \"event_type\": \"availability\",\n \"is_client\": \"True\",\n \"orion_timestamp\": \"1412942100000\",\n \"orion_uuid\": \"09084093-a810-48f6-bcde-593de9ad5867\",\n \"orion_writetime\": 1412942307234,\n \"owts\": \"75b6f1752fd946ef75c3bc4b8ed9a47e\",\n \"owts_tmp\": \"e8b0ed6a67e0a4f10384421cf2163f9b\",\n \"page_id\": \"cda1dc16c226c1a4c576504f31e9c165\",\n \"path\": \"/carpark\",\n \"port\": \"\",\n \"product_type\": \"carpark\",\n \"protocol\": \"https\",\n \"referrer\": \"http://www.holidayextras.co.uk/\",\n \"request_params\": {\"arrival_date\": \"NaN-NaN-NaN\",\n                     \"arrival_time\": \"NaN:NaN:NaN\",\n                     \"customer_ref\": \"\",\n                     \"depart\": \"LGW\",\n                     \"depart_date\": \"2014-10-17\",\n                     \"depart_time\": \"01:00:00\",\n                     \"flight\": \"TBC\",\n                     \"park_to\": \"13:00\",\n                     \"rrive\": \"\",\n                     \"term\": \"\",\n                     \"terminal\": \"\"},\n \"screen\": {\"height\": 900, \"pixelDepth\": 24, \"width\": 1440},\n \"server_ip\": \"10.210.175.195\",\n \"service\": \"webapp\",\n \"source_ip\": \"62.254.236.250\",\n \"time\": \"12:58:28\",\n \"title\": \"Parking 17 October 2014 | HolidayExtras.com\",\n \"url\": \"https://v2staging.holidayextras.co.uk/?agent=WEB1&ppcmsg=\"}")

(def test-2
  "{\"product_type\":\"carpark\",\"location\":\"LPL\",\"sort_criteria\":\"recommended\",\"sort_order\":\"asc\",\"products\":[{\"code\":\"HPLPY2\",\"price\":2510,\"position\":1},{\"code\":\"HPLPY1\",\"price\":2672,\"position\":2},{\"code\":\"HPLPX9\",\"price\":2915,\"position\":3},{\"code\":\"HPLPL1\",\"price\":3158,\"position\":4},{\"code\":\"HPLPY3\",\"price\":2429,\"position\":5},{\"code\":\"HPLPX1\",\"price\":5759,\"position\":6},{\"code\":\"HPLPL0\",\"price\":6209,\"position\":7},{\"code\":\"HPLPX8\",\"price\":2699,\"position\":8},{\"code\":\"HPLPL6\",\"price\":2789,\"position\":9},{\"code\":\"HPLPY5\",\"price\":5400,\"position\":10},{\"code\":\"HPLPX3\",\"price\":5400,\"position\":11},{\"code\":\"HPLPY8\",\"price\":4616,\"position\":12}],\"path\":\"/carpark\",\"referrer\":\"http://www.holidayextras.co.uk/email/email-parking.html?prodcode=ADM-HX-PKG-APR15-2&reset=1&agent=WJ389&email=ladee.ja7ne@yahoo.co.uk&location=MAN&linkid=05\",\"title\":\"Liverpool Parking 15 October 2015 | HolidayExtras.com\",\"url\":\"https://v2.holidayextras.co.uk/?agent=WJ389&ppcmsg=&lang=en\",\"service\":\"webapp\",\"environment\":\"production\",\"domain\":\"v2.holidayextras.co.uk\",\"date\":\"2015-05-06\",\"time\":\"01:00:00\",\"client_timestamp\":1430870400403,\"is_client\":true,\"owts\":\"bbe13e8bb011c8d369b7257218b31fab\",\"owts_tmp\":\"65c85875ab8aa1f1d00a936bf9e0feed\",\"email\":\"ladee.ja7ne@yahoo.co.uk\",\"page_id\":\"2401fd1f8c494fa2a9e6f3463a6ef695\",\"page_type\":\"availability\",\"event\":\"load\",\"screen\":{\"pixelDepth\":32,\"width\":320,\"height\":480},\"browser\":{\"user_agent\":\"Mozilla/5.0 (iPhone; CPU iPhone OS 8_2 like Mac OS X) AppleWebKit/600.1.4 (KHTML, like Gecko) Version/8.0 Mobile/12D508 Safari/600.1.4\",\"cookie_enabled\":true,\"inner_width\":320,\"inner_height\":372,\"page_height\":372},\"request_params\":{\"agent\":\"WJ389\",\"ppcmsg\":\"\",\"lang\":\"en\",\"arrive\":\"\",\"customer_ref\":\"\",\"flight\":\"\",\"park_to\":\"19:30\",\"term\":\"\",\"terminal\":\"\",\"arrival_date\":\"NaN-NaN-NaN\",\"arrival_time\":\"NaN:NaN:NaN\",\"depart_date\":\"2015-10-15\",\"depart_time\":\"01:00:00\",\"location\":\"LPL\"},\"url_hash\":\"#carpark?arrive=&customer_ref=&depart=LPL&flight=&in=2015-10-23&out=2015-10-15&park_from=17:00&park_to=19:30&term=&terminal=\",\"referrer_params\":{\"prodcode\":\"ADM-HX-PKG-APR15-2\",\"reset\":\"1\",\"agent\":\"WJ389\",\"email\":\"ladee.ja7ne@yahoo.co.uk\",\"location\":\"MAN\",\"linkid\":\"05\",\"search\":\"?prodcode=ADM-HX-PKG-APR15-2&reset=1&agent=WJ389&email=ladee.ja7ne@yahoo.co.uk&location=MAN&linkid=05\",\"url\":\"http://www.holidayextras.co.uk/email/email-parking.html?prodcode=ADM-HX-PKG-APR15-2&reset=1&agent=WJ389&email=ladee.ja7ne@yahoo.co.uk&location=MAN&linkid=05\",\"protocol\":\"http\",\"path\":\"/email/email-parking.html\",\"domain\":\"www.holidayextras.co.uk\"},\"protocol\":\"https\",\"port\":\"\",\"server_ip\":\"10.208.49.181\",\"orion_uuid\":\"799faea5-0711-479f-8631-61de7eee0cab\",\"source_ip\":\"82.40.9.234\",\"orion_timestamp\":\"1430870400000\",\"orion_writetime\":1430870401089}\n")