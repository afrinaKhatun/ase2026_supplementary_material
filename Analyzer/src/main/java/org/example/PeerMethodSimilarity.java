package org.example;

public class PeerMethodSimilarity {
    public double jaccardSimilarity;
    public double editDistance;
    public String category;
    public String severity;
    public double fullBodyEditDistance;
    public int positionDistance;
    public PeerMethodSimilarity() {}
    public PeerMethodSimilarity(double j, double e, String c, String s, double b, int p){
        jaccardSimilarity = j;
        editDistance = e;
        fullBodyEditDistance = b;
        category = c;
        severity = s;
        positionDistance = p;
    }
}
