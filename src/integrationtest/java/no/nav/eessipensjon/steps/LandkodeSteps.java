package no.nav.eessipensjon.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import cucumber.api.java.no.Gitt;
import cucumber.api.java.no.Når;
import cucumber.api.java.no.Så;
import no.nav.eessipensjon.IntegrationTestBaseClass;
import org.apache.http.HttpHeaders;
import org.apache.http.client.fluent.Request;
import org.junit.Assert;

import java.util.List;

public class LandkodeSteps extends IntegrationTestBaseClass {

    private String oidcToken;
    private List landkoder;

    @Gitt("^en saksbehandler \"([^\"]*)\"$")
    public void enSaksbehandler(String saksbehandler) throws Throwable {
        oidcToken = getSaksbehandlerToken(saksbehandler);
    }

    @Når("^det bes om landkodelisten$")
    public void detBesOmLandkodeliste() throws Throwable {
        String response = Request
                .Post("http://localhost:" + basePort + "/api/landkoder")
                .setHeader(HttpHeaders.AUTHORIZATION, "Bearer eyAidHlwIjogIkpXVCIsICJraWQiOiAiaG9ReWxXNDZpYWoyY1VjN3d4TFF2b3pLVTMwPSIsICJhbGciOiAiUlMyNTYiIH0.eyAiYXRfaGFzaCI6ICJXR0o4NlowX0hZSXlyQXo5RVdmdURRIiwgInN1YiI6ICJaOTkyMzY2IiwgImF1ZGl0VHJhY2tpbmdJZCI6ICJhNjI3MDA3Ny1hZGU2LTQ5OWUtYmE1NC0wMzI4M2Q1MjUxMDQtNjEwNDkxIiwgImlzcyI6ICJodHRwczovL2lzc28tdC5hZGVvLm5vOjQ0My9pc3NvL29hdXRoMiIsICJ0b2tlbk5hbWUiOiAiaWRfdG9rZW4iLCAibm9uY2UiOiAidlV6UnJzVzliejFYQTFUWUxlWjFUT2dQYklSaGlTRTBnYXRCS0dnSWYybyIsICJhdWQiOiAiZWVzc2ktcGVuc2pvbi1mcm9udGVuZC1hcGktZnNzLXQ4IiwgImNfaGFzaCI6ICJZdkVJMnZpTWdXZnI3eVRsdGR4Z3RnIiwgIm9yZy5mb3JnZXJvY2sub3BlbmlkY29ubmVjdC5vcHMiOiAiMDVhMmRhM2EtYTI5Yy00ODFlLTliNzItNzljYzAwMWRlYWU2IiwgImF6cCI6ICJlZXNzaS1wZW5zam9uLWZyb250ZW5kLWFwaS1mc3MtdDgiLCAiYXV0aF90aW1lIjogMTU1NzQ4MjI1MywgInJlYWxtIjogIi8iLCAiZXhwIjogMTU1NzQ4NTg1MywgInRva2VuVHlwZSI6ICJKV1RUb2tlbiIsICJpYXQiOiAxNTU3NDgyMjUzIH0.RXcKNhpRiXOiqpBjB09cGernEviZA-Z1M0W_kpAyiB_iVpDD-pesufPrF7-vVr_ANcuVGTn250FaavkQqxU1qMkzQheVxN9xdyEKV4_0KtkrNdwoxNV5cYaLOPWUNdKRV6Q-jUURXLycEnyj37Kq66mDKO23m0-ANQlqqYameJNPKz5vdpVIWz5t549LAMsz7evvIpjtTHF1sNkCkTEPNkDUtBmhTVJugBxTRquPjAIvmAqjYChCvgzLtb6RHdW_J4xm85IZWgqpAh_76iJEpAXP_7P359jWaqvd8_uF5v2AbLLrjN4I9nTlfkqWO-kQz8vSm9jfy-CSUFmdscXOnw")
                .execute()
                .returnContent()
                .asString();

        landkoder = new ObjectMapper().readValue(response, List.class);
    }

    @Så("^får vi en liste med (\\d+) landkoder$")
    public void fårViEnListeMedLandkoder(int antall) throws Throwable {
        Assert.assertEquals("Forventer " + antall + " landkoder", landkoder.size(), antall);
    }
}
