package org.ingestor;

import com.couchbase.client.java.json.JsonObject;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.Random;

public class DLDocGenerator implements DocGenerator {

    private final Random rand = new Random();
    private final String[] events = {
            "update",
            "create",
            "history"
    };

    private final String loremSentence = "Lorem ipsum repellendus necessitatibus culpa blanditiis consequuntur dolor.";

    public JsonObject generateDoc(int counter){
        return JsonObject.create()
                .put("_id", generateOid())
                .put("id", counter)
                .put("item_type", "Appointment")
                .put("item_id", Long.parseLong(generateNatId()))
                .put("event",returnRandom(events))
                .put("whodounnit", Long.parseLong(generateNatId()))
                .put("created_at", generateDate())
                .put("object", loremSentence)
                .put("object_changes", generateObjectChanges())
                .put("remote_ip", generateIPAddress())
                .put("user_agent", generateUserAgent())
                .put("api_consumer_id", generateApiId())
                .put("external_sync_modification", loremSentence)
                .put("generated_sync_out_message", returnRandomBool());
    }

    private String returnRandom(String[] list) {
        return list[rand.nextInt(list.length)];
    }

    private boolean returnRandomBool(){
        return rand.nextBoolean();
    }

    private String generateIPAddress() {
        return rand.nextInt(256) + "." + rand.nextInt(256) + "." + rand.nextInt(256) + "." + rand.nextInt(256);
    }

    private String generateUserAgent() {
        return RandomStringUtils.randomAlphabetic(6).toUpperCase() + "." +
                RandomStringUtils.randomNumeric(3) + RandomStringUtils.randomAlphabetic(3).toUpperCase() + "." +
                RandomStringUtils.randomNumeric(3) + RandomStringUtils.randomAlphabetic(3).toUpperCase() + "." +
                RandomStringUtils.randomNumeric(3) + RandomStringUtils.randomAlphabetic(4).toUpperCase();

    }

    private String generateOid(){
        return RandomStringUtils.randomAlphanumeric(24);
    }

    private String generateNatId(){
        return RandomStringUtils.randomNumeric(10);
    }

    private String generateDate(){
        int year = 2013 + rand.nextInt(10);
        int month = rand.nextInt(12) + 1;
        int day;
        switch(month){
            case 1:
            case 3:
            case 5:
            case 7:
            case 8:
            case 10:
            case 12:
                day = rand.nextInt(31) + 1;
                break;
            case 2:
                switch(year){
                    case 2016:
                    case 2020:
                        day = rand.nextInt(29) + 1;
                        break;
                    default:
                        day = rand.nextInt(28) + 1;
                }
                break;
            default:
                day = rand.nextInt(30) + 1;
        }
        return year + "-" + (month < 10 ? ("0" + month) : month) + "-" + (day < 10 ? ("0" + day) : day);
    }

    private Long generateApiId(){
        return rand.nextInt(100) == 99 ? null : Long.parseLong(generateNatId());
    }

    private String generateObjectChanges(){
        return loremSentence.repeat(10 + rand.nextInt(89));
    }


}
