package org.ingestor;

import com.couchbase.client.java.json.JsonObject;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.Random;

public class DoctoLibDocGenerator implements DocGenerator {

    private final Random rand = new Random();
    private final String[] events = {
            "update",
            "create",
            "history"
    };

    private final String loremSentence = "Lorem ipsum repellendus necessitatibus culpa blanditiis consequuntur dolor.";

    public JsonObject generateDoc(int counter){
        return JsonObject.create()
                .put("_id", JsonObject.create()
                        .put("$oid", generateOid())
                )
                .put("id", JsonObject.create()
                        .put("$numberLong", Integer.toString(counter))
                )
                .put("item_type", "Appointment")
                .put("item_id", JsonObject.create()
                        .put("$numberInt", generateNatId())
                )
                .put("event",returnRandom(events))
                .put("whodounnit", JsonObject.create()
                        .put("$numberInt", generateNatId())
                )
                .put("created_at", JsonObject.create()
                        .put("$date", JsonObject.create()
                                .put("$numberLong", generateDate())))
                .put("object", loremSentence)
                .put("object_changes", generateObjectChanges())
                .put("remote_ip", generateIPAddress())
                .put("user_agent", generateUserAgent())
                .put("api_consumer_id", JsonObject.create()
                        .put("$numberInt", generateApiId()))
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
        return RandomStringUtils.randomNumeric(13);
    }

    private String generateApiId(){
        return rand.nextInt(100) == 99 ? null : generateNatId();
    }

    private String generateObjectChanges(){
        return loremSentence.repeat(10 + rand.nextInt(89));
    }


}
