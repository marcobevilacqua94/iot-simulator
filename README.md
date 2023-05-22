# ingestor

use it with

docker run marcobevilacqua94/ingestor:latest java -jar ingestor.jar -h (host) -u (username) -p (password) -b (bucket-name) -s (scope-name) -c (collection-name) -b (buffer-size) -n (num-of-docs) -cl (content-limit)

content-limit is an option to tell the script to check the content of the collection to fill it to a certain size. It requires to run a select count query. The query is run each "buffer" number of insertion.

default values for parameters are

host: localhost
username: Administrator
password: password
bucker-name: sample
scope-name: _default
collection-name: _default
buffer-size: 1000e
num-of-docs: 0 (infinite)
content-limit: 0 (infinite)

