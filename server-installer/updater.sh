#!/bin/bash
token=$(curl --header "Content-Type: application/json" --request POST --data '{"username":"wooyme","password":"wy16880175"}' http://zzyun.co/wp-json/jwt-auth/v1/token | jq -r '.token')
echo $token
center=$(curl -H "Content-Type: application/json" -H "Authorization: Bearer $token" --request GET http://zzyun.co/wp-json/acf/v3/posts/3076/center | jq -r '.center')
echo $center
sed -i -E "s/[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}/$center/g" config.json

