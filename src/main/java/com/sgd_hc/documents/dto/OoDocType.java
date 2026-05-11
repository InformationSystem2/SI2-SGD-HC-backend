package com.sgd_hc.documents.dto;

public enum OoDocType {
    WORD ("word",  "docx"),
    CELL ("cell",  "xlsx"),
    SLIDE("slide", "pptx");

    public final String ooType;
    public final String fileExt;

    OoDocType(String ooType, String fileExt) {
        this.ooType  = ooType;
        this.fileExt = fileExt;
    }

    public String mimeType() {
        return switch (this) {
            case WORD  -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case CELL  -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case SLIDE -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        };
    }

    public static OoDocType fromExtension(String filename) {
        if (filename == null) return WORD;
        String f = filename.toLowerCase();
        if (f.endsWith(".xlsx")) return CELL;
        if (f.endsWith(".pptx")) return SLIDE;
        return WORD;
    }
}
