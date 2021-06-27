#!/bin/bash
echo "host: $1"
echo "password: $2"
echo "ID: $3"
echo "type: $4"
echo "port: $5"
echo "server: $6"
sshpass -p "$2" ssh root@$1 'mkdir proxy && apt-get update && apt-get install openjdk-8-jre -y && apt-get install net-tools -y && apt-get install nginx -y'
sshpass -p "$2" scp default root@$1:/etc/nginx/sites-enabled/default
sshpass -p "$2" scp 1.json root@$1:/var/www/html/1.json
cp config.tmp config.json
cp start.tmp start.sh
sed -i "s/AAA/$3/g" config.json
sed -i "s/BBB/$6/g" config.json
sed -i "s/CCC/$5/g" config.json
sed -i "s/EEE/$4/g" start.sh
sshpass -p "$2" scp wsocks.jar root@$1:/root/proxy/wsocks.jar
sshpass -p "$2" scp config.json root@$1:/root/proxy/config.json
sshpass -p "$2" scp start.sh root@$1:/root/proxy/run.sh
sshpass -p "$2" ssh root@$1 $'crontab -l | { cat; echo "@daily /sbin/reboot"; } | crontab - && crontab -l | { cat; echo "@reboot cd /root/proxy && bash run.sh"; } | crontab - && wget --no-check-certificate https://github.com/tcp-nanqinlang/general/releases/download/3.4.2.1/tcp_nanqinlang-fool-1.3.0.sh'
sshpass -p "$2" ssh root@$1
echo "Waiting for reboot"
sleep 30s
sshpass -p "$2" ssh root@$1 'bash tcp_nanqinlang-fool-1.3.0.sh'

