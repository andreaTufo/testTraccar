package main

import (
	"bytes"
	"encoding/base64"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"
)

//----------------------UTILITY-----------------------------------

//url != nil auth != nil client != nil
//esegue una post alla url indicata e restuisce l'id dell'entity appena creata sse id_ret == true
func post(url string, auth string, client http.Client, body io.Reader, id_ret bool) string {

	req1, err := http.NewRequest("POST", url, body)
	req1.Header.Add("Authorization", "Basic "+auth)
	if body != nil {
		req1.Header.Set("Content-Type", "application/json")
	}

	resp1, err := client.Do(req1)
	if err != nil {
		fmt.Println(resp1)
	}

	if id_ret {
		b, err := ioutil.ReadAll(resp1.Body)
		defer resp1.Body.Close()
		if err != nil {
			log.Fatalln(err)
		}

		var split = strings.Split(string(b), `"id":`)

		var temp = strings.Split(split[1], ",")

		return temp[0]
	} else {
		return "none"
	}

}

func isInactive(url string, auth string, client http.Client) bool {

	req1, err := http.NewRequest("GET", url, nil)
	req1.Header.Add("Authorization", "Basic "+auth)

	resp1, err := client.Do(req1)
	if err != nil {
		fmt.Print(resp1)
		return true
	} else {
		return false
	}

}

func get(url string, auth string, client http.Client) []string {

	req1, err := http.NewRequest("GET", url, nil)
	req1.Header.Add("Authorization", "Basic "+auth)

	resp1, err := client.Do(req1)
	if err != nil {
		fmt.Println(resp1)
	}

	defer resp1.Body.Close()

	b, err := ioutil.ReadAll(resp1.Body)
	if err != nil {
		log.Fatalln(err)
	}

	var split = strings.Split(string(b), `"id"`)

	if split[0] == "[]" {
		return nil
	}

	return split
}

func getId(arr []string) []string {

	var ids []string

	for i := 0; i < len(arr); i++ {
		if i != 0 {
			var temp = strings.Split(arr[i], ",")
			var id_as_str = strings.Split(temp[0], ":")
			ids = append(ids, id_as_str[1])
		}

	}

	return ids
}

func del(url string, auth string, client http.Client) {
	req1, err := http.NewRequest("DELETE", url, nil)
	req1.Header.Add("Authorization", "Basic "+auth)

	resp1, err := client.Do(req1)
	if err != nil {
		fmt.Println(resp1)
	}
	defer resp1.Body.Close()
}

//-----------------------------------------------------------

//cancella le istanze che una precedente simulazione pu?? aver creato
func delete(client http.Client, auth string, base_url string) {
	fmt.Println("Deleting old values")

	split := get(base_url+"/api/groups?all=true", auth, client)
	groupIds := getId(split)

	split = get(base_url+"/api/devices?all=true", auth, client)
	devids := getId(split)

	split = get(base_url+"/api/geofences?all=true", auth, client)
	geofids := getId(split)

	split = get(base_url+"/api/notifications?all=true", auth, client)
	notids := getId(split)

	for i := 0; i < len(groupIds); i++ {
		del(base_url+"/api/groups/"+groupIds[i], auth, client)
	}

	for i := 0; i < len(devids); i++ {
		del(base_url+"/api/devices/"+devids[i], auth, client)
	}

	for i := 0; i < len(geofids); i++ {
		del(base_url+"/api/geofences/"+geofids[i], auth, client)
	}

	for i := 0; i < len(notids); i++ {
		del(base_url+"/api/notifications/"+notids[i], auth, client)
	}
}

//crea le istanze necessarie
func init_simulation(client http.Client, auth string, base_url string) {

	var jsonGr = []byte(`{"id":0,"attributes":{},"groupId":0,"name":"veivoli"}`)
	var jsonNot1 = []byte(`{"id":50,"attributes":{"alarms":"geofenceEnter"},"calendarId":0,"always":true,"type":"geofenceEnter","notificators":"web,traccar"}`)
	var jsonNot2 = []byte(`{"id":51,"attributes":{"alarms":"geofenceExit"},"calendarId":0,"always":true,"type":"geofenceExit","notificators":"web,traccar"}`)
	var jsonGeoF = []byte(`{"id":5,"attributes":{},"calendarId":0,"name":"incendio","description":"","area":"CIRCLE (40.64215013804545 17.549314596909138, 8432.6)"}`)

	//creo il gruppo

	groupId := post(base_url+"/api/groups", auth, client, bytes.NewBuffer(jsonGr), true)

	fmt.Println("Group 'veivoli' created")
	//creo il device
	var jsonDev = []byte(`{"id":1105,"attributes":{"droppingWater":"Boolean"},"groupId":` + groupId + `,"name":"Vigili del fuoco","uniqueId":"080","status":"offline","lastUpdate":"","positionId":0,"geofenceIds":[],"phone":"","model":"","contact":"","category":"helicopter","disabled":false}`)
	post(base_url+"/api/devices", auth, client, bytes.NewBuffer(jsonDev), false)

	//creo la geofence
	geofid := post(base_url+"/api/geofences", auth, client, bytes.NewBuffer(jsonGeoF), true)
	fmt.Println("geofence 'incendio' created")

	//creo le due notify
	not1id := post(base_url+"/api/notifications", auth, client, bytes.NewBuffer(jsonNot1), true)
	not2id := post(base_url+"/api/notifications", auth, client, bytes.NewBuffer(jsonNot2), true)

	fmt.Println("notifications created")

	//collego la geofence al gruppo veivoli e alle notifications
	var jsonPerm = []byte(`{ "groupId":` + groupId + `,"geofenceId":` + geofid + `}`)
	var jsonPerm1 = []byte(`{ "groupId":` + groupId + `,"notificationId":` + not1id + `}`)
	var jsonPerm2 = []byte(`{ "groupId":` + groupId + `,"notificationId":` + not2id + `}`)

	post(base_url+"/api/permissions", auth, client, bytes.NewBuffer(jsonPerm), false)
	post(base_url+"/api/permissions", auth, client, bytes.NewBuffer(jsonPerm1), false)
	post(base_url+"/api/permissions", auth, client, bytes.NewBuffer(jsonPerm2), false)

	fmt.Println("links created")
}

//fa partire la simulazione
func simulation(client http.Client, auth string, base_url string) {

	var lat float64 = 40.409339
	var long float64 = 17.23805
	var speed int = 0
	var motion string = "false"
	var droppingW = "false"
	//346
	fmt.Println("Sending data...")
	for i := 0; i < 400; i++ {

		var url = base_url + "/?id=080&lat=" + strconv.FormatFloat(lat, 'f', -1, 32) + "&lon=" + strconv.FormatFloat(long, 'f', -1, 32) + "&hdop=3&altitude=1000&speed=" + strconv.Itoa(speed) + "&droppingWater=" + droppingW + "&motion=" + motion

		time.Sleep(300 * time.Millisecond)
		post(url, auth, client, nil, false)

		if i < 50 {
			motion = "false"
			speed = 0
		} else if i >= 50 && i < 80 {
			motion = "true"
			speed = 1
			lat += 0.00
			long += 0.007
		} else if i >= 80 && i < 100 {
			speed = 5
			droppingW = "true"
			lat += 0.009
			long += 0.001
		} else if i >= 100 && i < 150 {
			speed = 10
			lat += 0.001
			long += 0.005

		} else if i >= 150 && i < 250 {
			speed = 0
			motion = "false"
			droppingW = "false"
		} else if i >= 250 && i < 300 {
			motion = "true"
			droppingW = "true"
			speed = 13
			long -= 0.00432
			lat -= 0.0008
		} else {
			motion = "false"
			speed = 17
			lat += 0
			lat -= 0
		}

	}

	fmt.Println("finished")

}

func main() {

	var base_url = "http://traccar:8082"

	client := http.Client{}
	//costruisco l'header per l'autorizzazione ed inizio la sessione
	str := "admin:admin"
	auth := base64.StdEncoding.EncodeToString([]byte(str)) // encoding prima in utf-8 e poi in base64

	var inactive = true

	time.Sleep(1 * time.Second)
	for inactive {
		inactive = isInactive(base_url+"/api/server", auth, client)
		time.Sleep(1 * time.Second)
		fmt.Println("checking if the server is online...")
	}

	//cancello le entit?? create dalla simulazione precedente
	delete(client, auth, base_url)
	//creo delle nuove entit??
	init_simulation(client, auth, base_url)
	//parte la simulazione
	simulation(client, auth, base_url)

}
