package result.analysis;

import org.example.JavapClassificationResult;
import tree.analyzer.LlmRecommendation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SortStructure {
    public String distinctMethodName;
    public String distinctMethodBody;
    public String distinctMethodNormalizedBody;
    public String distinctFullMethodNormalizedBody;
    public String addedMethodName;
    public String addedMethodBody;
    public String addedMethodNormalizedBody;
    public String addedFullMethodNormalizedBody;
    public int removedMethods;
    public int duplicateMethods;
    public int addedMethods;
    public int sameMethods;
    public int totalDupArgumentAmp;
    public int actualDupOperatorAmp;
    public int totalSameArgumentAmp;
    public int actualSameOperatorAmp;
    public int totalAssertionMethodAmplification;
    public int actualAssertionMethodAmplification;
    public int actualAssertionAttributeAmplification;
    public int totalModifications;
    public double similarityScore;
    public double normalizedScore;
    public Set<String> extraObjects;
    public List<String> llmSimilarMethods;
    //public String category;
    public JavapClassificationResult category;
    public double methodNameSimilarity;
    public double editDistance;
    public double bodyEditDistance;
    public int positionDistance;
    public String sourceAddedPositions;
    public int noOfExistingMethodsInFile;
    public Set<String> newMethodsTested = new HashSet<>();
    public String selector;
    public String severity;
    public Set<String> uncommonMethodsWithPeer = new HashSet<>();
    public String existingOrModified;

}
