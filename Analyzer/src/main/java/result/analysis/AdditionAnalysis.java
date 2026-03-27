package result.analysis;

import java.util.HashMap;
import java.util.Map;

public class AdditionAnalysis{
    public String methodName;
    public int numberOfSimilarMethods;
    public int numberOfExactMatch;
    public Map<String,Boolean> numberOfAssertionAmplification = new HashMap<>();
    public boolean multipleMethodsAdded;
    public Map<String, Double> similar_AddedPair = new HashMap<>();
    public Map<String, Integer> methodRemoved = new HashMap<>();
    public Map<String, Integer> methodNewAdded = new HashMap<>();
            ;
    public Map<String,Integer> methodDuplicated = new HashMap<>();
}
