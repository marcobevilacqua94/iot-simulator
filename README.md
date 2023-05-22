# ingestor

use it with

docker run marcobevilacqua94/ingestor:latest java -jar ingestor.jar -h (host) -u (username) -p (password) -b (bucket-name) -s (scope-name) -c (collection-name) -b (buffer-size) -n (num-of-docs) -uc

use_count is an option used to tell the script if it should query the cluster to know the number of docs in the collection and stop more precisely
