echo "host: $1"
echo "password: $2"
sshpass -p "$2" ssh root@$1 'apt-get update && apt-get install nginx -y'
sshpass -p "$2" scp 1.json root@$1:/var/www/html/1.json
sshpass -p "$2" scp default root@$1:/etc/nginx/sites-enabled/default
sshpass -p "$2" ssh root@$1 'nginx -s reload'
