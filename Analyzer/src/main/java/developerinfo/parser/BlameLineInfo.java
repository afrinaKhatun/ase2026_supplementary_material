package developerinfo.parser;

public class BlameLineInfo {
    String fullCommitHash;
    String authorName;
    String authorEmail;
    String authorTime;
    String authorDateFormatted;
    String authorTimeZone;
    int finalLineNumber;
    String sourceLine;

    @Override
    public String toString() {
        return "BlameLineInfo[ " +
                "fullCommitHash='" + fullCommitHash + '\'' +
                ", authorName='" + authorName + '\'' +
                ", authorEmail='" + authorEmail + '\'' +
                ", authorDateFormatted='" + authorDateFormatted + '\'' +
                ", finalLineNumber=" + finalLineNumber +
                ", sourceLine='" + sourceLine + '\'' +
                " ]";
    }
}