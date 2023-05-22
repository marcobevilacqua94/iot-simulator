# ingestor

use it with

docker run marcobevilacqua94/ingestor:latest java -jar app.jar -h <host> -u <username> -p <password> -b <bucket-name> -s <scope-name> -c <collection-name> -b <buffer-size> -n <num-of-docs> -uc <usecount>

usecount is a boolean to tell the script if it should use a query to count the docs and stops when the docs num is precisely the one indicated 
