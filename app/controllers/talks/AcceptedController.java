/*
 * Copyright 2013- Yan Bonnel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package controllers.talks;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import fr.ybonnel.csvengine.CsvEngine;
import fr.ybonnel.csvengine.annotation.CsvColumn;
import fr.ybonnel.csvengine.annotation.CsvFile;
import models.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.StringWriter;
import java.util.*;

import static play.libs.Jsonp.jsonp;

public class AcceptedController extends Controller {

    public static Result acceptedTalkByIdJsonp(Long id, String callback) {
        Talk talk = Talk.findByIdWithFetch(id);

        if (talk == null) {
            return notFound();
        }

        ObjectNode talkJson = talkToJson(talk);
        return ok(jsonp(callback, talkJson));
    }

    public static Result acceptedTalkById(Long id) {
        Talk talk = Talk.findByIdWithFetch(id);

        if (talk == null) {
            return notFound();
        }

        ObjectNode talkJson = talkToJson(talk);
        return ok(talkJson);
    }

    private static ObjectNode talkToJson(Talk talk) {
        ObjectNode talkJson = Json.newObject();
        talkJson.put("id", talk.id);
        talkJson.put("title", talk.title);
        talkJson.put("description", talk.description);
        talkJson.put("indicationsOrganisateurs", talk.indicationsOrganisateurs);
        ArrayNode tags = new ArrayNode(JsonNodeFactory.instance);
        for (Tag tag : talk.getTags()) {
            tags.add(tag.nom);
        }
        talkJson.put("tags", tags);

        ArrayNode speakers = new ArrayNode(JsonNodeFactory.instance);
        if (talk.speaker != null) {
            speakers.add(getSpeakerInJson(talk.speaker));
        }
        for (User otherSpeaker : talk.getCoSpeakers()) {
            speakers.add(getSpeakerInJson(otherSpeaker));
        }
        talkJson.put("speakers", speakers);
        return talkJson;
    }

    public static Result acceptedSpeakers() {
        return ok(getAcceptedTalksToJson());
    }

    @CsvFile(separator = ";")
    private static class AdressMacForSpeakers {

        public AdressMacForSpeakers(String speaker, String mac, String mail, String talks) {
            this.speaker = speaker;
            this.mac = mac;
            this.mail = mail;
            this.talks = talks;
        }

        public AdressMacForSpeakers() {
        }

        @CsvColumn("speaker_fullname")
        public String speaker;

        @CsvColumn("speaker_mail")
        public String mail;

        @CsvColumn("talks")
        public String talks;

        @CsvColumn("@MAC")
        public String mac;
    }

    public static Result adressMacOfAcceptedSpeakers() {

        List<Talk> talksAccepted = Talk.findByStatusForMinimalData(StatusTalk.ACCEPTE);

        Map<User, List<Talk>> speakers = new HashMap<User, List<Talk>>();

        for (Talk talk : talksAccepted) {
            if (talk.speaker != null) {
                if (!speakers.containsKey(talk.speaker)) {
                    speakers.put(talk.speaker, new ArrayList<Talk>());
                }
                speakers.get(talk.speaker).add(talk);
            }
            for (User speaker : talk.getCoSpeakers()) {
                if (!speakers.containsKey(speaker)) {
                    speakers.put(speaker, new ArrayList<Talk>());
                }
                speakers.get(speaker).add(talk);
            }
        }

        List<User> speakersSorted = new ArrayList<User>(speakers.keySet());
        Collections.sort(speakersSorted, new Comparator<User>() {
            @Override
            public int compare(User o1, User o2) {
                return o1.id.compareTo(o2.id);
            }
        });

        List<AdressMacForSpeakers> macAddressOfSpeakers = new ArrayList<AdressMacForSpeakers>();

        for (User speaker : speakersSorted) {
            macAddressOfSpeakers.add(new AdressMacForSpeakers(speaker.fullname, speaker.adresseMac, speaker.email, Joiner.on('\n').join(
                    Iterables.transform(speakers.get(speaker), new Function<Talk, String>() {
                        @Override
                        public String apply(Talk talk) {
                            return talk.title;
                        }
                    }))));
        }


        CsvEngine engine = new CsvEngine(AdressMacForSpeakers.class);

        StringWriter writer = new StringWriter();

        engine.writeFile(writer, macAddressOfSpeakers, AdressMacForSpeakers.class);

        response().setContentType("application/octet-stream");
        response().setHeader("Content-Description", "File Transfer");
        response().setHeader("Content-Disposition", "attachment;filename=macaddress.csv");
        response().setHeader("Content-Transfer-Encoding", "binary");
        response().setHeader("Expires", "0");
        response().setHeader("Cache-Control", "must-revalidate, post-check=0, pre-check=0");
        response().setHeader("Pragma", "public");
        return ok(writer.toString(), "ISO-8859-1");
    }

    public static Result acceptedSpeakersToJson(String callback) {

        ArrayNode result = getAcceptedTalksToJson();
        return ok(jsonp(callback, result));
    }

    private static ArrayNode getAcceptedTalksToJson() {
        List<Talk> talksAccepted = Talk.findByStatusForMinimalData(StatusTalk.ACCEPTE);

        Map<User, List<Talk>> speakers = new HashMap<User, List<Talk>>();

        for (Talk talk : talksAccepted) {
            if (talk.speaker != null) {
                if (!speakers.containsKey(talk.speaker)) {
                    speakers.put(talk.speaker, new ArrayList<Talk>());
                }
                speakers.get(talk.speaker).add(talk);
            }
            for (User speaker : talk.getCoSpeakers()) {
                if (!speakers.containsKey(speaker)) {
                    speakers.put(speaker, new ArrayList<Talk>());
                }
                speakers.get(speaker).add(talk);
            }
        }

        List<User> speakersSorted = new ArrayList<User>(speakers.keySet());
        Collections.sort(speakersSorted, new Comparator<User>() {
            @Override
            public int compare(User o1, User o2) {
                return o1.id.compareTo(o2.id);
            }
        });

        // Data used in html :
        // speaker.id
        // speaker.fullname
        // speaker.avatar
        // speaker.description
        // speaker.liens.url
        // speaker.liens.label
        // speaker.talks.id
        // speaker.talks.title
        // speaker.talks.description
        // speaker.talks.otherspeakers

        ArrayNode result = new ArrayNode(JsonNodeFactory.instance);
        for (User speaker : speakersSorted) {
            ObjectNode speakerJson = getSpeakerInJson(speaker);
            ArrayNode talksJson = new ArrayNode(JsonNodeFactory.instance);
            for (Talk talk : speakers.get(speaker)) {
                ObjectNode talkJson = Json.newObject();
                talkJson.put("id", talk.id);
                talkJson.put("title", talk.title);
                talkJson.put("description", talk.description);
                talkJson.put("indicationsOrganisateurs", talk.indicationsOrganisateurs);
                ArrayNode otherSpeakers = new ArrayNode(JsonNodeFactory.instance);
                if (talk.speaker != null && !speaker.equals(talk.speaker)) {
                    otherSpeakers.add(getSpeakerInJson(talk.speaker));
                }
                for (User otherSpeaker : talk.getCoSpeakers()) {
                    if (!otherSpeaker.equals(speaker)) {
                        otherSpeakers.add(getSpeakerInJson(otherSpeaker));
                    }
                }
                talkJson.put("otherspeakers", otherSpeakers);
                talksJson.add(talkJson);
            }
            speakerJson.put("talks", talksJson);
            result.add(speakerJson);
        }
        return result;
    }

    public static Result acceptedTalksToJson(String callback) {

        // Data used in html :
        // talk.id
        // talk.title
        // talk.description
        // talk.speaker.fullname
        // talk.speaker.avatar
        // talk.speaker.description
        // talk.speaker.liens.url
        // talk.speaker.liens.label
        // talk.coSpeakers

        List<Talk> talksAccepted = Talk.findByStatusForMinimalData(StatusTalk.ACCEPTE);
        ArrayNode result = new ArrayNode(JsonNodeFactory.instance);
        for (Talk talk : talksAccepted) {
            ObjectNode talkJson = Json.newObject();
            talkJson.put("id", talk.id);
            talkJson.put("title", talk.title);
            talkJson.put("description", talk.description);
            talkJson.put("indicationsOrganisateurs", talk.indicationsOrganisateurs);

            if (talk.speaker != null) {
                talkJson.put("speaker", getSpeakerInJson(talk.speaker));
            }
            ArrayNode coSpeakers = new ArrayNode(JsonNodeFactory.instance);
            for (User speaker : talk.getCoSpeakers()) {
                coSpeakers.add(getSpeakerInJson(speaker));
            }
            talkJson.put("coSpeakers", coSpeakers);
            result.add(talkJson);
        }
        return ok(jsonp(callback, result));
    }

    private static ObjectNode getSpeakerInJson(User speaker) {
        ObjectNode speakerJson = Json.newObject();
        speakerJson.put("id", speaker.id);
        speakerJson.put("fullname", speaker.fullname);
        speakerJson.put("avatar", speaker.getAvatar());
        speakerJson.put("description", speaker.description);

        ArrayNode liens = new ArrayNode(JsonNodeFactory.instance);
        for (Lien lien : speaker.getLiens()) {
            ObjectNode lienJson = Json.newObject();
            lienJson.put("url", lien.url);
            lienJson.put("label", lien.label);
            liens.add(lienJson);
        }
        speakerJson.put("liens", liens);
        return speakerJson;
    }
}
