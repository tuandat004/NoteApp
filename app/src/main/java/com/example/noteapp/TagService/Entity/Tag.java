package com.example.noteapp.TagService.Entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "tags",
        indices = {@Index(value = {"tag_name", "user_id"}, unique = true)}
)
public class Tag {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "tag_id")
    public int tagId;

    @ColumnInfo(name = "tag_name")
    public String tagName;

    @ColumnInfo(name = "user_id", defaultValue = "0")
    public int userId;
}