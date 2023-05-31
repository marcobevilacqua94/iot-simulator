package org.iot_simulator;

import com.couchbase.client.java.json.JsonObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;


public class SensorDocGenerator implements DocGenerator {

    private final Random rand = new Random();
    public JsonObject generateDoc(long millis, Double lastValue, long sensor){
        return JsonObject.create()
                .put("timestamp", millis)
                .put("temperature", generateTemperature(lastValue))
                .put("sensor", sensor);
    }


    double generateTemperature(Double lastValue){
        double newValue;
        if(lastValue == null){
            return BigDecimal.valueOf(rand.nextDouble() * 40 - 10).setScale(3, RoundingMode.FLOOR).doubleValue();
        } else {
            newValue = BigDecimal.valueOf(lastValue + rand.nextDouble() * 2 - 1).setScale(3, RoundingMode.FLOOR).doubleValue();
        }

        if(newValue > 50){
            return lastValue;
        }
        if(newValue < -20){
            return lastValue;
        }

        return newValue;
    }


}
