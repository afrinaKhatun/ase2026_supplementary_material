package tree.analyzer;

import com.github.javaparser.printer.concretesyntaxmodel.CsmList;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LLMSimilarityChecker {
    private static final String OLLAMA_URL = "http://localhost:11434/api/chat";
    private static final String MODEL_NAME = "qwen2.5-coder:7b";


    public static void main(String[] args) throws Exception {
        String baseName = "testGroupOrAttribute";
        String baseCode = """
                @Test
                 public void testGroupOrAttribute() {
                     String h = ""<div id=1 /><div id=2 /><div title=foo /><div title=bar />"";
                     Elements els = Jsoup.parse(h).select(""[id],[title=foo]"");
                     assertEquals(3, els.size());
                     assertEquals(""1"", els.get(0).id());
                     assertEquals(""2"", els.get(1).id());
                     assertEquals(""foo"", els.get(2).attr(""title""));
                 }
            """;

        Map<String, String> candidates = Map.of(
                "testByTag", """
                        @Test
                         public void testByTag() {
                             Elements els = Jsoup.parse(""<div id=1><div id=2><p>Hello</p></div></div><div id=3>"").select(""div"");
                             assertEquals(3, els.size());
                             assertEquals(""1"", els.get(0).id());
                             assertEquals(""2"", els.get(1).id());
                             assertEquals(""3"", els.get(2).id());
                             Elements none = Jsoup.parse(""<div id=1><div id=2><p>Hello</p></div></div><div id=3>"").select(""span"");
                             assertEquals(0, none.size());
                         }
                    """,
                "testById", """
                        @Test
                        public void testById() {
                            Elements els = Jsoup.parse(""<div><p id=foo>Hello</p><p id=foo>Foo two!</p></div>"").select(""#foo"");
                            assertEquals(1, els.size());
                            assertEquals(""Hello"", els.get(0).text());
                            Elements none = Jsoup.parse(""<div id=1></div>"").select(""#foo"");
                            assertEquals(0, none.size());
                        }
                    """,
                "testByAttribute", """
                        @Test
                             public void testByAttribute() {
                                 String h = ""<div title=foo /><div title=bar /><div />"";
                                 Document doc = Jsoup.parse(h);
                                 Elements withTitle = doc.select(""[title]"");
                                 Elements foo = doc.select(""[title=foo]"");
                                 assertEquals(2, withTitle.size());
                                 assertEquals(1, foo.size());
                             }
                    """,
                "testByClass", """
                        @Test
                            public void testByClass() {
                                Elements els = Jsoup.parse(""<p id=0 class='one two'><p id=1 class='one'><p id=2 class='two'>"").select("".one"");
                                assertEquals(2, els.size());
                                assertEquals(""0"", els.get(0).id());
                                assertEquals(""1"", els.get(1).id());
                                Elements none = Jsoup.parse(""<div class='one'></div>"").select("".foo"");
                                assertEquals(0, none.size());
                                Elements els2 = Jsoup.parse(""<div class='one-two'></div>"").select("".one-two"");
                                assertEquals(1, els2.size());
                            }
                    """
        );

        String prompt = buildPrompt(baseName, baseCode, candidates);

        List<String> similarNames = querySimilarMethods(prompt).similarMethods;

        System.out.println("Similar methods to " + baseName + ": " + similarNames);
    }

    public static LlmRecommendation checkLLMSimilarity(String baseName,
                                     String baseCode,
                                     Map<String, String> candidates) throws Exception {
        String prompt = buildPrompt(baseName, baseCode, candidates);
        try {
            LlmRecommendation recommendation = querySimilarMethods(prompt);
            return recommendation;
        }
        catch (Exception e){
            return new LlmRecommendation(new ArrayList<String>(),"LLM error");
        }

    }

    public static String buildPrompt(String baseName,
                                      String baseCode,
                                      Map<String, String> candidates) {

        StringBuilder candidatesBlock = new StringBuilder();
        for (Map.Entry<String, String> entry : candidates.entrySet()) {
            candidatesBlock
                    .append("Name: ").append(entry.getKey()).append("\n")
                    .append("Code:\n")
                    .append(entry.getValue()).append("\n\n");
        }

        return """
    You are a Java test analysis expert.

    You are given:
    1. A BASE JUnit test method.
    2. A list of CANDIDATE JUnit test methods, each with a unique method name.

    Two methods are considered SIMILAR if either of the following holds:
    - They test essentially the same behavior (same intent, same API usage pattern, similar assertions), or
    - The BASE method reuses the test setup of a candidate method and may/may not do some modifications in the test setup 
    - The BASE method has similar or modifies the observed methods or attributes in the assertions
    - Priority should be given if both test setup and assertions have similarities

    Small differences in variable names, formatting, ordering of statements, or other minor details should NOT affect similarity.

    TASK:
    From the candidate methods, select all methods that are similar in behavior to the BASE method according to the definition above.

    OUTPUT FORMAT (VERY IMPORTANT):
    - Return ONLY a single object with exactly two attributes:
      - "methods": an array of similar method names (strings), for example: ["method1", "method2"]
      - "message": a short string explaining why these methods were selected (or why none were selected)
    - Do NOT include the BASE method's name in the "methods" array.
    - If none of the candidate methods are similar, return an empty array for "methods" and still provide an explanation in "message".
    - Do NOT include any extra keys, explanations, or text outside of the output object.
    - the output format is - 
    { 
      methods:[],
      "message":""
    }

    BASE METHOD:
    Name: %s
    Code:
    %s

    CANDIDATE METHODS:
    %s
    """.formatted(baseName, baseCode, candidatesBlock.toString());
    }

    public static LlmRecommendation querySimilarMethods(String prompt) throws Exception {
        // Build JSON body for Ollama /api/chat
        JSONObject message = new JSONObject()
                .put("role", "user")
                .put("content", prompt);

        JSONArray messages = new JSONArray().put(message);

        JSONObject body = new JSONObject()
                .put("model", MODEL_NAME)
                .put("messages", messages)
                .put("stream", false);

        String requestBody = body.toString();

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        // Parse Ollama response
        JSONObject root = new JSONObject(response.body());
        JSONObject msg = root.getJSONObject("message");
        String content = msg.optString("content", "").trim();

        // Remove markdown code fences if present
        String cleaned = content.replaceAll("(?s)```(json)?", "").replaceAll("```", "").trim();

        // Find first '{' and last '}'
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');

        // Extract the substring containing the JSON object only
        String jsonPart = cleaned.substring(start, end + 1);

        // Parse it as a JSON object
        JSONObject obj = new JSONObject(jsonPart);

         // 1) Parse the "methods" array
        List<String> methods = new ArrayList<>();
        if (obj.has("methods")) {
            JSONArray arr = obj.getJSONArray("methods");
            for (int i = 0; i < arr.length(); i++) {
                methods.add(arr.getString(i));
            }
        }

        // 2) Parse the "message" string
        String explanation = obj.optString("message", "");
        return new LlmRecommendation(methods,explanation);
    }
}
