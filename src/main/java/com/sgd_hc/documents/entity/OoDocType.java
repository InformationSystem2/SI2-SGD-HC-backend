package com.sgd_hc.documents.entity;

public enum OoDocType {
    WORD ("word",  "docx"),
    CELL ("cell",  "xlsx"),
    SLIDE("slide", "pptx"),
    PDF("pdf", "pdf");

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
            case PDF   -> "application/vnd.openxmlformats-officedocument.pdfprocessingml.pdf";
        };
    }

    public static OoDocType fromExtension(String filename) {
        if (filename == null) return WORD;
        String f = filename.toLowerCase();
        if (f.endsWith(".xlsx")) return CELL;
        if (f.endsWith(".pptx")) return SLIDE;
        if (f.endsWith(".pdf")) return PDF;
        return WORD;
    }
}
