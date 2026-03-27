package org.example;

import java.util.List;

public class DuplicateMethodInfo {
    public String originalMethod;
    public List<MethodModificationInformation.ArgumentAmplificationType> argumentAmplificationType;
    public DuplicateMethodInfo() { }
    public DuplicateMethodInfo(String o, List<MethodModificationInformation.ArgumentAmplificationType> listOfTypes){
        this.originalMethod = o;
        this.argumentAmplificationType = listOfTypes;
    }
}
