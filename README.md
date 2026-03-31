
# ASE 2026 Artifact: Test Amplification Analysis

This repository contains the artifact accompanying our ASE 2026 paper submission. The artifact includes the implementation, datasets, annotations, and survey materials used in our study on test amplification in open-source repositories.


## 📁 Folder Structure
- **Analyzer/** – Java implementation for repository mining and analysis  
- **Coding/** – Annotated datasets from human coders  
- **Data/** – 1728 potential amplification pairs and generated results  
- **Survey Forms/** – Survey instruments and collected responses  
## 📁 Folder Details
### 🔹 Analyzer/

This folder contains the core implementation of our analysis pipeline. It supports:

- Continuous traversal of repository commit history  
- Extraction of test case additions
- Computation of structural and non-functional properties  


#### ⚙️ Environment Requirements

- All our implementation are run on Apple MacBook Pro (Apple M-series chip, 24 GB RAM or higher).
- Java: **8, 11, 17, or later** . Old java versions required to compile historical commits.
- Maven 

---

#### ▶️ Running the Analyzer

Run the following command:
mvn -DskipTests exec:java
-Dexec.mainClass=org.example.RunnerVer3
-Dexec.args="\<repo-name> \<branch-name>"

Alternatively, run the `RunnerVer3` class directly from an IDE.

---

#### 📂 Output Artifacts
The following table indicates the specific java files running which generates the corresponding output files in specific output folders - 

| Component              | Output Folder     | File Name Pattern        | Description |
|-----------------------|------------------|-------------------------|------------|
| RunnerVer3.java            | `ParsedJsons/`   | `<repo-name>-commit-test-change-history.ndjson`    | Parsed commit-level information |
| RunnerVer3.java | `LogData/`  | `<repo-name>logData.txt`      | Logging Execution Details |
| SetSimilarityAnalysis.java            | `ExcelResults/`   | `<repo-name>_set_Result.xlsx`    | Identifies the Potential Amplification Pairs |
| DeveloperInfoExtracter.java            | `DeveloperInfo/`   | `<repo-name>_developer_info_output.xlsx`    |  Parsed Method Level Code Authorship, Locality and Temporal Distance|



### 🔹 coding/

This folder contains annotated datasets produced by human annotators. Out of the 1728 amplification pairs, both human annotators firstly annotated 101 pairs to refine the codes and then additonally annotated 318 pairs making it to 419 pairs.

- Includes the individual annotation by both annotators for all 419 pairs

---

### 🔹 data/

This folder contains the output results genertaed after running the Analyzer. This folder contains four sub folders - 

- **Amplification Pairs/** - This folder includes - 
    - The 1728 amplification pairs across studied subjects developed by our study
    - The final coding for the 419 amplification pairs after resolving the disagreements. The proposed four amplification patterns were derived using these pairs.
- **Developer Repository Info/** - The generated developer's authorship, locality and temporal distance data are here.  
- **Method and Body Distribution Data Repository Info/** - The method call and method body edit similarity between every added method and its closest reference method is enlisted here.
- **TestNameSimilarityResults/** - The testcase name edit distance similarity between every added method and its closest reference method is enlisted here.

---

### 🔹 Survey Forms/

This folder includes:

- Survey questionnaires used to define similarity thresholds  
- Collected participant responses   

---

