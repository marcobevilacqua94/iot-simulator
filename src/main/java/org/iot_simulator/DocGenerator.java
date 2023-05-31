package org.iot_simulator;

import com.couchbase.client.java.json.JsonObject;

public interface DocGenerator {

    JsonObject generateDoc(long millis, Double lastValue, long sensor);
}
