package com.example.noteapp.NoteService.ViewModel;

public class NoteItemViewModel {
    private String title;
    private String contentPreview;
    private String time;
    private String tag;

    public NoteItemViewModel(String title, String contentPreview, String time, String tag) {
        this.title = title;
        this.contentPreview = contentPreview;
        this.time = time;
        this.tag = tag;
    }

    public String getTitle() {
        return title;
    }

    public String getContentPreview() {
        return contentPreview;
    }

    public String getTime() {
        return time;
    }

    public String getTag() {
        return tag;
    }
}