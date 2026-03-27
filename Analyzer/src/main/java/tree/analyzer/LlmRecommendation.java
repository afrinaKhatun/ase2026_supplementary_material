package tree.analyzer;

import java.util.List;

public class LlmRecommendation {
    public List<String> similarMethods;
    public String observation;
    public LlmRecommendation(List<String> sm, String ob){
        similarMethods = sm;
        observation = ob;
    }
}
