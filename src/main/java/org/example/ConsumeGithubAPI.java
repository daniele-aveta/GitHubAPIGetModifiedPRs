package org.example;

import com.google.gson.*;
import okhttp3.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Properties;

public class ConsumeGithubAPI {

    static final DateTimeFormatter GITHUB_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssz");
    static final String CONFIG = "auth.properties";


    public static void main(String[] args) throws IOException {
        //JsonArray pullRequests = getPullRequests("-Owner-", "-reponame-", "2022-06-01", "2022-07-10");
        JsonArray pullRequests = getPullRequests("daniele-aveta", "GitHubAPIGetModifiedPRs", "2023-09-15", "2023-09-17");
        String formattedOutput = formatOutput(pullRequests);
        System.out.println(formattedOutput);
    }

    public static JsonArray getPullRequests(String owner, String repositoryName, String startDate, String endDate) throws IOException {
        Properties prop = new Properties();

        try {
            //Reading the authorization token from a config file
            InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(CONFIG);
            if (Objects.isNull(stream)) {
                System.err.printf("Configuration file %s couldn't be found among the program's resources...\n", CONFIG);
                return null;
            }
            prop.load(stream);
        } catch (SecurityException ex) {
            System.err.printf("Caller does not have the RuntimePermission: %s\n", ex.getLocalizedMessage());
            return null;
        } catch (IOException ex) {
            System.err.printf("Couldn't read properties: %s\n", ex.getLocalizedMessage());
            return null;
        }

        //Parameterizing the URL building (using actual variables would be better as it achieves actual parameterization, this snippet only shows how to  build URLs with HttpUrl)
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(new HttpUrl.Builder()
                        .scheme("https")
                        .host("api.github.com")
                        .addPathSegment("repos")
                        .addPathSegment(owner)
                        .addPathSegment(repositoryName)
                        .addPathSegment("pulls")
                        .addQueryParameter("state", "all")
                        .addQueryParameter("per_page", "100")
                        .addQueryParameter("page", "1")
                        .build())
                .addHeader("Authorization", "Bearer " + prop.get("auth"))
                .build();

        //Parsing the response
        JsonArray modifiedPRs = new JsonArray();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            JsonArray pullRequests = JsonParser.parseString(responseBody).getAsJsonArray();

            //Employing the LocalDateTime classes to better handle date confrontations
            LocalDateTime tempDateTime;
            LocalDateTime startDateTime = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
            LocalDateTime endDateTime = LocalDate.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE).plusDays(1).atStartOfDay();

            for (JsonElement pR : pullRequests) {
                JsonObject prObject = pR.getAsJsonObject();

                //Reading the creation date of the current pull request
                tempDateTime = LocalDateTime.parse(prObject.get("created_at").getAsString(), GITHUB_DATE_TIME_FORMAT);

                //Here we assume that the range boundaries are included.
                //The end date has been converted to the next given date to better cover (and include) every hour of the original end date.
                //Skipping the pull request if its creation date/time is lower than the start date or greater/equal than the date following end.
                if (tempDateTime.isBefore(startDateTime) || tempDateTime.isEqual(endDateTime) || tempDateTime.isAfter(endDateTime)) {
                    continue;
                }

                JsonObject prData = new JsonObject();
                prData.addProperty("id", prObject.get("id").getAsBigInteger());
                prData.addProperty("user", prObject.get("user").getAsJsonObject().get("login").getAsString());
                prData.addProperty("title", prObject.get("title").getAsString());
                prData.addProperty("state", prObject.get("state").getAsString());
                prData.addProperty("created_at", prObject.get("created_at").getAsString());
                modifiedPRs.add(prData);
            }
        }

        return modifiedPRs;
    }

    private static String formatOutput(JsonArray jsonArray) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(jsonArray);
    }
}
