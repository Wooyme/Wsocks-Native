rm hex
rm hex.out
hexdump -v -e '"\\x" 1/1 "%02x"' kila.ko > hex
sed -e "s/.\{1000\}/&\" >> kila.ko';sleep 1;echo 'echo -ne \"/g" < hex > hex.out
sed -i "1s/^/{ echo 'cd \/var \&\& rm kila.ko';sleep 1;echo 'echo -ne \"/" hex.out
echo "\" >> kila.ko';sleep 1; }" >> hex.out
chmod +x hex.out
