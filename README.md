# iot_simulator
This is a project to simulate the insertion of data points in Couchbase from a certain number of sensors with a configurable frequency.
Data is then prepared with eventing to be queried using timeseries.

use it with

```
docker run marcobevilacqua94/iot_simulator:latest java -jar iot_simulator.jar -h (host) -u (username) -p (password) -b (bucket-name) -s (scope-name) -c (collection-name) -se (sensors) -mt (max-seconds) -ips (inserts-per-second)
```

inserts-per-second are referred to single sensor

default values for parameters are
```
host: 127.0.0.1
username: Administrator
password: password
bucker-name: sample
scope-name: _default
collection-name: source
sensors: 5
max-time: 20
inserts-per-second: 100
```
The collection were the sensors write is supposed to have a short time to live (to save space).
To aggregate data with and eventing function and use timeseries feature of couchbase, **build an eventing function like this one** 

This function aggregates data in the same 10 seconds window (-> ```doc.timestamp.toString().substring(0,9)```)
tgt is the collection where you want to aggregate the data. The function must listen to where the sensors write. 
Use a From Now on policy.

```
function OnUpdate(doc, meta) {
    log("New data insertion", meta.id)
    var id = doc.sensor + ":" + doc.timestamp.toString().substring(0,9)
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
CREATE INDEX "index1" ON `sample`.`_default`.`target`(`ts_start`, `ts_end`, `device`)
```

Now you can use the query engine to run this king of queries and produce charts in the UI (check the date ranges):

**ONLY ONE SENSOR - USE MULTI-LINE BY COLUMNS** 
```
WITH range_start as (STR_TO_MILLIS("2023-05-01T00:00:00Z")), range_end as (STR_TO_MILLIS("2023-06-30T00:00:00Z"))
SELECT MILLIS_TO_TZ(t._t,"UTC") AS date, t._v0 AS temperature
FROM target AS d
UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t
WHERE d.device= 50 AND (d.ts_start <= range_end AND d.ts_end >= range_start);
```

**MULTIPLE SENSORS - USE X-Y**
```
WITH range_start as (STR_TO_MILLIS("2023-05-01T00:00:00Z")), range_end as (STR_TO_MILLIS("2023-06-30T00:00:00Z"))SELECT MILLIS_TO_TZ(t._t,"UTC") AS date, t._v0 AS temperature, d.device as sensor
FROM target AS d
UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t
WHERE d.device in [45,46,47,48,49,50,51,52,53,54] AND (d.ts_start <= range_end AND d.ts_end >= range_start);
```

**THREE SECONDS MOVING AVERAGE - USE MULTI-LINE BY COLUMNS** 
```
WITH range_start as (STR_TO_MILLIS("2023-05-01T00:00:00Z")), range_end as (STR_TO_MILLIS("2023-06-30T00:00:00Z"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg, AVG(second_avg) OVER (ORDER BY second ROWS 3 PRECEDING) AS three_seconds_mov_avg 
FROM target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t 
WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 50
GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0);
```

**THREE AND FIVE SECONDS MOVING AVERAGE - USE MULTI-LINE BY COLUMNS** 
```
WITH range_start as (STR_TO_MILLIS("2023-05-01T00:00:00Z")), range_end as (STR_TO_MILLIS("2023-06-30T00:00:00Z"))
SELECT MILLIS_TO_TZ(second * 1000, "UTC") AS date, second_avg as A_second_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 3 PRECEDING) AS B_three_seconds_mov_avg,
AVG(second_avg) OVER (ORDER BY second ROWS 5 PRECEDING) AS C_five_seconds_mov_avg 
FROM target AS d UNNEST _timeseries(d, {"ts_ranges": [range_start, range_end]}) AS t 
WHERE (d.ts_start <= range_end AND d.ts_end >= range_start) AND d.device = 50 
GROUP BY IDIV(t._t, 1000) AS second LETTING second_avg = AVG(t._v0);
```
