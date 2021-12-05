package main

import (
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"strings"
)

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

func delete(client http.Client, auth string, base_url string) {

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
	fmt.Println()
	for i := 0; i < len(devids); i++ {
		del(base_url+"/api/devices/"+devids[i], auth, client)
	}
	fmt.Println()
	for i := 0; i < len(geofids); i++ {
		del(base_url+"/api/geofences/"+geofids[i], auth, client)
	}
	fmt.Println()
	for i := 0; i < len(notids); i++ {
		del(base_url+"/api/notifications/"+notids[i], auth, client)
	}
}
