# iot_simulator
This is a project to simulate the insertion of data points in Couchbase from a certain number of sensors with a configurable frequency.
Data is then prepared with eventing to be queried using timeseries.

use it with

```
docker run marcobevilacqua94/iot_simulator:latest java -jar iot_simulator.jar -h (host) -u (username) -p (password) -b (bucket-name) -s (scope-name) -c (collection-name) -se (sensors) -mt (max-seconds) -ips (inserts-per-second) -ttl (time-to-live)
```

inserts-per-second are referred to single sensor
max-time is in seconds, after this time the program stops

default values for parameters are
```
host: 127.0.0.1
username: Administrator
password: password
bucker-name: sample
scope-name: _default
collection-name: source
sensors: 5
max-time: 0 (infinte)
inserts-per-second: 5
time-to-live: 60
```
To run Couchbase Server 7.2.0 run this 
```
docker run -d --name db1 -p 8091-8096:8091-8096 -p 11210-11211:11210-11211 couchbase:7.2.0
```
Create "sample" bucket, create required collections, in the _default scope, collection "source" (where sensors write), collection "metadata" (for eventing storage), collection "target" (for timeseries final data).
The collection were the sensors write ("source") is supposed to have a short time to live (to save space).
Then create the eventing function, listening on "source", with storage in "metadata" and with alias "tgt" pointing to "target" collection.
This function aggregates data from the same sensor in the same 60 seconds window (-> ```Math.trunc(doc.timestamp / 60000)```).
tgt is the collection where you want to aggregate the data. The function must listen to where the sensors write.
Use a From Now on policy. Use ts_interval and add only the temperature to the array of values (not the array couple temperature + timestamp) if you want to use regular intervals.
To aggregate data with and eventing function and use timeseries feature of couchbase, **build an eventing function like this one**

```
function OnUpdate(doc, meta) {
    log("New data insertion", meta.id)
    var id = doc.sensor + ":" + Math.trunc(doc.timestamp / 60000)
    if(tgt[id]){
        var agg = tgt[id]
        
        if(doc.timestamp > agg.ts_end){
            agg.ts_end = doc.timestamp
        } else if(doc.timestamp < agg.ts_start){
            agg.ts_start = doc.timestamp
        }
        
        agg.ts_data.push([doc.timestamp, doc.temperature])
        
        tgt[id] = agg

        
    } else {
        tgt[id] = {
            "ts_start" : doc.timestamp,
            "ts_end" : doc.timestamp,
            // "ts_interval" : 10,
            "device" : doc.sensor,
            "ts_data" : [[doc.timestamp, doc.temperature]]
        }
    }
}
```

Now if you have a version of Couchbase which supports timeseries, **create this index** (target is the collection with timeseries data):
```
CREATE INDEX index1 ON `target`(`device`, `ts_start`, `ts_end`)
```
Populate Couchbase Server on docker with this:
```
docker run marcobevilacqua94/iot_simulator:latest java -jar iot_simulator.jar -h 172.17.0.1 -mt 300
```
Now you can use the query engine to run this king of queries and produce charts in the UI (check the date ranges):

**ONLY ONE SENSOR - USE MULTI-LINE BY COLUMNS**
```
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(t._t,"UTC") AS date, t._v0 AS temperature
FROM target AS d
UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t
WHERE d.device= 0 AND (d.ts_start <= range_end AND d.ts_end >= range_start);
```

**MULTIPLE SENSORS - USE X-Y**
```
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(t._t,"UTC") AS date, t._v0 AS temperature, d.device as sensor
FROM target AS d
UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t
WHERE d.device in [0,1,2,3,4] AND (d.ts_start <= range_end AND d.ts_end >= range_start);
```

**MULTIPLE SENSORS - USE MULTI-LINE BY COLUMNS -- VERY SLOW**
```
WITH device0data AS (
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(t._t,"UTC") AS date, t._v0 AS temperature0
FROM target AS d
UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t
WHERE d.device= 0 AND (d.ts_start <= range_end AND d.ts_end >= range_start)
), device1data AS (
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(t._t,"UTC") AS date, t._v0 AS temperature1
FROM target AS d
UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t
WHERE d.device = 1 AND (d.ts_start <= range_end AND d.ts_end >= range_start)
)

SELECT SUBSTR(device0data.date, 0, 19) as timestamp, device0data.temperature0, device1data.temperature1
FROM 
device0data 
JOIN device1data
ON SUBSTR(device0data.date, 0, 19) = SUBSTR(device1data.date, 0, 19)
```

**ONE, THREE SECONDS MOVING AVERAGE - USE MULTI-LINE BY COLUMNS**
```
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg, AVG(second_avg) OVER (ORDER BY second ROWS 3 PRECEDING) AS three_seconds_mov_avg 
FROM target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t 
WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 0
GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0);
```

**ONE, THREE AND FIVE SECONDS MOVING AVERAGE - USE MULTI-LINE BY COLUMNS**
```
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg as A_second_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 3 PRECEDING) AS B_three_seconds_mov_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 5 PRECEDING) AS C_five_seconds_mov_avg 
FROM target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t 
WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 0 
GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0);
```

**ONE, THREE AND FIVE SECONDS MOVING AVERAGE - USE MULTI-LINE BY COLUMNS, TWO SENSORS**
```
WITH device0data AS (
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg as A_second_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 30 PRECEDING) AS B_thirty_sec_mov_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 60 PRECEDING) AS C_one_minute_mov_avg 
FROM target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t 
WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 0 
GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0)
), device1data AS (
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg as A_second_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 30 PRECEDING) AS B_thirty_sec_mov_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 60 PRECEDING) AS C_one_minute_mov_avg 
FROM target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t 
WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 1 
GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0)
)

SELECT device0data.date as timestamp, device0data.A_second_avg as second_avg_0, device1data.A_second_avg as second_avg_1, 
device0data.B_thirty_sec_mov_avg as thirty_sec_mov_avg_0, device1data.B_thirty_sec_mov_avg as thirty_sec_mov_avg_1, 
device0data.C_one_minute_mov_avg as one_minute_mov_avg_0, device1data.C_one_minute_mov_avg  as one_minute_mov_avg
FROM 
device0data 
JOIN device1data
ON device0data.date = device1data.date
```

**ONE SECOND MOVING AVERAGE - USE MULTI-LINE BY COLUMNS, FIVE SENSORS**
```
WITH device0data AS (
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg as A_second_avg
FROM target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t
WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 0
GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0)
), device1data AS (
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg as A_second_avg
FROM target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t
WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 1
GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0)
), device2data AS (
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg as A_second_avg
FROM target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t
WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 2
GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0)
), device3data AS (
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg as A_second_avg
FROM target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t
WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 3
GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0)
), device4data AS (
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg as A_second_avg
FROM target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t
WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 4
GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0)
)

SELECT device0data.date as timestamp,
device0data.A_second_avg as second_avg_0,
device1data.A_second_avg as second_avg_1,
device2data.A_second_avg as second_avg_2,
device3data.A_second_avg as second_avg_3,
device4data.A_second_avg as second_avg_4

FROM
device0data
JOIN device1data
ON device0data.date = device1data.date
JOIN device2data
ON device0data.date = device2data.date
JOIN device3data
ON device0data.date = device3data.date
JOIN device4data
ON device0data.date = device4data.date
```

**ONE, THREE AND FIVE SECONDS MOVING AVERAGE - USE MULTI-LINE BY COLUMNS, FIVE SENSORS**
```
WITH device0data AS (
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg as A_second_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 30 PRECEDING) AS B_thirty_sec_mov_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 60 PRECEDING) AS C_one_minute_mov_avg
FROM target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t
WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 0
GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0)
), device1data AS (
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg as A_second_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 30 PRECEDING) AS B_thirty_sec_mov_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 60 PRECEDING) AS C_one_minute_mov_avg
FROM target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t
WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 1
GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0)
), device2data AS (
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg as A_second_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 30 PRECEDING) AS B_thirty_sec_mov_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 60 PRECEDING) AS C_one_minute_mov_avg
FROM target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t
WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 2
GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0)
), device3data AS (
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg as A_second_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 30 PRECEDING) AS B_thirty_sec_mov_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 60 PRECEDING) AS C_one_minute_mov_avg
FROM target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t
WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 3
GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0)
), device4data AS (
WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg as A_second_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 30 PRECEDING) AS B_thirty_sec_mov_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 60 PRECEDING) AS C_one_minute_mov_avg
FROM target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t
WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 4
GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0)
)

SELECT device0data.date as timestamp,
device0data.A_second_avg as second_avg_0,
device0data.B_thirty_sec_mov_avg as thirty_sec_mov_avg_0,
device0data.C_one_minute_mov_avg as one_minute_mov_avg_0,
device1data.A_second_avg as second_avg_1,
device1data.B_thirty_sec_mov_avg as thirty_sec_mov_avg_1,
device1data.C_one_minute_mov_avg  as one_minute_mov_avg_1,
device2data.A_second_avg as second_avg_2,
device2data.B_thirty_sec_mov_avg as thirty_sec_mov_avg_2,
device2data.C_one_minute_mov_avg  as one_minute_mov_avg_2,
device3data.A_second_avg as second_avg_3,
device3data.B_thirty_sec_mov_avg as thirty_sec_mov_avg_3,
device3data.C_one_minute_mov_avg  as one_minute_mov_avg_3,
device4data.A_second_avg as second_avg_4,
device4data.B_thirty_sec_mov_avg as thirty_sec_mov_avg_4,
device4data.C_one_minute_mov_avg  as one_minute_mov_avg_4

FROM
device0data
JOIN device1data
ON device0data.date = device1data.date
JOIN device2data
ON device0data.date = device2data.date
JOIN device3data
ON device0data.date = device3data.date
JOIN device4data
ON device0data.date = device4data.date
```
**QUERY FOR USE WITH COUCHBASE GRAFANA CONNECTORS**
```
SELECT MILLIS_TO_STR(t._t) AS time, t._v0 as temperature, d.device as sensor
FROM sample._default.target AS d
UNNEST _timeseries(d) AS t 
WHERE d.device in [0,1,2] and time_range(t._t)![image](https://github.com/marcobevilacqua94/iot-simulator/assets/116554667/9f4037d1-88f9-4011-8797-3f1f90a2784b)
```
**QUERY FOR USE WITH COUCHBASE GRAFANA CONNECTORS, MULTIPLE SENSORS**
```
WITH device0data AS (WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg as A_second_avg,AVG(second_avg) OVER (ORDER BY second ROWS 30 PRECEDING) AS B_thirty_sec_mov_avg, AVG(second_avg) OVER (ORDER BY second ROWS 60 PRECEDING) AS C_one_minute_mov_avg FROM sample._default.target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 0 GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0)), 
device1data AS (WITH range_start as (STR_TO_MILLIS("2023-05-01")), range_end as (STR_TO_MILLIS("2023-06-30"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg as A_second_avg,AVG(second_avg) OVER (ORDER BY second ROWS 30 PRECEDING) AS B_thirty_sec_mov_avg,AVG(second_avg) OVER (ORDER BY second ROWS 60 PRECEDING) AS C_one_minute_mov_avg FROM sample._default.target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 1 GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0))

SELECT device0data.A_second_avg as second_avg_0, device1data.A_second_avg as second_avg_1, device0data.B_thirty_sec_mov_avg as thirty_sec_mov_avg_0, device1data.B_thirty_sec_mov_avg as thirty_sec_mov_avg_1, device0data.C_one_minute_mov_avg as one_minute_mov_avg_0, device1data.C_one_minute_mov_avg  as one_minute_mov_avg,
device0data.date as time
FROM device0data JOIN device1data ON device0data.date = device1data.date

WHERE str_time_range(device0data.date)
```

