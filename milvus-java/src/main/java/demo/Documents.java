package demo;

import software.amazon.awssdk.services.s3.endpoints.internal.Value;

public class Documents {
    private Integer doc_id;
    private Integer char_count;
    private String doc_text;

    public Integer getDoc_id() {
        return doc_id;
    }

    public void setDoc_id(Integer doc_id) {
        this.doc_id = doc_id;
    }

    public Integer getChar_count() {
        return char_count;
    }

    public void setChar_count(Integer char_count) {
        this.char_count = char_count;
    }

    public String getDoc_text() {
        return doc_text;
    }

    public void setDoc_text(String doc_text) {
        this.doc_text = doc_text;
    }

    @Override
    public String toString() {
        return "Documents{" +
                "doc_id=" + doc_id +
                ", char_count=" + char_count +
                ", doc_text='" + doc_text + '\'' +
                '}';
    }
}
