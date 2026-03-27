package analyzer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PerCommitCoverage {
    public String commit_hash;
    public Map<String, List<PerTestCoverage>> all_test_coverage = new HashMap<>();

    public PerCommitCoverage(){

    }
    public PerCommitCoverage(String s, Map<String,List<PerTestCoverage>> m){
        commit_hash = s;
        all_test_coverage = m;
    }
}
