package com.redhat.cajun.navy.finding.service;

import com.redhat.cajun.navy.finding.client.IncidentRestClient;
import com.redhat.cajun.navy.finding.client.MissionRestClient;
import com.redhat.cajun.navy.finding.model.Shelter;
import com.redhat.cajun.navy.finding.model.Victim;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;
import javax.enterprise.event.Observes;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;


@ApplicationScoped
public class FindService {

    private static final Logger logger = LoggerFactory.getLogger(FindService.class);

    @Inject
    @RestClient
    IncidentRestClient incidentRestClient;

    @Inject
    @RestClient
    MissionRestClient missionRestClient;

    @ConfigProperty(name="com.redhat.cajun.navy.finding.client.MissionRestClient/mp-rest/url")
    String missionServiceURL;

    
    @ConfigProperty(name="com.redhat.cajun.navy.finding.client.IncidentRestClient/mp-rest/url")
    String incidentServiceURL;


    void onStart(@Observes @Priority(value = 1) StartupEvent ev) {
        logger.info("start() \n\t missionServiceURL = "+missionServiceURL+"\n\t incidentServiceUrl = "+incidentServiceURL);
    }

    public Response getVictimByName(String name) {

        logger.info("***Requesting Victim Details for victim name - "+name+"***");

        List<Victim> victimList = null;
        try {
            victimList = incidentRestClient.getByName(name);
            JsonArray victimsArray = new JsonArray(victimList.stream().map(this::toJsonObject).collect(Collectors.toList()));
            JsonObject jsonObject = new JsonObject().put("victims", victimsArray);
            return Response.ok(jsonObject).build();
        } catch (Exception ex) {
            logger.error("Unable to connect to Incident Service. ", ex);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
    }

    private JsonObject toJsonObject(Victim victim) {
        return JsonCodec.toJsonObject(victim);
    }

    public Response getVictimShelter(String incidentId) {

        logger.info("***Requesting Shelter Details for Incident - "+incidentId+"***");

        boolean isMissionCreated = false;
        String missionId = null;

        try {
            List<String> missionList = missionRestClient.getMissions();

            //Mission Id = Incident Id + Responder Id
            for (String mission : missionList) {
                isMissionCreated = mission.contains(incidentId);
                if (isMissionCreated) {
                    missionId = mission;
                    break;
                }
            }

            if (!isMissionCreated) {
                logger.info("Mission not found for Incident Id - " + incidentId);
                JsonObject jsonObject = new JsonObject().put("status", false);
                jsonObject.put("Desc", "Mission not found");
                return Response.ok(jsonObject).build();
            }

            String missionDetails = missionRestClient.getMissionById(missionId);
            JSONObject jsonResponse = new JSONObject(missionDetails);
            String shelterLat = jsonResponse.getString("destinationLat");
            String shelterLong = jsonResponse.getString("destinationLong");

            Shelter shelter = getShelterName(shelterLat, shelterLong);
            logger.debug("Shelter Details for Mission Id - " + missionId + " name - " + shelter.getName() + " lat - " + shelter.getLat() + " Long - " + shelter.getLon());

            JsonObject jsonObject = new JsonObject().put("status", true);
            jsonObject.put("shelter", toShelterJsonObject(shelter));
            return Response.ok(jsonObject).build();

        } catch (Exception ex) {
            logger.error("Unable to connect to Mission Service. ", ex);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
    }

    private Shelter getShelterName(String lat, String lon) {
        List<Shelter> shelterList = Shelter.getShelterList();
        for (Shelter shelter : shelterList) {
            if (lat.equals(shelter.getLat()) && lon.equals(shelter.getLon())) {
                return shelter;
            }
        }
        return new Shelter("Shelter Name not available", lat, lon);
    }

    private JsonObject toShelterJsonObject(Shelter shelter) {
        return JsonCodec.toShelterJsonObject(shelter);
    }

    void onStop(@Observes ShutdownEvent ev) {
        logger.info("onStop() stopping...");
    }

}
